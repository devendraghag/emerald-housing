package com.emerald.member.auth;

import com.emerald.member.api.ApiFailure;
import com.emerald.member.audit.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

@RestController @RequestMapping("/api/v1/access")
public class AccessController {
 private final AccessRegistry registry;private final AuditTrail audit;
 public AccessController(AccessRegistry registry,AuditTrail audit){this.registry=registry;this.audit=audit;}
 public record Grant(@NotBlank String permission,@Pattern(regexp="SOCIETY|OWN_FLATS|SELF") @NotNull String scope){}
 public record Role(@Pattern(regexp="[A-Z][A-Z0-9_]{1,31}") @NotNull String code,@NotBlank @Size(max=200) String description,boolean enabled,@NotNull Long version,@NotNull @Size(max=100) List<@Valid Grant> grants){}
 public record Permission(@Pattern(regexp="[A-Z][A-Z0-9_]{1,63}") @NotNull String code,@NotBlank @Size(max=200) String description){}
 @GetMapping("/roles") @PreAuthorize("@memberAccess.has('ROLE_VIEW','SOCIETY')") @Transactional(readOnly=true)
 public Object roles(){return registry.database().queryForList("SELECT * FROM access_roles ORDER BY code").stream().map(row->{row.put("grants",registry.database().queryForList("SELECT permission_code AS permission,scope FROM role_permissions WHERE role_code=? ORDER BY permission_code,scope",row.get("code")));return row;}).toList();}
 @GetMapping("/permissions") @PreAuthorize("@memberAccess.has('PERMISSION_VIEW','SOCIETY')") @Transactional(readOnly=true)
 public Object permissions(){return registry.database().queryForList("SELECT * FROM access_permissions ORDER BY code");}
 @PostMapping("/permissions") @PreAuthorize("@memberAccess.has('PERMISSION_MANAGE','SOCIETY')") @Transactional
 public Object permission(@Valid @RequestBody Permission r){
  registry.lock();if(Boolean.TRUE.equals(registry.database().queryForObject("SELECT EXISTS(SELECT 1 FROM access_permissions WHERE code=?)",Boolean.class,r.code())))throw new ApiFailure(409,"PERMISSION_EXISTS","Permission already exists");
  registry.database().update("INSERT INTO access_permissions VALUES (?,?)",r.code(),r.description());audit.record("CREATE_PERMISSION","PERMISSION",entityId(r.code()),null,r);return r;
 }
 @PutMapping("/permissions/{code}") @PreAuthorize("@memberAccess.has('PERMISSION_MANAGE','SOCIETY')") @Transactional
 public Object describe(@PathVariable String code,@Valid @RequestBody Permission r){
  registry.lock();if(!code.equals(r.code()))throw new ApiFailure(400,"PERMISSION_MISMATCH","Permission code cannot change");
  var rows=registry.database().queryForList("SELECT * FROM access_permissions WHERE code=?",code);if(rows.isEmpty())throw new ApiFailure(404,"PERMISSION_NOT_FOUND","Permission not found");
  registry.database().update("UPDATE access_permissions SET description=? WHERE code=?",r.description(),code);audit.record("DESCRIBE_PERMISSION","PERMISSION",entityId(code),rows.getFirst(),r);return r;
 }
 @PostMapping("/roles") @PreAuthorize("@memberAccess.has('ROLE_CREATE','SOCIETY') and @memberAccess.has('ROLE_ASSIGN_PERMISSION','SOCIETY')") @Transactional
 public Object create(@Valid @RequestBody Role r){return save(r,true);}
 @PutMapping("/roles/{code}") @PreAuthorize("@memberAccess.has('ROLE_UPDATE','SOCIETY') and @memberAccess.has('ROLE_ASSIGN_PERMISSION','SOCIETY')") @Transactional
 public Object update(@PathVariable String code,@Valid @RequestBody Role r){if(!code.equals(r.code()))throw new ApiFailure(400,"ROLE_MISMATCH","Role code cannot change");return save(r,false);}
 private UUID entityId(String code){return UUID.nameUUIDFromBytes(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private Object save(Role r,boolean create){
  registry.lock();var db=registry.database();var rows=db.queryForList("SELECT * FROM access_roles WHERE code=?",r.code());
  var seen=new HashSet<String>();for(var grant:r.grants())if(!seen.add(grant.permission()))throw new ApiFailure(400,"DUPLICATE_PERMISSION","Choose one scope per permission in a role; use multiple roles to combine scopes");
  Object before=rows.isEmpty()?null:Map.of("role",rows.getFirst(),"grants",db.queryForList("SELECT * FROM role_permissions WHERE role_code=?",r.code()));
  if(create){if(!rows.isEmpty())throw new ApiFailure(409,"ROLE_EXISTS","Role already exists");db.update("INSERT INTO access_roles(code,description,enabled) VALUES (?,?,?)",r.code(),r.description(),r.enabled());}
  else{
   if(rows.isEmpty())throw new ApiFailure(404,"ROLE_NOT_FOUND","Role not found");
   if(Boolean.TRUE.equals(rows.getFirst().get("system_role")))throw new ApiFailure(409,"PROTECTED_ROLE","Built-in administrator roles are protected; create a custom role");
   if(db.update("UPDATE access_roles SET description=?,enabled=?,version=version+1 WHERE code=? AND version=?",r.description(),r.enabled(),r.code(),r.version())!=1)throw new ApiFailure(409,"VERSION_CONFLICT","Role changed; reload it");
  }
  db.update("DELETE FROM role_permissions WHERE role_code=?",r.code());
  for(var g:r.grants()){
   if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM access_permissions WHERE code=?)",Boolean.class,g.permission())))throw new ApiFailure(400,"UNKNOWN_PERMISSION","Unknown permission");
   boolean scoped=g.permission().startsWith("FAMILY_")||g.permission().equals("MEMBER_VIEW")||g.permission().equals("FLAT_VIEW");
   if(!"SOCIETY".equals(g.scope())&&!scoped)throw new ApiFailure(400,"UNSUPPORTED_SCOPE","This permission requires SOCIETY scope");
   if(g.permission().equals("MEMBER_VIEW")&&"OWN_FLATS".equals(g.scope())||g.permission().equals("FLAT_VIEW")&&"SELF".equals(g.scope()))throw new ApiFailure(400,"UNSUPPORTED_SCOPE","Unsupported directory scope");
   if(g.permission().startsWith("FAMILY_")&&!g.permission().equals("FAMILY_VIEW")&&"SELF".equals(g.scope()))throw new ApiFailure(400,"UNSUPPORTED_SCOPE","Family mutations require SOCIETY or OWN_FLATS scope");
   db.update("INSERT INTO role_permissions VALUES (?,?,?) ON CONFLICT DO NOTHING",r.code(),g.permission(),g.scope());
  }
  var after=new Role(r.code(),r.description(),r.enabled(),create?0L:r.version()+1,r.grants());
  audit.record(create?"CREATE_ROLE":"UPDATE_ROLE","ROLE",entityId(r.code()),before,after);return Map.of("code",r.code(),"version",after.version());
 }
}
