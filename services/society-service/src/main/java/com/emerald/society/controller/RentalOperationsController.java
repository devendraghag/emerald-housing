package com.emerald.society.controller;

import com.emerald.society.audit.AuditTrail;
import com.emerald.society.security.SocietyAccess;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

/** Rental workflows share flat-scoped records while retaining independent permission checks. */
@RestController
@RequestMapping("/api/v1/rental")
public class RentalOperationsController {
    private static final Map<String,String> VIEW = Map.of("payments","RENT_VIEW", "maintenance","MAINTENANCE_VIEW", "leases","LEASE_VIEW", "viewings","VIEWING_VIEW");
    private static final Map<String,String> CREATE = Map.of("payments","RENT_MANAGE", "maintenance","MAINTENANCE_CREATE", "leases","LEASE_MANAGE", "viewings","VIEWING_MANAGE");
    private static final Map<String,String> MANAGE = Map.of("payments","RENT_MANAGE", "maintenance","MAINTENANCE_MANAGE", "leases","LEASE_MANAGE", "viewings","VIEWING_MANAGE");
    private static final Map<String,String> TYPES = Map.of("payments","PAYMENT", "maintenance","MAINTENANCE", "leases","LEASE", "viewings","VIEWING");
    private final JdbcTemplate db;
    private final SocietyAccess access;
    private final JsonMapper json;
    private final AuditTrail audit;
    public RentalOperationsController(JdbcTemplate db, SocietyAccess access, JsonMapper json, AuditTrail audit) { this.db=db; this.access=access; this.json=json; this.audit=audit; }

    @GetMapping("/{resource}")
    public List<Map<String,Object>> list(@PathVariable String resource, @RequestParam(required=false) UUID flatId) {
        requireResource(resource); var actor=access.identity();
        boolean society=access.has(VIEW.get(resource),"SOCIETY");
        if(!society&&(!access.has(VIEW.get(resource),"OWN_FLATS")||actor==null||actor.flatIds().isEmpty()||(flatId!=null&&!actor.flatIds().contains(flatId))))throw new AccessDeniedException("Permission denied for this flat");
        String sql="SELECT id,flat_id,member_id,CASE WHEN operation_type='PAYMENT' AND status='DUE' AND (details->>'dueDate')::date<CURRENT_DATE THEN 'OVERDUE' ELSE status END AS status,details,created_by,created_at,updated_at,version FROM rental_operations WHERE operation_type=?";
        List<Object> args=new ArrayList<>(List.of(TYPES.get(resource)));
        if (access.has(VIEW.get(resource),"SOCIETY")) { if(flatId!=null){sql+=" AND flat_id=?";args.add(flatId);} }
        else { var flats=access.ownFlats(); if(flats.isEmpty()) return List.of(); sql+=" AND flat_id IN ("+String.join(",",Collections.nCopies(flats.size(),"?"))+ ")";args.addAll(flats);if(flatId!=null){sql+=" AND flat_id=?";args.add(flatId);} }
        sql+=" ORDER BY created_at DESC LIMIT 200";
        boolean maySeeMembers=society;
        return db.query(sql,(rs,n)->row(rs.getObject("id",UUID.class),rs.getObject("flat_id",UUID.class),maySeeMembers?rs.getObject("member_id",UUID.class):null,rs.getString("status"),rs.getString("details"),rs.getObject("created_by",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getLong("version")),args.toArray());
    }

    @PostMapping("/{resource}") @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String,Object> create(@PathVariable String resource, @RequestBody Map<String,Object> body) {
        requireResource(resource); UUID flat=uuid(body.get("flatId"));
        validatePayload(resource,body);
        requirePermission(CREATE.get(resource),flat);
        UUID id=UUID.randomUUID(); var actor=access.identity(); UUID member=actor.roles().contains("TENANT")?actor.memberId():null;
        String status=switch(resource){case "payments"->"DUE";case "maintenance"->"OPEN";case "leases"->"DRAFT";default->"AVAILABLE";};
        try { db.update("INSERT INTO rental_operations(id,operation_type,flat_id,member_id,status,details,created_by) VALUES (?,?,?, ?,?,?::jsonb,?)",id,TYPES.get(resource),flat,member,status,json.writeValueAsString(body),actor.adminId()); }
        catch(org.springframework.dao.DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"A matching rent period or viewing slot already exists",e);}
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rental workflow data",e);}
        var result=Map.of("id",id,"flatId",flat,"memberId",member==null?"":member,"status",status,"details",body,"version",0);
        audit.record("CREATE_"+TYPES.get(resource),"RENTAL_OPERATION",id,null,result);
        return result;
    }

