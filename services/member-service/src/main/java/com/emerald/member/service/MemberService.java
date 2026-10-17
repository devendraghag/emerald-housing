package com.emerald.member.service;

import com.emerald.member.dto.CreateMemberRequest;
import com.emerald.member.dto.MemberResponse;
import com.emerald.member.dto.UpdateMemberRequest;
import com.emerald.member.dto.PageResponse;
import com.emerald.member.config.PageRequests;
import com.emerald.member.entity.MemberEntity;
import com.emerald.member.entity.IdempotencyRecordEntity;
import com.emerald.member.dto.MemberCreationResult;
import com.emerald.member.exception.IdempotencyConflictException;
import com.emerald.member.exception.InvalidIdempotencyKeyException;
import com.emerald.member.exception.DuplicateMobileNumberException;
import com.emerald.member.exception.MemberNotFoundException;
import com.emerald.member.exception.InactiveMemberException;
import com.emerald.member.repository.MemberRepository;
import com.emerald.member.repository.IdempotencyRecordRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.JacksonException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class MemberService {
    private final MemberRepository members;
    private final IdempotencyRecordRepository idempotencyRecords;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate idempotencyTransaction;
    private final TransactionTemplate idempotencyLookupTransaction;
    private final Duration idempotencyTtl;
    private final Counter idempotencyReplayCounter;
    private final Counter idempotencyConflictCounter;
    private static final Pattern SAFE_IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final String CREATE_MEMBER_OPERATION = "CREATE_MEMBER";

    @Autowired
    public MemberService(MemberRepository members, IdempotencyRecordRepository idempotencyRecords,
            JsonMapper jsonMapper, PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry,
            @Value("${app.idempotency.ttl:24h}") Duration idempotencyTtl) {
        this.members = members;
        this.idempotencyRecords = idempotencyRecords;
        this.jsonMapper = jsonMapper;
        this.idempotencyTtl = idempotencyTtl;
        this.idempotencyReplayCounter = meterRegistry.counter("emerald.idempotency.replays");
        this.idempotencyConflictCounter = meterRegistry.counter("emerald.idempotency.conflicts");
        this.idempotencyTransaction = new TransactionTemplate(transactionManager);
        this.idempotencyTransaction.setTimeout(15);
        // This conflict lookup must see a just-committed idempotency record, so route it to the primary.
        this.idempotencyLookupTransaction = new TransactionTemplate(transactionManager);
        this.idempotencyLookupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Retains the original service construction used by existing focused tests. */
    public MemberService(MemberRepository members) {
        this.members = members;
        this.idempotencyRecords = null;
        this.jsonMapper = null;
        this.idempotencyTransaction = null;
        this.idempotencyLookupTransaction = null;
        this.idempotencyTtl = Duration.ofHours(24);
        this.idempotencyReplayCounter = null;
        this.idempotencyConflictCounter = null;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse create(CreateMemberRequest request) {
        return createNewMember(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public MemberCreationResult createIdempotent(CreateMemberRequest request, String idempotencyKey, UUID actorId) {
        if (idempotencyKey == null || !SAFE_IDEMPOTENCY_KEY.matcher(idempotencyKey).matches())
            throw new InvalidIdempotencyKeyException();
        if (actorId == null) throw new IllegalStateException("Authenticated actor identity is unavailable");
        String fingerprint = fingerprint(request);
        Instant now = Instant.now();
        try {
            MemberCreationResult result = idempotencyTransaction.execute(transaction -> {
                idempotencyRecords.deleteExpired(now);
                IdempotencyRecordEntity record = new IdempotencyRecordEntity(UUID.randomUUID(), actorId,
                        CREATE_MEMBER_OPERATION, idempotencyKey, fingerprint, now, now.plus(idempotencyTtl));
                idempotencyRecords.saveAndFlush(record);
                MemberResponse created = createNewMember(request);
                String savedResponse;
                try {
                    savedResponse = jsonMapper.writeValueAsString(created);
                } catch (JacksonException serializationFailure) {
                    throw new IllegalStateException("Member creation response could not be saved", serializationFailure);
                }
                record.complete(201, savedResponse);
                idempotencyRecords.saveAndFlush(record);
                return new MemberCreationResult(created, false);
            });
            if (result == null) throw new IllegalStateException("Member creation transaction returned no result");
            return result;
        } catch (DataIntegrityViolationException duplicateKeyOrConstraint) {
            Optional<IdempotencyRecordEntity> existing = idempotencyLookupTransaction.execute(transaction ->
                    idempotencyRecords.findByActorIdAndOperationAndIdempotencyKey(
                            actorId, CREATE_MEMBER_OPERATION, idempotencyKey));
            if (existing == null || existing.isEmpty()) throw duplicateKeyOrConstraint;
            IdempotencyRecordEntity record = existing.get();
            if (!record.getRequestFingerprint().equals(fingerprint) || !"COMPLETED".equals(record.getStatus())) {
                idempotencyConflictCounter.increment();
                throw new IdempotencyConflictException();
            }
            try {
                idempotencyReplayCounter.increment();
                return new MemberCreationResult(jsonMapper.readValue(record.getResponseBody(), MemberResponse.class), true);
            } catch (Exception invalidStoredResponse) {
                throw new IllegalStateException("The saved idempotency response cannot be read", invalidStoredResponse);
            }
        }
    }

    private MemberResponse createNewMember(CreateMemberRequest request) {
        String mobile = normalizeMobile(request.mobileNumber());
        if (members.existsByMobileNumberAndStatus(mobile, MemberStatus.ACTIVE)) throw new DuplicateMobileNumberException();
        Instant now = Instant.now();
        try {
            return toResponse(members.saveAndFlush(new MemberEntity(UUID.randomUUID(), normalizeName(request.fullName()), mobile,
                    normalizeEmail(request.email()), MemberStatus.ACTIVE, now, now)));
        } catch (DataIntegrityViolationException ex) { throw new DuplicateMobileNumberException(); }
    }

    private static String fingerprint(CreateMemberRequest request) {
        String canonical = normalizeName(request.fullName()) + "\n" + normalizeMobile(request.mobileNumber())
                + "\n" + String.valueOf(normalizeEmail(request.email()));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse get(UUID id) { return toResponse(find(id)); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<MemberResponse> getAll() { return members.findAllByOrderByCreatedAtDesc().stream().map(MemberService::toResponse).toList(); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<MemberResponse> getPage(int page, int size, String sortBy, String direction) {
        Pageable pageable = PageRequests.of(page, size, sortBy, direction,
                Set.of("createdAt", "updatedAt", "fullName", "mobileNumber", "email", "status"));
        Page<MemberEntity> result = members.findAll(pageable);
        return PageResponse.from(result, MemberService::toResponse, sortBy, direction);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse update(UUID id, UpdateMemberRequest request) {
        MemberEntity member = find(id);
        String mobile = normalizeMobile(request.mobileNumber());
        if (member.getStatus() == MemberStatus.ACTIVE && !member.getMobileNumber().equals(mobile)
                && members.existsByMobileNumberAndStatus(mobile, MemberStatus.ACTIVE)) throw new DuplicateMobileNumberException();
        member.update(normalizeName(request.fullName()), mobile, normalizeEmail(request.email()), Instant.now());
        try { return toResponse(members.saveAndFlush(member)); }
        catch (DataIntegrityViolationException ex) { throw new DuplicateMobileNumberException(); }
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public MemberResponse deactivate(UUID id) {
        MemberEntity member = find(id);
        member.deactivate(Instant.now());
        return toResponse(members.save(member));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public void requireActive(UUID id) {
        MemberEntity member = find(id);
        if (member.getStatus() != MemberStatus.ACTIVE) throw new InactiveMemberException();
    }
    private MemberEntity find(UUID id) { return members.findById(id).orElseThrow(() -> new MemberNotFoundException(id)); }
    private static String normalizeName(String s) { return s.trim().replaceAll("\\s+", " "); }
    private static String normalizeMobile(String s) { return s.trim(); }
    private static String normalizeEmail(String s) { return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT); }
    private static MemberResponse toResponse(MemberEntity m) { return new MemberResponse(m.getId(), m.getFullName(), m.getMobileNumber(), m.getEmail(), m.getStatus(), m.getCreatedAt(), m.getUpdatedAt()); }
}
