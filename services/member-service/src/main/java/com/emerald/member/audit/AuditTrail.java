package com.emerald.member.audit;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
@Service
public class AuditTrail {
 private final JdbcTemplate jdbc; private final JsonMapper json;
 public AuditTrail(JdbcTemplate jdbc,JsonMapper json) { this.jdbc=jdbc; this.json=json; }
 public void record(String action,String type,UUID id,Object before,Object after) {
  if(!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Audit writes require the business transaction");
  var auth=SecurityContextHolder.getContext().getAuthentication();
  var identity=auth != null && auth.getPrincipal() instanceof com.emerald.member.security.SessionIdentityResponse actor ? actor : null;
  jdbc.update("insert into audit_events(id,actor_id,actor_name,action,entity_type,entity_id,before_state,after_state,occurred_at,correlation_id) values(?,?,?,?,?,?,?,?,?,?)",
   UUID.randomUUID(),identity==null?null:identity.adminId(),identity==null?"system":identity.username(),action,type,id,
   before==null?null:json.writeValueAsString(before),after==null?null:json.writeValueAsString(after),java.sql.Timestamp.from(Instant.now()),MDC.get("correlationId"));
 }
 @Transactional(readOnly=true)
 public List<Map<String,Object>> list(String type,UUID id,int limit,long before) {
  if(limit<1 || limit>100 || before<0) throw new com.emerald.member.api.ApiFailure(400,"INVALID_PAGE","limit must be 1-100 and before must be nonnegative");
  // Monotonic sequence provides a stable audit timeline without an offset scan.
  return jdbc.queryForList("select sequence,id,actor_id,actor_name,action,entity_type,entity_id,before_state,after_state,occurred_at,correlation_id from audit_events where (cast(? as varchar) is null or entity_type=?) and (cast(? as uuid) is null or entity_id=?) and (?=0 or sequence<?) order by sequence desc limit ?",type,type,id,id,before,before,limit);
 }
}