    @PatchMapping("/{resource}/{id}")
    @Transactional
    public Map<String,Object> update(@PathVariable String resource,@PathVariable UUID id,@RequestBody Map<String,Object> body){
        requireResource(resource); var current=db.query("SELECT flat_id,member_id,status,details,version FROM rental_operations WHERE id=? AND operation_type=?",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getLong(5)},id,TYPES.get(resource)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID flat=(UUID)current[0];
        long version=((Number)current[4]).longValue(); if(!body.containsKey("version")||!(body.get("version") instanceof Number supplied)||supplied.doubleValue()!=supplied.longValue()||supplied.longValue()!=version)throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,"Supply the current record version");
        Set<String> editable=switch(resource){case "payments"->Set.of("status","version","reference");case "maintenance"->Set.of("status","version","assignedTo");default->Set.of("status","version");};
        if(body.keySet().stream().anyMatch(k->!editable.contains(k)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported workflow update field");
        boolean statusChanged=body.containsKey("status")&&!Objects.equals(body.get("status"),current[2]);
        if(!statusChanged&&body.keySet().stream().noneMatch(k->!Set.of("status","version").contains(k)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"No workflow change was supplied");
        String requested=Objects.toString(body.getOrDefault("status",current[2])); String status=requested;
        boolean tenantActor=access.identity().roles().contains("TENANT");
        boolean tenantResponse="leases".equals(resource)&&Set.of("RENEWAL_ACCEPTED","RENEWAL_DECLINED","MOVE_OUT_CONFIRMED").contains(requested);
        if(tenantResponse){if(!tenantActor)throw new AccessDeniedException("Only the tenant can respond to a lease workflow");requirePermission("LEASE_VIEW",flat);}
        if(access.has(MANAGE.get(resource),"SOCIETY")||access.has(MANAGE.get(resource),"OWN_FLATS")) requirePermission(MANAGE.get(resource),flat);
        else {
            requirePermission("LEASE_VIEW",flat);
            if(!"leases".equals(resource)||!Set.of("RENEWAL_ACCEPTED","RENEWAL_DECLINED","MOVE_OUT_CONFIRMED").contains(requested)||
                    !("RENEWAL_OFFERED".equals(current[2])&&requested.startsWith("RENEWAL_"))&&!("MOVE_OUT_STARTED".equals(current[2])&&"MOVE_OUT_CONFIRMED".equals(requested)))
                throw new AccessDeniedException("Tenants may only respond to their own renewal or move-out workflow");
        }
        if(statusChanged&&!validTransition(resource,current[2].toString(),requested))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid workflow status transition");
        if(statusChanged&&"leases".equals(resource)&&"DRAFT".equals(current[2])){
            try { Map<?,?> lease=json.readValue((String)current[3],Map.class);String action=Objects.toString(lease.get("action"),"");if(("RENEWAL".equalsIgnoreCase(action)&&!"RENEWAL_OFFERED".equals(requested))||("MOVE_OUT".equalsIgnoreCase(action)&&!"MOVE_OUT_STARTED".equals(requested)))throw new ResponseStatusException(HttpStatus.CONFLICT,"Status must match the selected lease workflow"); }
            catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Stored lease workflow is invalid",e);}
        }
        Map<String,Object> details=new LinkedHashMap<>(); String encoded;
        try { details.putAll(json.readValue((String)current[3],Map.class));for(String key:editable)if(body.containsKey(key)&&!Set.of("status","version").contains(key))details.put(key,body.get(key)); encoded=json.writeValueAsString(details); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"Stored workflow data is invalid",e);}
        int changed;
        try { changed=db.update("UPDATE rental_operations SET status=?,details=?::jsonb,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?",status,encoded,id,version); }
        catch(org.springframework.dao.DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"A matching rent period or viewing slot already exists",e);}
        if(changed==0)throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,"Record version is stale");
        audit.record("UPDATE_"+TYPES.get(resource),"RENTAL_OPERATION",id,Map.of("status",current[2],"details",current[3]),Map.of("status",status,"details",details));
        return Map.of("id",id,"flatId",flat,"memberId",current[1]==null?"":current[1],"status",status,"details",details,"version",version+1);
    }
    @PostMapping("/viewings/{id}/book")
    @Transactional
    public Map<String,Object> bookViewing(@PathVariable UUID id){
        var record=db.query("SELECT flat_id,status,version FROM rental_operations WHERE id=? AND operation_type='VIEWING'",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3)},id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID flat=(UUID)record[0];requirePermission("VIEWING_BOOK",flat);if(!"AVAILABLE".equals(record[1]))throw new ResponseStatusException(HttpStatus.CONFLICT,"Viewing slot is no longer available");
        var actor=access.identity();int changed=db.update("UPDATE rental_operations SET status='BOOKED',member_id=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND status='AVAILABLE' AND version=?",actor.memberId(),id,record[2]);if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Viewing slot is no longer available");
        Map<String,Object> result=Map.of("id",id,"flatId",flat,"status","BOOKED","memberId",actor.memberId()==null?"":actor.memberId());
        audit.record("BOOK_VIEWING","RENTAL_OPERATION",id,Map.of("status","AVAILABLE"),result);
        return result;
    }
    private void requirePermission(String permission,UUID flat){var actor=access.identity(); if(actor==null)throw new AccessDeniedException("Authentication required");
        if(access.has(permission,"SOCIETY"))return;
        if(flat==null||!access.has(permission,"OWN_FLATS")||!actor.flatIds().contains(flat))throw new AccessDeniedException("Permission denied for this flat");
    }
    private void requireResource(String resource){if(!TYPES.containsKey(resource))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private void validatePayload(String resource,Map<String,Object> body){
        Set<String> allowed=switch(resource){case "payments"->Set.of("flatId","period","amount","dueDate","reference");case "maintenance"->Set.of("flatId","summary","description","priority");case "leases"->Set.of("flatId","leaseEndDate","action","rentAmount","securityDeposit","notes");default->Set.of("flatId","slot","notes");};
        if(body.keySet().stream().anyMatch(k->!allowed.contains(k)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported rental workflow field");
        List<String> required=switch(resource){case "payments"->List.of("period","amount","dueDate");case "maintenance"->List.of("summary","description");case "leases"->List.of("leaseEndDate","action","rentAmount","securityDeposit");default->List.of("slot");};
        if(required.stream().anyMatch(k->body.get(k)==null||body.get(k).toString().isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing required fields: "+String.join(", ",required));
        try {
            if("payments".equals(resource)){if(new java.math.BigDecimal(body.get("amount").toString()).signum()<=0)throw new IllegalArgumentException();java.time.LocalDate.parse(body.get("dueDate").toString());if(!body.get("period").toString().matches("\\d{4}-(0[1-9]|1[0-2])"))throw new IllegalArgumentException();}
            if("leases".equals(resource)){java.time.LocalDate.parse(body.get("leaseEndDate").toString());if(!Set.of("RENEWAL","MOVE_OUT").contains(body.get("action").toString().toUpperCase())||new java.math.BigDecimal(body.get("rentAmount").toString()).signum()<=0||new java.math.BigDecimal(body.get("securityDeposit").toString()).signum()<0)throw new IllegalArgumentException();}
            if("viewings".equals(resource))java.time.OffsetDateTime.parse(body.get("slot").toString());
            if("maintenance".equals(resource)&&body.containsKey("priority")&&!Set.of("LOW","NORMAL","HIGH","URGENT").contains(body.get("priority").toString().toUpperCase()))throw new IllegalArgumentException();
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date, amount, or workflow action");}
    }
    private boolean validTransition(String resource,String from,String to){return switch(resource){
        case "payments" -> Set.of("DUE>PAID","OVERDUE>PAID").contains(from+">"+to);
        case "maintenance" -> Set.of("OPEN>IN_PROGRESS","OPEN>RESOLVED","IN_PROGRESS>RESOLVED","RESOLVED>CLOSED").contains(from+">"+to);
        case "leases" -> Set.of("DRAFT>RENEWAL_OFFERED","DRAFT>MOVE_OUT_STARTED","RENEWAL_OFFERED>RENEWED","RENEWAL_OFFERED>MOVE_OUT_STARTED","RENEWAL_OFFERED>RENEWAL_ACCEPTED","RENEWAL_OFFERED>RENEWAL_DECLINED","MOVE_OUT_STARTED>MOVE_OUT_CONFIRMED","MOVE_OUT_STARTED>COMPLETED","RENEWAL_ACCEPTED>RENEWED","RENEWAL_DECLINED>MOVE_OUT_STARTED").contains(from+">"+to);
        case "viewings" -> Set.of("AVAILABLE>CANCELLED","BOOKED>CANCELLED").contains(from+">"+to);
        default -> false;
    };}
    private UUID uuid(Object v){try{return UUID.fromString(Objects.toString(v));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"flatId must be a UUID");}}
    @SuppressWarnings("unchecked")
    private Map<String,Object> row(UUID id,UUID flat,UUID member,String status,String details,UUID actor,Object created,Object updated,long version){try{return Map.of("id",id,"flatId",flat,"memberId",member==null?"":member,"status",status,"details",(Map<String,Object>)json.readValue(details,Map.class),"createdBy",actor,"createdAt",created,"updatedAt",updated,"version",version);}catch(Exception e){throw new IllegalStateException(e);}}
}
