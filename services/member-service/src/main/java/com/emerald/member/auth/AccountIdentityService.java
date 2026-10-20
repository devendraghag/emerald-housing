package com.emerald.member.auth;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.emerald.member.repository.*;
import com.emerald.member.service.MemberStatus;
import com.emerald.member.api.ApiFailure;
@Service
public class AccountIdentityService {
 private final AdminAccountRepository accounts; private final MemberRepository members; private final FlatMembershipRepository memberships;
 public AccountIdentityService(AdminAccountRepository accounts,MemberRepository members,FlatMembershipRepository memberships) { this.accounts=accounts;this.members=members;this.memberships=memberships; }
 @Transactional // Current permissions and assignments must read the primary, not a lagging replica.
 public AdminIdentityResponse current(UUID id) {
  var account=accounts.findById(id).filter(AdminAccountEntity::isEnabled).orElseThrow(()->new ApiFailure(401,"ACCOUNT_DISABLED","Account is unavailable"));
  Set<UUID> flatIds=Set.of();
  if("RESIDENT".equals(account.getRole())) {
   if(account.getMemberId()==null || members.findByIdAndStatus(account.getMemberId(),MemberStatus.ACTIVE).isEmpty())
    throw new ApiFailure(401,"ACCOUNT_UNLINKED","Resident account must link to an active member");
   var today=java.time.LocalDate.now(java.time.ZoneOffset.UTC);
   flatIds=memberships.findAllByMemberIdOrderByStartDateDesc(account.getMemberId()).stream()
    .filter(row->!row.getStartDate().isAfter(today) && row.getEndDate()==null)
    .map(row->row.getFlatId()).collect(Collectors.toUnmodifiableSet());
  }
  return new AdminIdentityResponse(account.getId(),account.getUsername(),account.getRole(),account.getSocietyCode(),account.getMemberId(),flatIds);
 }
}
