package com.emerald.member.auth;
import java.util.UUID;
import java.util.Set;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.emerald.member.repository.MemberRepository;
import com.emerald.member.audit.AuditTrail;
import com.emerald.member.api.ApiFailure;
@RestController @RequestMapping("/api/v1/accounts")
@PreAuthorize("@memberAccess.manage()")
public class AccountController {
 private final AdminAccountRepository accounts; private final MemberRepository members; private final PasswordEncoder passwords; private final AuditTrail audit;
 public AccountController(AdminAccountRepository accounts,MemberRepository members,PasswordEncoder passwords,AuditTrail audit) { this.accounts=accounts;this.members=members;this.passwords=passwords;this.audit=audit; }
 public record Create(@NotBlank @Size(max=120) String username,@NotBlank String password,@NotBlank String role,@NotBlank String societyCode,UUID memberId) { }
 public record Permissions(@NotBlank String role,@NotBlank String societyCode,UUID memberId,boolean enabled,@NotNull Long version) { }
 public record View(UUID accountId,String username,String role,String societyCode,UUID memberId,boolean enabled,long version) { }
 private View view(AdminAccountEntity a) { if(!"EMERALD".equals(a.getSocietyCode()))throw new org.springframework.security.access.AccessDeniedException("Wrong society");return new View(a.getId(),a.getUsername(),a.getRole(),a.getSocietyCode(),a.getMemberId(),a.isEnabled(),a.getVersion()); }
 private void validate(String role,String society,UUID member) {
  if(!Set.of("ADMIN","SOCIETY_ADMIN","COMMITTEE","RESIDENT").contains(role) || !"EMERALD".equals(society)) throw new ApiFailure(400,"INVALID_SCOPE","Use a supported role and societyCode EMERALD");
  if(("RESIDENT".equals(role) && member==null) || (member!=null && !members.existsById(member))) throw new ApiFailure(400,"INVALID_MEMBER_LINK","Link the account to an existing member");
 }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
 public View create(@Valid @RequestBody Create request) {
  validate(request.role(),request.societyCode(),request.memberId());
  int length=request.password().getBytes(StandardCharsets.UTF_8).length;
  if(length<14 || length>72) throw new ApiFailure(400,"INVALID_PASSWORD","Password must be 14-72 UTF-8 bytes");
  var account=accounts.saveAndFlush(new AdminAccountEntity(UUID.randomUUID(),request.username().trim(),passwords.encode(request.password()),request.role(),request.societyCode(),request.memberId()));
  var result=view(account); audit.record("CREATE_ACCOUNT","ACCOUNT",account.getId(),null,result);return result;
 }
 @GetMapping("/{id}") @Transactional(readOnly=true)
 public View get(@PathVariable UUID id) { return view(accounts.findById(id).orElseThrow(()->new ApiFailure(404,"ACCOUNT_NOT_FOUND","Account not found"))); }
 @PutMapping("/{id}/permissions") @Transactional
 public View permissions(@PathVariable UUID id,@Valid @RequestBody Permissions request) {
  validate(request.role(),request.societyCode(),request.memberId());
  var account=accounts.findById(id).orElseThrow(()->new ApiFailure(404,"ACCOUNT_NOT_FOUND","Account not found"));
  if(account.getVersion()!=request.version()) throw new ApiFailure(409,"VERSION_CONFLICT","Account changed; reload it");
  var actor=(com.emerald.member.security.SessionIdentityResponse)org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  if(actor.adminId().equals(id)) throw new ApiFailure(409,"SELF_PERMISSION_CHANGE","Use another administrator to change your account permissions");
  var before=view(account);account.permissions(request.role(),request.societyCode(),request.memberId(),request.enabled());
  var result=view(accounts.saveAndFlush(account));audit.record("CHANGE_PERMISSIONS","ACCOUNT",id,before,result);return result;
 }
}
