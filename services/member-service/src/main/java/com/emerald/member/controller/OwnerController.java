package com.emerald.member.controller;

import com.emerald.member.auth.*;
import com.emerald.member.dto.*;
import com.emerald.member.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController @RequestMapping("/api/v1/owners")
public class OwnerController {
 private final MemberService members;private final MembershipService memberships;private final AccountController accounts;private final AccessRegistry registry;
 public OwnerController(MemberService members,MembershipService memberships,AccountController accounts,AccessRegistry registry){this.members=members;this.memberships=memberships;this.accounts=accounts;this.registry=registry;}
 @org.springframework.beans.factory.annotation.Autowired private com.emerald.member.security.MemberAccess access;
 @GetMapping @Transactional
 public Object list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="100") int size){
  if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw new com.emerald.member.api.ApiFailure(400,"INVALID_PAGE","Use page >= 0 and size 1-100");
  if(!access.has("MEMBER_VIEW","SOCIETY")&&!access.has("MEMBER_VIEW","SELF"))throw new org.springframework.security.access.AccessDeniedException("No owner directory access");
  String sql="SELECT fm.*,m.full_name FROM flat_memberships fm JOIN members m ON m.id=fm.member_id WHERE fm.relationship_type='OWNER'";
  if(access.has("MEMBER_VIEW","SOCIETY"))return registry.database().queryForList(sql+" ORDER BY m.full_name,fm.id LIMIT ? OFFSET ?",size,(long)page*size);
  return registry.database().queryForList(sql+" AND fm.member_id=? AND fm.end_date IS NULL AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE) AND m.status='ACTIVE' ORDER BY fm.id LIMIT ? OFFSET ?",access.ownMember(),size,(long)page*size);
 }
 public record Register(@NotNull @Valid CreateMemberRequest person,@NotNull UUID flatId,@NotNull @PastOrPresent LocalDate startDate,@NotBlank String username,@NotBlank String password){}
 @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED) @Transactional
 @PreAuthorize("@memberAccess.has('OWNER_REGISTER','SOCIETY') and @memberAccess.has('MEMBER_CREATE','SOCIETY') and @memberAccess.has('USER_CREATE','SOCIETY') and @memberAccess.has('USER_ASSIGN_ROLE','SOCIETY')")
 public Object register(@Valid @RequestBody Register r){
  registry.lock();var person=members.create(r.person());var ownership=memberships.assign(person.memberId(),new CreateMembershipRequest(r.flatId(),RelationshipType.OWNER,r.startDate()));
  var account=accounts.create(new AccountController.Create(r.username(),r.password(),"FLAT_OWNER","EMERALD",person.memberId(),Set.of("FLAT_OWNER")));
  return Map.of("person",person,"ownership",ownership,"account",account);
 }
 public record Transfer(@NotNull UUID previousMembershipId,@NotNull UUID previousMemberId,@NotNull UUID newOwnerMemberId,@NotNull UUID flatId,@NotNull LocalDate date){}
 @PostMapping("/transfer") @Transactional
 @PreAuthorize("@memberAccess.has('OWNER_REGISTER','SOCIETY') and @memberAccess.has('OWNER_END_OWNERSHIP','SOCIETY')")
 public Object transfer(@Valid @RequestBody Transfer r){
  registry.lock();var rows=registry.database().queryForList("SELECT id FROM flat_memberships WHERE id=? AND member_id=? AND flat_id=? AND relationship_type='OWNER' AND end_date IS NULL",r.previousMembershipId(),r.previousMemberId(),r.flatId());
  if(rows.isEmpty())throw new com.emerald.member.api.ApiFailure(409,"INVALID_OWNERSHIP","Select the open owner membership for this flat");
  if(r.previousMemberId().equals(r.newOwnerMemberId())||r.date().isAfter(LocalDate.now(java.time.ZoneOffset.UTC)))throw new com.emerald.member.api.ApiFailure(400,"INVALID_TRANSFER","Choose a different owner and a transfer date no later than today");
  memberships.end(r.previousMemberId(),r.previousMembershipId(),new EndMembershipRequest(r.date()));
  return memberships.assign(r.newOwnerMemberId(),new CreateMembershipRequest(r.flatId(),RelationshipType.OWNER,r.date()));
 }
}
