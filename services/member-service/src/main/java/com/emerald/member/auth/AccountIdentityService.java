package com.emerald.member.auth;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.emerald.member.repository.*;
import com.emerald.member.service.MemberStatus;
import com.emerald.member.api.ApiFailure;
import com.emerald.member.entity.FlatMembershipEntity;
import com.emerald.member.entity.MembershipPeriod;
@Service
public class AccountIdentityService {
 @org.springframework.beans.factory.annotation.Autowired private AccessRegistry registry;
 private final AdminAccountRepository accounts; private final MemberRepository members; private final FlatMembershipRepository memberships;
 public AccountIdentityService(AdminAccountRepository accounts,MemberRepository members,FlatMembershipRepository memberships) { this.accounts=accounts;this.members=members;this.memberships=memberships; }
 @Transactional // Current permissions and assignments must read the primary, not a lagging replica.
 public AdminIdentityResponse current(UUID id) {
  var account=accounts.findById(id).filter(AdminAccountEntity::isEnabled).orElseThrow(()->new ApiFailure(401,"ACCOUNT_DISABLED","Account is unavailable"));
  Set<UUID> flatIds=Set.of();
  var roles=registry.roles(id);
  var permissions=registry.grants(id);
  if(roles.isEmpty()) throw new ApiFailure(401,"NO_ACTIVE_ROLE","Account has no enabled role");
  if(account.getMemberId()!=null || permissions.stream().anyMatch(p->p.endsWith(":SELF")||p.endsWith(":OWN_FLATS"))) {
   if(account.getMemberId()==null || members.findByIdAndStatus(account.getMemberId(),MemberStatus.ACTIVE).isEmpty())
    throw new ApiFailure(401,"ACCOUNT_UNLINKED","Scoped account must link to an active member");
   var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
   flatIds=memberships.findAllByMemberIdOrderByStartDateDesc(account.getMemberId()).stream()
    .filter(MembershipPeriod.currentOn(today))
    .map(FlatMembershipEntity::getFlatId).collect(Collectors.toUnmodifiableSet());
  }
  if(account.getMemberId()!=null) {
   var allFlats=new java.util.HashSet<>(flatIds);
   allFlats.addAll(registry.database().queryForList("SELECT DISTINCT f.flat_id FROM family_members f JOIN flat_memberships fm ON fm.id=f.owner_membership_id JOIN members m ON m.id=fm.member_id WHERE f.linked_member_id=? AND f.active AND fm.relationship_type='OWNER' AND fm.end_date IS NULL AND fm.start_date<=CAST(CURRENT_TIMESTAMP AT TIME ZONE 'UTC' AS DATE) AND m.status='ACTIVE'",UUID.class,account.getMemberId()));
   flatIds=Set.copyOf(allFlats);
  }
  return new AdminIdentityResponse(account.getId(),account.getUsername(),account.getRole(),account.getSocietyCode(),account.getMemberId(),flatIds,roles,permissions);
 }
}
