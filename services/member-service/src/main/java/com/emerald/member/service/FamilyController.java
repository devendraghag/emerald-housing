package com.emerald.member.service;

import com.emerald.member.api.ApiFailure;
import com.emerald.member.audit.AuditTrail;
import com.emerald.member.auth.AccessRegistry;
import com.emerald.member.security.MemberAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@RestController @RequestMapping("/api/v1/families")
public class FamilyController {
 private final AccessRegistry registry;private final MemberAccess access;private final AuditTrail audit;
 public FamilyController(AccessRegistry registry,MemberAccess access,AuditTrail audit){this.registry=registry;this.access=access;this.audit=audit;}
 public record Details(@NotBlank @Size(max=160) String fullName,@NotBlank @Size(max=40) String relationship,@Size(max=20) String mobileNumber,@Email @Size(max=254) String email,UUID linkedMemberId){}
 public record Create(@NotNull UUID ownerMembershipId,@NotNull @Valid Details details){}
 public record Update(@NotNull Long version,@NotNull @Valid Details details){}
 public record Version(@NotNull Long version){}
 private Map<String,Object> family(UUID id){var rows=registry.database().queryForList("SELECT * FROM family_members WHERE id=?",id);if(rows.isEmpty())throw new ApiFailure(404,"FAMILY_NOT_FOUND","Family record not found");return rows.getFirst();}
 private Map<String,Object> ownership(UUID id){var rows=registry.database().queryForList("SELECT fm.*,m.status AS member_status FROM flat_memberships fm JOIN members m ON m.id=fm.member_id WHERE fm.id=? AND fm.relationship_type='OWNER'",id);if(rows.isEmpty())throw new ApiFailure(404,"OWNER_NOT_FOUND","Owner membership not found");return rows.getFirst();}
 private boolean current(Map<String,Object> owner){return owner.get("end_date")==null&&!((java.sql.Date)owner.get("start_date")).toLocalDate().isAfter(java.time.LocalDate.now(java.time.ZoneOffset.UTC))&&"ACTIVE".equals(owner.get("member_status"));}
 private void authorize(String permission,Map<String,Object> owner,Map<String,Object> row){
  if(access.has(permission,"SOCIETY"))return;
  if(!current(owner))throw new AccessDeniedException("Ownership is no longer active");
  if(access.has(permission,"OWN_FLATS")&&owner.get("member_id").equals(access.ownMember()))return;
  if(permission.equals("FAMILY_VIEW")&&access.has(permission,"SELF")&&row!=null&&access.ownMember()!=null&&access.ownMember().equals(row.get("linked_member_id")))return;
  throw new AccessDeniedException("No permission for this family record");
 }
 private void validateLink(Details d,Map<String,Object> before){
  if(d.linkedMemberId()!=null){
   if(!access.has("USER_UPDATE","SOCIETY")&&(before==null||!d.linkedMemberId().equals(before.get("linked_member_id"))))throw new AccessDeniedException("Only administrators can link a family record to a login identity");
   if(!Boolean.TRUE.equals(registry.database().queryForObject("SELECT EXISTS(SELECT 1 FROM members WHERE id=? AND status='ACTIVE')",Boolean.class,d.linkedMemberId())))throw new ApiFailure(400,"INVALID_MEMBER_LINK","Linked person must be active");
  }else if(before!=null&&before.get("linked_member_id")!=null&&!access.has("USER_UPDATE","SOCIETY"))throw new AccessDeniedException("Only administrators can remove identity links");
 }
 @GetMapping @Transactional
 public Object list(@RequestParam(required=false) UUID flatId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
  if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new ApiFailure(400,"INVALID_PAGE","Use page >= 0 and size 1-100");
  var args=new ArrayList<Object>();String where=" WHERE 1=1";
  if(flatId!=null){where+=" AND f.flat_id=?";args.add(flatId);}
  if(!access.has("FAMILY_VIEW","SOCIETY")){
   where+=" AND fm.end_date IS NULL AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE) AND m.status='ACTIVE' AND (";
   var scopes=new ArrayList<String>();
   if(access.has("FAMILY_VIEW","OWN_FLATS")&&access.ownMember()!=null){scopes.add("fm.member_id=?");args.add(access.ownMember());}
   if(access.has("FAMILY_VIEW","SELF")&&access.ownMember()!=null){scopes.add("(f.linked_member_id=? AND f.active)");args.add(access.ownMember());}
   if(scopes.isEmpty())throw new AccessDeniedException("No family directory access");where+=String.join(" OR ",scopes)+")";
  }
  args.add(size);args.add((long)page*size);
  return registry.database().queryForList("SELECT f.* FROM family_members f JOIN flat_memberships fm ON fm.id=f.owner_membership_id JOIN members m ON m.id=fm.member_id"+where+" ORDER BY f.created_at,f.id LIMIT ? OFFSET ?",args.toArray());
 }
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
 public Object create(@Valid @RequestBody Create r){
  registry.lock();var owner=ownership(r.ownerMembershipId());authorize("FAMILY_ADD",owner,null);
  if(!current(owner))throw new ApiFailure(409,"INACTIVE_OWNERSHIP","Family additions require current ownership");validateLink(r.details(),null);
  UUID id=UUID.randomUUID();var d=r.details();registry.database().update("INSERT INTO family_members(id,owner_membership_id,flat_id,full_name,relationship,mobile_number,email,linked_member_id) VALUES (?,?,?,?,?,?,?,?)",id,r.ownerMembershipId(),owner.get("flat_id"),d.fullName().trim(),d.relationship().trim(),d.mobileNumber(),d.email(),d.linkedMemberId());
  var result=family(id);audit.record("ADD_FAMILY","FAMILY",id,null,result);return result;
 }
 @PutMapping("/{id}") @Transactional
 public Object update(@PathVariable UUID id,@Valid @RequestBody Update r){
  registry.lock();var before=family(id);authorize("FAMILY_UPDATE",ownership((UUID)before.get("owner_membership_id")),before);validateLink(r.details(),before);var d=r.details();
  if(registry.database().update("UPDATE family_members SET full_name=?,relationship=?,mobile_number=?,email=?,linked_member_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND active",d.fullName().trim(),d.relationship().trim(),d.mobileNumber(),d.email(),d.linkedMemberId(),id,r.version())!=1)throw new ApiFailure(409,"VERSION_CONFLICT","Family record changed or is inactive; reload it");
  var result=family(id);audit.record("UPDATE_FAMILY","FAMILY",id,before,result);return result;
 }
 @PatchMapping("/{id}/deactivate") @Transactional
 public Object deactivate(@PathVariable UUID id,@Valid @RequestBody Version r){
  registry.lock();var before=family(id);authorize("FAMILY_DEACTIVATE",ownership((UUID)before.get("owner_membership_id")),before);
  if(registry.database().update("UPDATE family_members SET active=FALSE,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=? AND active",id,r.version())!=1)throw new ApiFailure(409,"VERSION_CONFLICT","Family record changed or is inactive; reload it");
  var result=family(id);audit.record("DEACTIVATE_FAMILY","FAMILY",id,before,result);return result;
 }
}
