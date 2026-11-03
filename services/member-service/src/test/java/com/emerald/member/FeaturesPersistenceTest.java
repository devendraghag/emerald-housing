package com.emerald.member;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.emerald.member.security.SessionIdentityResponse;
import com.emerald.member.service.MemberService;
import com.emerald.member.api.*;
@SpringBootTest(properties={"society.cache.warmup.enabled=false","TRACING_OTLP_ENABLED=false","management.otlp.metrics.export.enabled=false","lost-found.outbox-poll=60000","spring.kafka.listener.auto-startup=false","spring.kafka.admin.auto-create=false"})
@Testcontainers(disabledWithoutDocker=true)
class FeaturesPersistenceTest {
 private static long testMobileSequence=1L;
 @Test void idempotencyReplayReturnsOriginalMemberWithoutDuplicateWrites() {
  var request=new com.emerald.member.dto.CreateMemberRequest("Asha Replay","9876543291",null);
  var first=service.createIdempotent(request,"create-replay",actor);
  var replay=service.createIdempotent(request,"create-replay",actor);
  assertFalse(first.replayed());assertTrue(replay.replayed());
  assertEquals(first.member(),replay.member());
  assertEquals(1,jdbc.queryForObject("select count(*) from members",Integer.class));
  assertEquals(1,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,first.member().memberId()));
 }
 @Test void idempotencyConflictPreservesOriginalMember() {
  var request=new com.emerald.member.dto.CreateMemberRequest("Asha Original","9876543292",null);
  var first=service.createIdempotent(request,"create-conflict",actor);
  assertThrows(com.emerald.member.exception.IdempotencyConflictException.class,()->service.createIdempotent(
   new com.emerald.member.dto.CreateMemberRequest("Changed","9876543293",null),"create-conflict",actor));
  assertEquals("Asha Original",service.get(first.member().memberId()).fullName());
  assertEquals(1,jdbc.queryForObject("select count(*) from members",Integer.class));
 }
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) { p.add("app.datasource.writer.jdbc-url",POSTGRES::getJdbcUrl);p.add("app.datasource.writer.username",POSTGRES::getUsername);p.add("app.datasource.writer.password",POSTGRES::getPassword); }
 @Autowired MemberService service; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 @Autowired jakarta.persistence.EntityManagerFactory emf;
 @Autowired com.emerald.member.auth.AccountController accounts;
 @Autowired com.emerald.member.auth.AccountIdentityService identities;
 @Autowired com.emerald.member.audit.AuditTrail audit;
 @Autowired com.emerald.member.lostfound.LostFoundService lostFound;
 @Autowired com.emerald.member.lostfound.LostFoundEventListener lostFoundListener;
 @Autowired com.emerald.member.notices.CommunityNoticeService communityNotices;
 @Autowired com.emerald.member.notices.CommunityNoticeEventListener communityNoticeListener;
 @Autowired tools.jackson.databind.json.JsonMapper json;
 private final UUID actor=UUID.randomUUID();
 private void login(String role,UUID member,Set<UUID> flats) {
  var grants=new HashSet<>(jdbc.queryForList("select permission_code || ':' || scope from role_permissions where role_code=?",String.class,role));
  var identity=new SessionIdentityResponse(actor,"test-admin",role,"EMERALD",member,flats,Set.of(role),grants);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
 }
 @BeforeEach void setup() { login("ADMIN",null,Set.of());jdbc.update("delete from community_notice_notifications");jdbc.update("delete from community_notice_outbox");jdbc.update("delete from community_notice_flats");jdbc.update("delete from community_notices");jdbc.update("delete from in_app_notifications");jdbc.update("delete from lost_found_claims");jdbc.update("delete from lost_found_event_outbox");jdbc.update("delete from lost_found_reports");jdbc.update("delete from family_members");jdbc.update("delete from flat_memberships");jdbc.update("delete from idempotency_records");jdbc.update("delete from admin_users");jdbc.update("delete from members");jdbc.update("delete from audit_events");jdbc.update("insert into admin_users(id,username,password_hash,role,enabled,created_at,updated_at) values (?,'test-admin','test-only','ADMIN',true,current_timestamp,current_timestamp)",actor);jdbc.update("insert into account_roles values (?,'ADMIN')",actor); }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private com.emerald.member.dto.MemberResponse create(String suffix) { return service.create(new com.emerald.member.dto.CreateMemberRequest("Asha "+suffix,"9"+String.format("%09d",testMobileSequence++),null)); }
 private UUID id(com.emerald.member.dto.MemberResponse response) { return response.memberId(); }
 @Test void migrationsSearchAndCursorWorkAcrossPagesAndResidentScope() {
  var first=create("101");var second=create("102");var third=create("103");
  var page=service.searchPage(0,1,"createdAt","asc","asha","ACTIVE");assertEquals(3,page.totalElements());
  var one=service.cursor(1,null,null,null);assertTrue(one.hasNext());assertEquals(1,one.items().size());
  var two=service.cursor(1,one.nextCursor(),null,null);assertNotEquals(id(one.items().getFirst()),id(two.items().getFirst()));
  assertThrows(ApiFailure.class,()->service.cursor(1,one.nextCursor(),"different",null));
  login("RESIDENT",id(second),Set.of(id(second)));
  var own=service.searchPage(0,20,"createdAt","asc",null,null);assertEquals(1,own.totalElements());assertEquals(id(second),id(own.content().getFirst()));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.get(id(first)));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->create("104"));
  login("COMMITTEE",null,Set.of());assertEquals(3,service.searchPage(0,20,"createdAt","asc",null,null).totalElements());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->create("105"));
 }
 @Test void auditIsAtomicAndConditionalUpdateRejectsOldVersion() {
  var created=create("201");var id=id(created);
  assertEquals(1,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,id));
  var tag=VersionPreconditions.tag("member",id,created.version());
  var updated=service.update(id,new com.emerald.member.dto.UpdateMemberRequest("Changed","9876543201",null),tag);
  assertTrue(updated.version()>created.version());
  assertEquals(412,assertThrows(ApiFailure.class,()->service.update(id,new com.emerald.member.dto.UpdateMemberRequest("Stale","9876543201",null),tag)).status());
  assertEquals(2,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,id));
  var tx=new TransactionTemplate(manager);final UUID[] rolledBack={null};
  assertThrows(IllegalStateException.class,()->tx.execute(status->{rolledBack[0]=id(create("202"));throw new IllegalStateException("rollback requested");}));
  assertEquals(0,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,rolledBack[0]));
  assertEquals(0,jdbc.queryForObject("select count(*) from members where id=?",Integer.class,rolledBack[0]));
 }
 @Test void concurrentDatabaseWritersCannotSilentlyOverwriteEachOther() {
  var created=create("301");var id=id(created);var a=emf.createEntityManager();var b=emf.createEntityManager();
  try {
   a.getTransaction().begin();b.getTransaction().begin();
   var first=a.find(com.emerald.member.entity.MemberEntity.class,id);var second=b.find(com.emerald.member.entity.MemberEntity.class,id);
   first.update("First","9876543301",null,Instant.now());a.getTransaction().commit();
   second.update("Second","9876543301",null,Instant.now());
   assertThrows(jakarta.persistence.OptimisticLockException.class,b::flush);
  } finally { if(a.getTransaction().isActive())a.getTransaction().rollback();if(b.getTransaction().isActive())b.getTransaction().rollback();a.close();b.close(); }
 }
 @Test void accountRolesAndResidentAccessAreRefreshedAndAudited() {
  var member=create("401");
  var account=accounts.create(new com.emerald.member.auth.AccountController.Create(
   "resident-test","resident-test-password-2026","RESIDENT","EMERALD",member.memberId()));
  var flat=UUID.randomUUID();
  jdbc.update("insert into flat_memberships(id,member_id,flat_id,relationship_type,start_date,created_at,updated_at) values(?,?,?,'RESIDENT',current_date,current_timestamp,current_timestamp)",UUID.randomUUID(),member.memberId(),flat);
  assertEquals(Set.of(flat),identities.current(account.accountId()).flatIds());
  jdbc.update("update flat_memberships set end_date=current_date where member_id=?",member.memberId());
  assertTrue(identities.current(account.accountId()).flatIds().isEmpty());
  var changed=accounts.permissions(account.accountId(),new com.emerald.member.auth.AccountController.Permissions(
   "COMMITTEE","EMERALD",null,true,account.version()));
  assertEquals("COMMITTEE",identities.current(account.accountId()).role());
  accounts.permissions(account.accountId(),new com.emerald.member.auth.AccountController.Permissions(
   "COMMITTEE","EMERALD",null,false,changed.version()));
  assertEquals(401,assertThrows(ApiFailure.class,()->identities.current(account.accountId())).status());
  var events=audit.list("ACCOUNT",account.accountId(),1,0);
  assertEquals(1,events.size());
  assertFalse(events.getFirst().get("after_state").toString().contains("password"));
  long sequence=((Number)events.getFirst().get("sequence")).longValue();
  assertEquals(2,audit.list("ACCOUNT",account.accountId(),10,sequence).size());
 }
 @Test void lostFoundReportsAndNotificationsAreVisibleOnlyToCurrentFlatMembers() {
  UUID home=UUID.randomUUID(), otherFlat=UUID.randomUUID(), claimant=UUID.randomUUID();
  var request=new com.emerald.member.lostfound.LostFoundDtos.CreateReport("FOUND",home,"KEYS",null,"Near the lobby",Instant.now(),"Three keys", "SOCIETY_OFFICE",null);
  var report=lostFound.create(request,actor,Set.of(),true);
  assertEquals(home,report.flatId());
  lostFound.changeStatus(report.id(),"UNDER_REVIEW",actor);
  lostFound.changeStatus(report.id(),"PUBLISHED",actor);
  assertEquals(1,lostFound.board(Set.of(home),false).size());
  assertTrue(lostFound.board(Set.of(otherFlat),false).isEmpty());
  assertEquals(1,lostFound.board(Set.of(),true).size());
  var homeMember=create("LF_HOME");var otherMember=create("LF_OTHER");
  UUID homeAccount=UUID.randomUUID(),otherAccount=UUID.randomUUID();
  jdbc.update("insert into admin_users(id,username,password_hash,role,member_id,enabled,created_at,updated_at) values (?,'lf-home','test-only','RESIDENT',?,true,current_timestamp,current_timestamp)",homeAccount,homeMember.memberId());
  jdbc.update("insert into admin_users(id,username,password_hash,role,member_id,enabled,created_at,updated_at) values (?,'lf-other','test-only','RESIDENT',?,true,current_timestamp,current_timestamp)",otherAccount,otherMember.memberId());
  jdbc.update("insert into account_roles values (?,'RESIDENT')",homeAccount);jdbc.update("insert into account_roles values (?,'RESIDENT')",otherAccount);
  jdbc.update("insert into flat_memberships(id,member_id,flat_id,relationship_type,start_date,created_at,updated_at) values (?,?,?,'RESIDENT',current_date,current_timestamp,current_timestamp)",UUID.randomUUID(),homeMember.memberId(),home);
  jdbc.update("insert into flat_memberships(id,member_id,flat_id,relationship_type,start_date,created_at,updated_at) values (?,?,?,'RESIDENT',current_date,current_timestamp,current_timestamp)",UUID.randomUUID(),otherMember.memberId(),otherFlat);
  var event=com.emerald.member.lostfound.LostFoundEvent.create(report.id(),"LostFoundReportPublished",actor);
  lostFoundListener.onEvent(json.writeValueAsString(event));
  assertEquals(1,lostFound.notifications(homeAccount,Set.of(home),false).size());
  assertTrue(lostFound.notifications(otherAccount,Set.of(otherFlat),false).isEmpty());
  jdbc.update("insert into admin_users(id,username,password_hash,role,enabled,created_at,updated_at) values (?,'flat-claimant','test-only','ADMIN',true,current_timestamp,current_timestamp)",claimant);
  var denied=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->lostFound.claim(report.id(),"Blue keychain",claimant,Set.of(otherFlat)));
  assertEquals(404,denied.getStatusCode().value());
  var claim=lostFound.claim(report.id(),"Blue keychain",claimant,Set.of(home));
  UUID notificationId=UUID.randomUUID();
  jdbc.update("insert into in_app_notifications(event_id,recipient_id,report_id,event_type) values (?,?,?,'LostFoundClaimSubmitted')",notificationId,claimant,report.id());
  assertEquals(1,lostFound.notifications(claimant,Set.of(home),false).size());
  assertTrue(lostFound.notifications(claimant,Set.of(otherFlat),false).isEmpty());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->lostFound.markNotificationRead(notificationId,claimant,Set.of(otherFlat),false));
  lostFound.markNotificationRead(notificationId,claimant,Set.of(home),false);
  assertNotNull(jdbc.queryForObject("select read_at from in_app_notifications where event_id=?",java.sql.Timestamp.class,notificationId));
 }
 @Test void administratorCanAssignAnExistingUnscopedNoticeToAFlat() {
  UUID reportId=UUID.randomUUID(),flat=UUID.randomUUID();
  jdbc.update("insert into lost_found_reports(id,reporter_id,report_type,item_type,location,occurred_at,description,contact_preference,status) values (?,?,'FOUND','KEYS','Legacy location',current_timestamp,'Legacy report','SOCIETY_OFFICE','PUBLISHED')",reportId,actor);
  var assigned=lostFound.assignFlat(reportId,flat,actor);
  assertEquals(flat,assigned.flatId());
  assertEquals(1,lostFound.board(Set.of(flat),false).size());
 }
 @Test void communityNoticeAudienceIsReviewedBeforeFlatOrSocietyPublication() {
  UUID home=UUID.randomUUID(),target=UUID.randomUUID(),unrelated=UUID.randomUUID();
  var created=communityNotices.create(new com.emerald.member.notices.NoticeDtos.CreateNotice(
          "Water outage","Water will be off from 2 PM","FLATS",List.of(target)),actor,false,Set.of());
  assertEquals("SUBMITTED",created.status());
  assertTrue(communityNotices.board(Set.of(target),false).isEmpty());
  assertEquals(1,communityNotices.reviewQueue().size());
  communityNotices.changeStatus(created.id(),"UNDER_REVIEW",actor);
  communityNotices.changeAudience(created.id(),new com.emerald.member.notices.NoticeDtos.AudienceChange("FLATS",List.of(home,target)));
  communityNotices.changeStatus(created.id(),"PUBLISHED",actor);
  assertEquals(1,communityNotices.board(Set.of(home),false).size());
  assertEquals(1,communityNotices.board(Set.of(target),false).size());
  assertTrue(communityNotices.board(Set.of(unrelated),false).isEmpty());
  assertEquals(1,communityNotices.board(Set.of(),true).size());
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->communityNotices.create(
          new com.emerald.member.notices.NoticeDtos.CreateNotice("Invalid","Resident society notice","SOCIETY",List.of()),actor,false,Set.of()));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->communityNotices.create(
          new com.emerald.member.notices.NoticeDtos.CreateNotice("Own flat","Own flat target","FLATS",List.of(home)),actor,false,Set.of(home)));

  var homeMember=create("NOTICE_HOME");var targetMember=create("NOTICE_TARGET");var outsideMember=create("NOTICE_OUTSIDE");
  UUID homeAccount=UUID.randomUUID(),targetAccount=UUID.randomUUID(),outsideAccount=UUID.randomUUID();
  addResident(homeAccount,"notice-home",homeMember.memberId(),home);
  addResident(targetAccount,"notice-target",targetMember.memberId(),target);
  addResident(outsideAccount,"notice-outside",outsideMember.memberId(),unrelated);
  var event=com.emerald.member.notices.CommunityNoticeEvent.create(created.id(),"CommunityNoticePublished",actor);
  communityNoticeListener.onEvent(json.writeValueAsString(event));
  assertEquals(1,communityNotices.notifications(homeAccount,Set.of(home),false).size());
  assertEquals(1,communityNotices.notifications(targetAccount,Set.of(target),false).size());
  assertTrue(communityNotices.notifications(outsideAccount,Set.of(unrelated),false).isEmpty());
 }
 @Test void societyNoticeIsVisibleAcrossFlatsAndAdminCanRetargetBeforePublishing() {
  UUID home=UUID.randomUUID(),other=UUID.randomUUID();
  var created=communityNotices.create(new com.emerald.member.notices.NoticeDtos.CreateNotice(
          "Society meeting","Meeting starts at 6 PM","SOCIETY",List.of()),actor,true,Set.of());
  communityNotices.changeStatus(created.id(),"UNDER_REVIEW",actor);
  communityNotices.changeStatus(created.id(),"PUBLISHED",actor);
  assertEquals(1,communityNotices.board(Set.of(home),false).size());
  assertEquals(1,communityNotices.board(Set.of(other),false).size());
 }
 private void addResident(UUID account,String username,UUID member,UUID flat) {
  jdbc.update("insert into admin_users(id,username,password_hash,role,member_id,enabled,created_at,updated_at) values (?,?, 'test-only','RESIDENT',?,true,current_timestamp,current_timestamp)",account,username,member);
  jdbc.update("insert into account_roles values (?,'RESIDENT')",account);
  jdbc.update("insert into flat_memberships(id,member_id,flat_id,relationship_type,start_date,created_at,updated_at) values (?,?,?,'RESIDENT',current_date,current_timestamp,current_timestamp)",UUID.randomUUID(),member,flat);
 }
}
