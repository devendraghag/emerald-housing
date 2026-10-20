package com.emerald.society;
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
import com.emerald.society.security.SessionIdentityResponse;
import com.emerald.society.service.FlatService;
import com.emerald.society.api.*;
@SpringBootTest(properties={"society.cache.warmup.enabled=false","TRACING_OTLP_ENABLED=false","management.otlp.metrics.export.enabled=false"})
@Testcontainers(disabledWithoutDocker=true)
class FeaturesPersistenceTest {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine");
 @DynamicPropertySource static void database(DynamicPropertyRegistry p) { p.add("app.datasource.writer.jdbc-url",POSTGRES::getJdbcUrl);p.add("app.datasource.writer.username",POSTGRES::getUsername);p.add("app.datasource.writer.password",POSTGRES::getPassword); }
 @Autowired FlatService service; @Autowired JdbcTemplate jdbc; @Autowired PlatformTransactionManager manager;
 @Autowired jakarta.persistence.EntityManagerFactory emf;
 private final UUID actor=UUID.randomUUID();
 private void login(String role,UUID member,Set<UUID> flats) {
  var identity=new SessionIdentityResponse(actor,"test-admin",role,"EMERALD",member,flats);
  SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(identity,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
 }
 @BeforeEach void setup() { login("ADMIN",null,Set.of()); jdbc.update("delete from flats");jdbc.update("delete from audit_events"); }
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 private com.emerald.society.dto.FlatResponse create(String suffix) { return service.create(new com.emerald.society.dto.CreateFlatRequest(1,"A-"+suffix)); }
 private UUID id(com.emerald.society.dto.FlatResponse response) { return response.flatId(); }
 @Test void migrationsSearchAndCursorWorkAcrossPagesAndResidentScope() {
  var first=create("101");var second=create("102");var third=create("103");
  var page=service.searchPage(0,1,"createdAt","asc","A-","ACTIVE",1);assertEquals(3,page.totalElements());
  var one=service.cursor(1,null,null,null,null);assertTrue(one.hasNext());assertEquals(1,one.items().size());
  var two=service.cursor(1,one.nextCursor(),null,null,null);assertNotEquals(id(one.items().getFirst()),id(two.items().getFirst()));
  assertThrows(ApiFailure.class,()->service.cursor(1,one.nextCursor(),"different",null,null));
  login("RESIDENT",id(second),Set.of(id(second)));
  var own=service.searchPage(0,20,"createdAt","asc",null,null,null);assertEquals(1,own.totalElements());assertEquals(id(second),id(own.content().getFirst()));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.getCached(id(first)));
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->create("104"));
  login("COMMITTEE",null,Set.of());assertEquals(3,service.searchPage(0,20,"createdAt","asc",null,null,null).totalElements());
  assertThrows(org.springframework.security.access.AccessDeniedException.class,()->create("105"));
 }
 @Test void auditIsAtomicAndConditionalUpdateRejectsOldVersion() {
  var created=create("201");var id=id(created);
  assertEquals(1,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,id));
  var tag=VersionPreconditions.tag("flat",id,created.version());
  var updated=service.update(id,new com.emerald.society.dto.UpdateFlatRequest(2,"B-201"),tag);
  assertTrue(updated.version()>created.version());
  assertEquals(412,assertThrows(ApiFailure.class,()->service.update(id,new com.emerald.society.dto.UpdateFlatRequest(2,"B-stale"),tag)).status());
  assertEquals(2,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,id));
  var tx=new TransactionTemplate(manager);final UUID[] rolledBack={null};
  assertThrows(IllegalStateException.class,()->tx.execute(status->{rolledBack[0]=id(create("202"));throw new IllegalStateException("rollback requested");}));
  assertEquals(0,jdbc.queryForObject("select count(*) from audit_events where entity_id=?",Integer.class,rolledBack[0]));
  assertEquals(0,jdbc.queryForObject("select count(*) from flats where id=?",Integer.class,rolledBack[0]));
 }
 @Test void concurrentDatabaseWritersCannotSilentlyOverwriteEachOther() {
  var created=create("301");var id=id(created);var a=emf.createEntityManager();var b=emf.createEntityManager();
  try {
   a.getTransaction().begin();b.getTransaction().begin();
   var first=a.find(com.emerald.society.entity.FlatEntity.class,id);var second=b.find(com.emerald.society.entity.FlatEntity.class,id);
   first.update(first.getFloorId(),"A-first",Instant.now());a.getTransaction().commit();
   second.update(second.getFloorId(),"A-second",Instant.now());
   assertThrows(jakarta.persistence.OptimisticLockException.class,b::flush);
  } finally { if(a.getTransaction().isActive())a.getTransaction().rollback();if(b.getTransaction().isActive())b.getTransaction().rollback();a.close();b.close(); }
 }
}
