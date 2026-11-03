
package com.emerald.member.lostfound;

import static com.emerald.member.lostfound.LostFoundDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LostFoundService {
    private final JdbcTemplate db;
    private final LostFoundEventPublisher events;
    private final LostFoundPhotoStorage photos;

    public LostFoundService(JdbcTemplate db, LostFoundEventPublisher events, LostFoundPhotoStorage photos) {
        this.db = db;
        this.events = events;
        this.photos = photos;
    }

    @Transactional
    public Notice create(CreateReport r, UUID actor, Set<UUID> flatIds, boolean administrator) {
        UUID flatId = r.flatId();
        if (flatId == null) {
            if (administrator || flatIds == null || flatIds.size() != 1)
                throw bad("flatId is required when the account has zero or multiple assigned flats");
            flatId = flatIds.iterator().next();
        }
        requireFlatAccess(flatId, flatIds, administrator);
        String type = r.reportType().toUpperCase();
        if (!List.of("LOST", "FOUND").contains(type)) throw bad("reportType must be LOST or FOUND");
        if (r.itemType().equalsIgnoreCase("OTHER") && (r.itemOther() == null || r.itemOther().isBlank()))
            throw bad("itemOther is required when itemType is Other");
        if (r.contactPreference().equalsIgnoreCase("OTHER") && (r.contactOther() == null || r.contactOther().isBlank()))
            throw bad("contactOther is required when contactPreference is Other");
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO lost_found_reports(id,reporter_id,flat_id,report_type,item_type,item_other,location,occurred_at,description,contact_preference,contact_other,status) VALUES (?,?,?,?,?,?,?,?,?,?,?, 'SUBMITTED')",
                id, actor, flatId, type, r.itemType(), r.itemOther(), r.location(), java.sql.Timestamp.from(r.occurredAt()), r.description(), r.contactPreference(), r.contactOther());
        return get(id);
    }

    @Transactional(readOnly = true)
    public List<Notice> board(Set<UUID> flatIds, boolean administrator) {
        if (administrator)
            return db.query("SELECT * FROM lost_found_reports WHERE status IN ('PUBLISHED','CLAIM_RECEIVED') ORDER BY created_at DESC", this::notice);
        if (flatIds == null || flatIds.isEmpty()) return List.of();
        String marks = placeholders(flatIds.size());
        return db.query("SELECT * FROM lost_found_reports WHERE status IN ('PUBLISHED','CLAIM_RECEIVED') AND flat_id IN (" + marks + ") ORDER BY created_at DESC", this::notice, flatIds.toArray());
    }

    @Transactional(readOnly = true)
    public List<AdminReport> adminReports() {
        return db.query("SELECT * FROM lost_found_reports ORDER BY created_at DESC", (rs, n) -> new AdminReport(notice(rs, n), rs.getObject("reporter_id", UUID.class), rs.getString("contact_preference"), rs.getString("contact_other")));
    }

    @Transactional
    public Notice assignFlat(UUID id, UUID flatId, UUID actor) {
        Notice current = get(id);
        db.update("UPDATE lost_found_reports SET flat_id=?,updated_at=now() WHERE id=?", flatId, id);
        if (List.of("PUBLISHED", "CLAIM_RECEIVED").contains(current.status()))
            events.publish(LostFoundEvent.create(id, "LostFoundReportPublished", actor));
        return get(id);
    }

    @Transactional(readOnly = true)
    public AdminReport adminReport(UUID id) {
        return db.query("SELECT * FROM lost_found_reports WHERE id=?", rs -> rs.next() ? new AdminReport(notice(rs, 0), rs.getObject("reporter_id", UUID.class), rs.getString("contact_preference"), rs.getString("contact_other")) : null, id);
    }

    @Transactional
    public Notice changeStatus(UUID id, String status, UUID actor) {
        String next = status.toUpperCase();
        if (!List.of("UNDER_REVIEW", "PUBLISHED", "RESOLVED", "REJECTED", "EXPIRED", "CLOSED_UNCLAIMED").contains(next))
            throw bad("Unsupported report status");
        var current = get(id);
        boolean allowed = switch (current.status()) {
            case "SUBMITTED" -> next.equals("UNDER_REVIEW") || next.equals("REJECTED");
            case "UNDER_REVIEW" -> next.equals("PUBLISHED") || next.equals("REJECTED");
            case "PUBLISHED", "CLAIM_RECEIVED" -> List.of("RESOLVED", "EXPIRED", "CLOSED_UNCLAIMED").contains(next);
            default -> false;
        };
        if (!allowed)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Invalid report status transition");
        db.update("UPDATE lost_found_reports SET status=?,updated_at=now() WHERE id=?", next, id);
        if (next.equals("PUBLISHED") || next.equals("RESOLVED"))
            events.publish(LostFoundEvent.create(id, "LostFoundReport" + title(next), actor));
        return get(id);
    }

    @Transactional
    public AdminClaim claim(UUID id, String details, UUID actor, Set<UUID> flatIds) {
        var report = get(id);
        requireFlatAccess(report.flatId(), flatIds, false);
        if (!List.of("PUBLISHED", "CLAIM_RECEIVED").contains(report.status()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "This notice is closed for claims");
        UUID reporter = db.query("SELECT reporter_id FROM lost_found_reports WHERE id=?", rs -> {
            rs.next();
            return rs.getObject(1, UUID.class);
        }, id);
        if (reporter.equals(actor))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "The reporter cannot claim their own notice");
        UUID claimId = UUID.randomUUID();
        try {
            db.update("INSERT INTO lost_found_claims(id,report_id,claimant_id,identifying_details) VALUES (?,?,?,?)", claimId, id, actor, details);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "You have already claimed this notice");
        }
        db.update("UPDATE lost_found_reports SET status='CLAIM_RECEIVED',updated_at=now() WHERE id=?", id);
        events.publish(LostFoundEvent.create(id, "LostFoundClaimSubmitted", actor));
        return new AdminClaim(claimId, id, actor, details, "SUBMITTED", Instant.now());
    }

    @Transactional(readOnly = true)
    public List<AdminClaim> claims(UUID reportId) {
        return db.query("SELECT * FROM lost_found_claims WHERE report_id=? ORDER BY created_at DESC", (rs, n) -> new AdminClaim(rs.getObject("id", UUID.class), reportId, rs.getObject("claimant_id", UUID.class), rs.getString("identifying_details"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()), reportId);
    }

    @Transactional
    public AdminClaim decideClaim(UUID reportId, UUID claimId, String decision) {
        String status = decision.toUpperCase();
        if (!List.of("ACCEPTED", "REJECTED").contains(status)) throw bad("Claim status must be ACCEPTED or REJECTED");
        int updated = db.update("UPDATE lost_found_claims SET status=? WHERE id=? AND report_id=? AND status='SUBMITTED'", status, claimId, reportId);
        if (updated != 1)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Claim not found or already reviewed");
        return db.query("SELECT * FROM lost_found_claims WHERE id=?", (rs, n) -> new AdminClaim(claimId, reportId, rs.getObject("claimant_id", UUID.class), rs.getString("identifying_details"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()), claimId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> notifications(UUID recipient, Set<UUID> flatIds, boolean administrator) {
        if (administrator)
            return db.queryForList("SELECT event_id,report_id,event_type,created_at,read_at FROM in_app_notifications WHERE recipient_id=? ORDER BY created_at DESC LIMIT 100", recipient);
        if (flatIds == null || flatIds.isEmpty()) return List.of();
        String marks = placeholders(flatIds.size());
        return db.queryForList("SELECT n.event_id,n.report_id,n.event_type,n.created_at,n.read_at FROM in_app_notifications n JOIN lost_found_reports r ON r.id=n.report_id WHERE n.recipient_id=? AND r.flat_id IN (" + marks + ") ORDER BY n.created_at DESC LIMIT 100", notificationArgs(recipient, flatIds).toArray());
    }

    @Transactional
    public void markNotificationRead(UUID eventId, UUID recipient, Set<UUID> flatIds, boolean administrator) {
        int updated;
        if (administrator) {
            updated = db.update("UPDATE in_app_notifications SET read_at=COALESCE(read_at,now()) WHERE event_id=? AND recipient_id=?", eventId, recipient);
        } else {
            if (flatIds == null || flatIds.isEmpty()) throwNotFound();
            String marks = placeholders(flatIds.size());
            List<Object> args = notificationArgs(eventId, recipient, flatIds);
            updated = db.update("UPDATE in_app_notifications n SET read_at=COALESCE(n.read_at,now()) FROM lost_found_reports r WHERE n.event_id=? AND n.recipient_id=? AND r.id=n.report_id AND r.flat_id IN (" + marks + ")", args.toArray());
        }
        if (updated == 0) throwNotFound();
    }

    @Transactional
    public Notice savePhoto(UUID id, org.springframework.web.multipart.MultipartFile file, UUID actor, Set<UUID> flatIds, boolean administrator) {
        var report = db.query("SELECT reporter_id,flat_id FROM lost_found_reports WHERE id=?", rs -> rs.next() ? new Object[]{rs.getObject(1, UUID.class),rs.getObject(2, UUID.class)} : null, id);
        if (report == null) throwNotFound();
        UUID flatId = (UUID) report[1];
        requireFlatAccess(flatId, flatIds, administrator);
        if (!administrator && !report[0].equals(actor))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Only the reporter or society administrator can attach a photo");
        String key = photos.save(file);
        db.update("UPDATE lost_found_reports SET photo_key=?,updated_at=now() WHERE id=?", key, id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource photo(UUID id, Set<UUID> flatIds, boolean administrator) {
        var row = db.query("SELECT photo_key,status,flat_id FROM lost_found_reports WHERE id=?", rs -> rs.next() ? new Object[]{rs.getString(1),rs.getString(2),rs.getObject(3,UUID.class)} : null, id);
        if (row == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Photo not found");
        requireFlatAccess((UUID) row[2], flatIds, administrator);
        if (row[0] == null || (!administrator && !List.of("PUBLISHED", "CLAIM_RECEIVED").contains(row[1])))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Photo not found");
        String key = (String) row[0];
        return new org.springframework.core.io.FileSystemResource(photos.resolve(key));
    }

    private Notice get(UUID id) {
        Notice result = db.query("SELECT * FROM lost_found_reports WHERE id=?", rs -> rs.next() ? notice(rs, 0) : null, id);
        return result == null ? throwNotFound() : result;
    }

    private Notice throwNotFound() {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Lost and Found report not found");
    }

    private Notice notice(ResultSet rs, int n) throws SQLException {
        return new Notice(rs.getObject("id", UUID.class), rs.getObject("flat_id", UUID.class), rs.getString("report_type"), rs.getString("item_type"), rs.getString("item_other"), rs.getString("location"), rs.getTimestamp("occurred_at").toInstant(), rs.getString("description"), rs.getString("status"), rs.getString("photo_key") == null ? null : "/api/v1/lost-found/" + rs.getObject("id") + "/photo", rs.getTimestamp("created_at").toInstant());
    }

    private static void requireFlatAccess(UUID flatId, Set<UUID> flatIds, boolean administrator) {
        if (!administrator && (flatId == null || flatIds == null || !flatIds.contains(flatId)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Lost and Found report not found");
    }

    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }

    private static List<Object> notificationArgs(UUID recipient, Set<UUID> flatIds) {
        List<Object> args = new ArrayList<>(); args.add(recipient); args.addAll(flatIds); return args;
    }

    private static List<Object> notificationArgs(UUID eventId, UUID recipient, Set<UUID> flatIds) {
        List<Object> args = new ArrayList<>(); args.add(eventId); args.add(recipient); args.addAll(flatIds); return args;
    }

    private static com.emerald.member.api.ApiFailure bad(String message) {
        return new com.emerald.member.api.ApiFailure(400, "VALIDATION_FAILED", message);
    }

    private static String title(String value) {
        return value.substring(0, 1) + value.substring(1).toLowerCase();
    }
}
