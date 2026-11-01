package com.emerald.member.auth;

import java.util.*;
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
import com.emerald.member.security.MemberAccess;

@RestController @RequestMapping("/api/v1/accounts")
public class AccountController {
 private final AdminAccountRepository accounts; private final MemberRepository members; private final PasswordEncoder passwords;
 private final AuditTrail audit; private final AccessRegistry registry; private final MemberAccess access;
 public AccountController(AdminAccountRepository accounts,MemberRepository members,PasswordEncoder passwords,AuditTrail audit,AccessRegistry registry,MemberAccess access) {
  this.accounts=accounts;this.members=members;this.passwords=passwords;this.audit=audit;this.registry=registry;this.access=access;
 }
 public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9._@+-]{0,119}") String username,@NotBlank String password,String role,@NotBlank String societyCode,UUID memberId,@Size(max=20) Set<@NotBlank String> roles) {
  public Create(String username,String password,String role,String societyCode,UUID memberId){this(username,password,role,societyCode,memberId,null);}
 }
 public record Permissions(String role,@NotBlank String societyCode,UUID memberId,boolean enabled,@NotNull Long version,@Size(max=20) Set<@NotBlank String> roles) {
  public Permissions(String role,String societyCode,UUID memberId,boolean enabled,Long version){this(role,societyCode,memberId,enabled,version,null);}
 }
 public record View(UUID accountId,String username,String role,String societyCode,UUID memberId,boolean enabled,long version,Set<String> roles) { }
 public record PasswordReset(@NotBlank String password,@NotNull Long version) { }
 public record PasswordChange(@NotBlank String currentPassword,@NotBlank String password,@NotNull Long version) { }
 private View view(AdminAccountEntity a) {
  if(!"EMERALD".equals(a.getSocietyCode())) throw new org.springframework.security.access.AccessDeniedException("Wrong society");
  return new View(a.getId(),a.getUsername(),a.getRole(),a.getSocietyCode(),a.getMemberId(),a.isEnabled(),a.getVersion(),registry.assignedRoles(a.getId()));
 }
 private Set<String> validate(String role,Set<String> roles,String society,UUID member) {
  Set<String> selected=roles!=null?Set.copyOf(roles):role==null?Set.of():Set.of(role);registry.validateRoles(selected);
  if(!"EMERALD".equals(society)) throw new ApiFailure(400,"INVALID_SCOPE","Use societyCode EMERALD");
  boolean personRequired=registry.requiresPerson(selected);
  if((personRequired && member==null) || (member!=null && members.findByIdAndStatus(member,com.emerald.member.service.MemberStatus.ACTIVE).isEmpty()))throw new ApiFailure(400,"INVALID_MEMBER_LINK","Link the account to an active member");
  return selected;
 }
 private AdminAccountEntity find(UUID id) { return accounts.findById(id).orElseThrow(()->new ApiFailure(404,"ACCOUNT_NOT_FOUND","Account not found")); }
 private void passwordLength(String password){int length=password.getBytes(StandardCharsets.UTF_8).length;if(length<14||length>72)throw new ApiFailure(400,"INVALID_PASSWORD","Password must be 14-72 UTF-8 bytes");}
 private static String username(String value){return value.trim().toLowerCase(Locale.ROOT);}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
 @PreAuthorize("@memberAccess.has('USER_CREATE','SOCIETY') and @memberAccess.has('USER_ASSIGN_ROLE','SOCIETY') and @memberAccess.has('ROLE_VIEW','SOCIETY')")
 public View create(@Valid @RequestBody Create request) {
  registry.lock();var selected=validate(request.role(),request.roles(),request.societyCode(),request.memberId());registry.ensureDelegable(registry.grantsForRoles(selected),access.identity().adminId());passwordLength(request.password());
  String username=username(request.username());if(accounts.existsByUsernameIgnoreCase(username)) throw new ApiFailure(409,"USERNAME_EXISTS","Username is already registered");
  var a=accounts.saveAndFlush(new AdminAccountEntity(UUID.randomUUID(),username,passwords.encode(request.password()),selected.stream().sorted().findFirst().orElseThrow(),request.societyCode(),request.memberId()));
  registry.assign(a.getId(),selected);var result=view(a);audit.record("CREATE_ACCOUNT","ACCOUNT",a.getId(),null,result);return result;
 }
 @GetMapping @Transactional(readOnly=true) @PreAuthorize("@memberAccess.has('USER_VIEW','SOCIETY')")
 public List<View> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) String search,@RequestParam(required=false) String role,@RequestParam(required=false) Boolean enabled) {
  if(page<0 || size<1 || size>100)throw new ApiFailure(400,"INVALID_PAGE","Use a nonnegative page and size 1-100");
  String term=search==null?"":search.trim();if(term.length()>120)throw new ApiFailure(400,"INVALID_SEARCH","Search must be at most 120 characters");
  var sql=new StringBuilder("SELECT DISTINCT a.id FROM admin_users a LEFT JOIN account_roles ar ON ar.account_id=a.id WHERE a.society_code='EMERALD'");var args=new ArrayList<Object>();
  if(enabled!=null){sql.append(" AND a.enabled=?");args.add(enabled);}
  if(role!=null&&!role.isBlank()){String normalized=role.trim().toUpperCase(Locale.ROOT);if(!Boolean.TRUE.equals(registry.database().queryForObject("SELECT EXISTS(SELECT 1 FROM access_roles WHERE code=?)",Boolean.class,normalized)))throw new ApiFailure(400,"INVALID_ROLE","Unknown role filter");sql.append(" AND ar.role_code=?");args.add(normalized);}
  if(!term.isEmpty()){sql.append(" AND a.username ILIKE ? ESCAPE '!' ");args.add("%"+term.replace("!","!!").replace("%","!%").replace("_","!_")+"%");}
  sql.append(" ORDER BY a.id LIMIT ? OFFSET ?");args.add(size);args.add((long)page*size);
  return registry.database().queryForList(sql.toString(),UUID.class,args.toArray()).stream().map(accounts::findById).flatMap(Optional::stream).map(this::view).toList();
 }
 @GetMapping("/me") @Transactional
 public Object me() {var actor=access.identity();var a=find(actor.adminId());return Map.of("account",view(a),"permissions",registry.grants(a.getId()),"flatIds",actor.flatIds());}
 @GetMapping("/{id}") @Transactional(readOnly=true) @PreAuthorize("@memberAccess.has('USER_VIEW','SOCIETY')")
 public View get(@PathVariable UUID id) {return view(find(id));}
 @PutMapping("/{id}/permissions") @Transactional
 @PreAuthorize("@memberAccess.has('USER_UPDATE','SOCIETY') and @memberAccess.has('USER_ASSIGN_ROLE','SOCIETY') and @memberAccess.has('ROLE_VIEW','SOCIETY')")
 public View permissions(@PathVariable UUID id,@Valid @RequestBody Permissions request) {
  registry.lock();var selected=validate(request.role(),request.roles(),request.societyCode(),request.memberId());var a=find(id);
  if(a.getVersion()!=request.version())throw new ApiFailure(409,"VERSION_CONFLICT","Account changed; reload it");
  if(access.identity().adminId().equals(id))throw new ApiFailure(409,"SELF_PERMISSION_CHANGE","Use another administrator to change your account permissions");
  registry.ensureDelegable(registry.grantsForRoles(selected),access.identity().adminId());
  var before=view(a);a.permissions(selected.stream().sorted().findFirst().orElseThrow(),request.societyCode(),request.memberId(),request.enabled());
  accounts.saveAndFlush(a);registry.assign(id,selected);registry.requireAdministrator();var result=view(a);audit.record("CHANGE_PERMISSIONS","ACCOUNT",id,before,result);return result;
 }
 @PutMapping("/{id}/password") @Transactional
 @PreAuthorize("@memberAccess.has('USER_RESET_PASSWORD','SOCIETY')")
 public View resetPassword(@PathVariable UUID id,@Valid @RequestBody PasswordReset request){
  registry.lock();var account=find(id);if(account.getVersion()!=request.version())throw new ApiFailure(409,"VERSION_CONFLICT","Account changed; reload it");
  var before=view(account);passwordLength(request.password());account.resetPassword(passwords.encode(request.password()));accounts.saveAndFlush(account);var result=view(account);audit.record("RESET_ACCOUNT_PASSWORD","ACCOUNT",id,before,result);return result;
 }
 @PutMapping("/me/password") @Transactional
 public View changeOwnPassword(@Valid @RequestBody PasswordChange request){
  registry.lock();var actor=access.identity();var account=find(actor.adminId());
  if(account.getVersion()!=request.version())throw new ApiFailure(409,"VERSION_CONFLICT","Account changed; reload it");
  if(!passwords.matches(request.currentPassword(),account.getPasswordHash()))throw new ApiFailure(400,"INVALID_CURRENT_PASSWORD","Current password is incorrect");
  passwordLength(request.password());var before=view(account);account.resetPassword(passwords.encode(request.password()));accounts.saveAndFlush(account);var result=view(account);audit.record("CHANGE_OWN_PASSWORD","ACCOUNT",account.getId(),before,result);return result;
 }
}
