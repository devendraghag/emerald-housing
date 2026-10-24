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
@SpringBootTest(properties={"society.cache.warmup.enabled=false","TRACING_OTLP_ENABLED=false","management.otlp.metrics.export.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
class FeaturesPersistenceTest {
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
 private final UUID actor=UUID.randomUUID();
 private void login(String role,UUID member,Set<UUID> flats) {
  var grants=new HashSet<>(jdbc.queryForList("select permission_code || ':' || scope from role_permissions where role_code=?",String.class,role));
  var identity=new SessionIdentityResponse(actor,"test-admin",role,"EMERALD",member,flats,Set.of(role),grants);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
 }
 @BeforeEach void setup() { login("ADMIN",null,Set.of());jdbc.update("delete from family_members");jdbc.update("delete from flat_memberships");jdbc.update("delete from idempotency_records");jdbc.update("delete from admin_users");jdbc.update("delete from members");jdbc.update("delete from audit_events");jdbc.update("insert into admin_users(id,username,password_hash,role,enabled,created_at,updated_at) values (?,'test-admin','test-only','ADMIN',true,current_timestamp,current_timestamp)",actor);jdbc.update("insert into account_roles values (?,'ADMIN')",actor); }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private com.emerald.member.dto.MemberResponse create(String suffix) { return service.create(new com.emerald.member.dto.CreateMemberRequest("Asha "+suffix,"9"+String.format("%09d",Math.abs(suffix.hashCode())),null)); }
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
}
