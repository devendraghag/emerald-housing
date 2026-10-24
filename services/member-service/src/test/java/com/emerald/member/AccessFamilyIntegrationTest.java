package com.emerald.member;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.emerald.member.api.ApiFailure;
import com.emerald.member.auth.*;
import com.emerald.member.controller.OwnerController;
import com.emerald.member.dto.*;
import com.emerald.member.service.*;
import com.emerald.member.security.SessionIdentityResponse;
import java.util.*;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Run scripts/test-access-local.ps1 for a disposable PostgreSQL cluster. No application database is used. */
@SpringBootTest(properties={"TRACING_OTLP_ENABLED=false","management.otlp.metrics.export.enabled=false"})
@EnabledIfSystemProperty(named="access.test.jdbc-url",matches=".+")
class AccessFamilyIntegrationTest {
 static final String SCHEMA="access_test_"+UUID.randomUUID().toString().replace("-","");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p)throws Exception{
  String url=System.getProperty("access.test.jdbc-url");
  try(var c=java.sql.DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){s.execute("CREATE SCHEMA "+SCHEMA);}
  p.add("app.datasource.writer.jdbc-url",()->url+(url.contains("?")?"&":"?")+"currentSchema="+SCHEMA);
  p.add("app.datasource.writer.username",()->"postgres");p.add("app.datasource.writer.password",()->"");
 }
 @Autowired JdbcTemplate db;@Autowired AccessRegistry registry;@Autowired AccountController accounts;
 @Autowired AccessController roles;@Autowired AccountIdentityService identities;@Autowired MemberService members;
 @Autowired FamilyController families;@Autowired OwnerController owners;@Autowired MembershipService memberships;
 @MockitoBean com.emerald.member.client.SocietyClient society;
 @MockitoBean com.emerald.member.lock.RedisMembershipLock locks;
 UUID admin,owner,other,ownerMembership,otherMembership,flat,ownerAccount;
 void login(UUID id){var a=identities.current(id);var actor=new SessionIdentityResponse(a.adminId(),a.username(),a.role(),a.societyCode(),a.memberId(),a.flatIds(),a.roles(),a.permissions());SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));}
 UUID person(String name,String mobile){return members.create(new CreateMemberRequest(name,mobile,null)).memberId();}
 UUID ownership(UUID member,UUID flat){UUID id=UUID.randomUUID();db.update("INSERT INTO flat_memberships(id,member_id,flat_id,relationship_type,start_date,created_at,updated_at) VALUES (?,?,?,'OWNER',CURRENT_DATE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,member,flat);return id;}
 FamilyController.Details details(String name){return new FamilyController.Details(name,"Child",null,null,null);}
 @SuppressWarnings("unchecked") Map<String,Object> add(){return (Map<String,Object>)families.create(new FamilyController.Create(ownerMembership,details("Family One")));}
 @BeforeEach void setup(){
  db.execute("TRUNCATE family_members,flat_memberships,idempotency_records,account_roles,admin_users,members,audit_events CASCADE");
  db.update("DELETE FROM role_permissions WHERE role_code='CUSTOM_TEST'");db.update("DELETE FROM access_roles WHERE code='CUSTOM_TEST'");
  admin=UUID.randomUUID();db.update("INSERT INTO admin_users(id,username,password_hash,role,enabled,created_at,updated_at) VALUES (?,'administrator','not-used','ADMIN',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",admin);
  db.update("INSERT INTO account_roles VALUES (?,'ADMIN')",admin);login(admin);
  owner=person("Owner One","9876543201");other=person("Owner Two","9876543202");flat=UUID.randomUUID();ownerMembership=ownership(owner,flat);otherMembership=ownership(other,UUID.randomUUID());
  ownerAccount=accounts.create(new AccountController.Create("owner-one","owner-one-password-2026","FLAT_OWNER","EMERALD",owner)).accountId();
  when(locks.withMemberLock(any(),any())).thenAnswer(invocation->((Supplier<?>)invocation.getArgument(1)).get());
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 @Test void ownerCanManageOnlyTheirOwnFamilyAndStaleWritesFail(){
  login(ownerAccount);var record=add();UUID id=(UUID)record.get("id");
  assertEquals(1,((List<?>)families.list(null,0,20)).size());
  assertThrows(AccessDeniedException.class,()->families.create(new FamilyController.Create(otherMembership,details("Forbidden"))));
  families.update(id,new FamilyController.Update(0L,details("Updated")));
  assertEquals(409,assertThrows(ApiFailure.class,()->families.update(id,new FamilyController.Update(0L,details("Stale")))).status());
  families.deactivate(id,new FamilyController.Version(1L));
  assertFalse(db.queryForObject("SELECT active FROM family_members WHERE id=?",Boolean.class,id));
  assertEquals(3,db.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id=?",Integer.class,id));
 }
 @Test void endingOwnershipRevokesFamilyAccessAndRetainsHistory(){
  login(ownerAccount);var record=add();login(admin);memberships.end(owner,ownerMembership,new EndMembershipRequest(LocalDate.now()));login(ownerAccount);
  assertTrue(identities.current(ownerAccount).flatIds().isEmpty());assertTrue(((List<?>)families.list(null,0,20)).isEmpty());
  assertThrows(AccessDeniedException.class,()->families.update((UUID)record.get("id"),new FamilyController.Update(0L,details("Forbidden"))));
  login(admin);assertEquals(1,((List<?>)families.list(null,0,20)).size());
 }
 @Test void ownershipRoleDoesNotAllowRegisteringOwnersOrAssigningRoles(){
  login(ownerAccount);
  assertThrows(AccessDeniedException.class,()->owners.register(new OwnerController.Register(new CreateMemberRequest("Forbidden","9876543299",null),flat,LocalDate.now(),"forbidden","forbidden-password-2026")));
  assertThrows(AccessDeniedException.class,()->roles.create(new AccessController.Role("CUSTOM_TEST","Forbidden",true,0L,List.of())));
 }
 @Test void customRolesAndMultipleRolesTakeEffectWithoutRelogin(){
  roles.create(new AccessController.Role("CUSTOM_TEST","Family reader",true,0L,List.of(new AccessController.Grant("FAMILY_VIEW","SOCIETY"))));
  var a=accounts.get(ownerAccount);accounts.permissions(ownerAccount,new AccountController.Permissions(null,"EMERALD",owner,true,a.version(),Set.of("FLAT_OWNER","CUSTOM_TEST")));
  assertTrue(identities.current(ownerAccount).permissions().contains("FAMILY_VIEW:SOCIETY"));assertEquals(2,identities.current(ownerAccount).roles().size());
  roles.update("CUSTOM_TEST",new AccessController.Role("CUSTOM_TEST","Disabled",false,0L,List.of()));
  assertFalse(identities.current(ownerAccount).permissions().contains("FAMILY_VIEW:SOCIETY"));assertTrue(identities.current(ownerAccount).roles().contains("FLAT_OWNER"));
  assertEquals(409,assertThrows(ApiFailure.class,()->roles.update("CUSTOM_TEST",new AccessController.Role("CUSTOM_TEST","Stale",true,0L,List.of()))).status());
 }
 @Test void protectedAdministratorsAndSelfChangesAreRejected(){
  var a=accounts.get(admin);assertEquals(409,assertThrows(ApiFailure.class,()->accounts.permissions(admin,new AccountController.Permissions("COMMITTEE","EMERALD",null,true,a.version()))).status());
  assertEquals(409,assertThrows(ApiFailure.class,()->roles.update("ADMIN",new AccessController.Role("ADMIN","Changed",false,0L,List.of()))).status());
  var delegated=accounts.create(new AccountController.Create("delegate","delegate-password-2026","COMMITTEE","EMERALD",null));
  db.update("INSERT INTO role_permissions VALUES ('COMMITTEE','USER_UPDATE','SOCIETY'),('COMMITTEE','USER_ASSIGN_ROLE','SOCIETY') ON CONFLICT DO NOTHING");
  try{login(delegated.accountId());assertEquals("LAST_ADMINISTRATOR",assertThrows(ApiFailure.class,()->accounts.permissions(admin,new AccountController.Permissions("COMMITTEE","EMERALD",null,false,a.version()))).code());assertTrue(identities.current(admin).roles().contains("ADMIN"));}
  finally{db.update("DELETE FROM role_permissions WHERE role_code='COMMITTEE' AND permission_code IN ('USER_UPDATE','USER_ASSIGN_ROLE')");}
 }
 @Test void linkedFamilyLoginSeesOnlyTheirOwnRecordAndLosesFlatAccessOnDeactivation(){
  UUID member=person("Linked Child","9876543203");var account=accounts.create(new AccountController.Create("child","child-password-2026","FAMILY_MEMBER","EMERALD",member));
  var record=(Map<?,?>)families.create(new FamilyController.Create(ownerMembership,new FamilyController.Details("Linked Child","Child",null,null,member)));
  families.create(new FamilyController.Create(ownerMembership,details("Other Child")));
  login(account.accountId());assertEquals(Set.of(flat),identities.current(account.accountId()).flatIds());assertEquals(1,((List<?>)families.list(null,0,20)).size());
  assertThrows(AccessDeniedException.class,()->families.create(new FamilyController.Create(ownerMembership,details("Forbidden"))));
  login(admin);families.deactivate((UUID)record.get("id"),new FamilyController.Version(0L));login(account.accountId());assertTrue(identities.current(account.accountId()).flatIds().isEmpty());assertTrue(((List<?>)families.list(null,0,20)).isEmpty());
 }
 @Test void ownersCannotAttachAnArbitraryPersonIdentity(){login(ownerAccount);assertThrows(AccessDeniedException.class,()->families.create(new FamilyController.Create(ownerMembership,new FamilyController.Details("Spoof","Child",null,null,other))));}
 @Test void futureOwnershipDoesNotGrantFamilyOrFlatAccess(){
  login(ownerAccount);add();db.update("UPDATE flat_memberships SET start_date=? WHERE id=?",LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1),ownerMembership);
  assertTrue(identities.current(ownerAccount).flatIds().isEmpty());assertTrue(((List<?>)families.list(null,0,20)).isEmpty());
  assertThrows(AccessDeniedException.class,this::add);
 }
 @Test void linkedPersonDeactivationCannotLockOutTheLastAdministrator(){
  db.update("UPDATE admin_users SET member_id=? WHERE id=?",owner,admin);
  assertEquals("LAST_ADMINISTRATOR",assertThrows(ApiFailure.class,()->members.deactivate(owner)).code());
  assertEquals("ACTIVE",db.queryForObject("SELECT status FROM members WHERE id=?",String.class,owner));
 }
 @Test void roleScopeValidationRollsBackInvalidDefinitions(){
  assertEquals("UNSUPPORTED_SCOPE",assertThrows(ApiFailure.class,()->roles.create(new AccessController.Role("CUSTOM_TEST","Invalid",true,0L,List.of(new AccessController.Grant("USER_CREATE","SELF"))))).code());
  assertEquals(0,db.queryForObject("SELECT count(*) FROM access_roles WHERE code='CUSTOM_TEST'",Integer.class));
 }
 @Test void customSelfScopedRolesRequirePersonLinksWithoutHardcodedRoleNames(){
  roles.create(new AccessController.Role("CUSTOM_TEST","Personal directory",true,0L,List.of(new AccessController.Grant("MEMBER_VIEW","SELF"))));
  assertEquals("INVALID_MEMBER_LINK",assertThrows(ApiFailure.class,()->accounts.create(new AccountController.Create("unlinked-custom","unlinked-custom-password-2026","CUSTOM_TEST","EMERALD",null))).code());
  var linked=accounts.create(new AccountController.Create("linked-custom","linked-custom-password-2026","CUSTOM_TEST","EMERALD",owner));
  assertTrue(identities.current(linked.accountId()).permissions().contains("MEMBER_VIEW:SELF"));
 }
 @Test void allDisabledRolesRejectAuthenticationButKeepAssignmentsVisible(){
  roles.create(new AccessController.Role("CUSTOM_TEST","Temporary",true,0L,List.of()));
  var account=accounts.create(new AccountController.Create("custom-only","custom-only-password-2026","CUSTOM_TEST","EMERALD",null));
  roles.update("CUSTOM_TEST",new AccessController.Role("CUSTOM_TEST","Disabled",false,0L,List.of()));
  assertEquals("NO_ACTIVE_ROLE",assertThrows(ApiFailure.class,()->identities.current(account.accountId())).code());
  assertEquals(Set.of("CUSTOM_TEST"),accounts.get(account.accountId()).roles());
 }
 @Test void failedTransferPreservesThePreviousOwnership(){
  members.deactivate(other);
  assertThrows(com.emerald.member.exception.InactiveMemberException.class,()->owners.transfer(new OwnerController.Transfer(ownerMembership,owner,other,flat,LocalDate.now())));
  assertNull(db.queryForObject("SELECT end_date FROM flat_memberships WHERE id=?",java.sql.Date.class,ownerMembership));
 }
 @Test void upgradePreservesExistingAccountsAndBackfillsRoleAssignments()throws Exception{
  String schema=SCHEMA+"_upgrade",url=System.getProperty("access.test.jdbc-url");
  org.flywaydb.core.Flyway.configure().dataSource(url,"postgres","").schemas(schema).defaultSchema(schema).target("5").load().migrate();
  String scoped=url+(url.contains("?")?"&":"?")+"currentSchema="+schema;
  UUID existing=UUID.randomUUID();
  try(var c=java.sql.DriverManager.getConnection(scoped,"postgres","");var s=c.prepareStatement("INSERT INTO admin_users(id,username,password_hash,role,enabled,created_at,updated_at) VALUES (?,'existing-admin','preserved-password-hash','ADMIN',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")){s.setObject(1,existing);s.executeUpdate();}
  org.flywaydb.core.Flyway.configure().dataSource(url,"postgres","").schemas(schema).defaultSchema(schema).load().migrate();
  try(var c=java.sql.DriverManager.getConnection(scoped,"postgres","");var s=c.createStatement();var result=s.executeQuery("SELECT a.password_hash,ar.role_code FROM admin_users a JOIN account_roles ar ON ar.account_id=a.id WHERE a.username='existing-admin'")){
   assertTrue(result.next());assertEquals("preserved-password-hash",result.getString(1));assertEquals("ADMIN",result.getString(2));assertFalse(result.next());
  }
 }
 @Test void auditFailureRollsBackFamilyWrite(){
  db.execute("ALTER TABLE audit_events ADD CONSTRAINT reject_family_test CHECK(entity_type<>'FAMILY')");
  try{login(ownerAccount);assertThrows(org.springframework.dao.DataIntegrityViolationException.class,this::add);assertEquals(0,db.queryForObject("SELECT count(*) FROM family_members",Integer.class));}
  finally{db.execute("ALTER TABLE audit_events DROP CONSTRAINT reject_family_test");}
 }
 @Test void registrationAndTransferAreAtomic(){
  when(society.getFlat(any())).thenReturn(new SocietyFlatResponse(flat,"A-101",UUID.randomUUID(),1,"ACTIVE"));
  var result=(Map<?,?>)owners.register(new OwnerController.Register(new CreateMemberRequest("New Owner","9876543204",null),flat,LocalDate.now(),"new-owner","new-owner-password-2026"));
  assertNotNull(result.get("account"));var newOwner=(MemberResponse)result.get("person");
  owners.transfer(new OwnerController.Transfer(ownerMembership,owner,other,flat,LocalDate.now()));
  assertTrue(identities.current(ownerAccount).flatIds().isEmpty());
  int before=db.queryForObject("SELECT count(*) FROM members",Integer.class);
  assertThrows(ApiFailure.class,()->owners.register(new OwnerController.Register(new CreateMemberRequest("Rollback Owner","9876543205",null),flat,LocalDate.now(),"new-owner","new-owner-password-2026")));
  assertEquals(before,db.queryForObject("SELECT count(*) FROM members",Integer.class));
 }
}
