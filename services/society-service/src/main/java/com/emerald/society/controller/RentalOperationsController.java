package com.emerald.society.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.emerald.society.security.SocietyAccess;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Rental workflows share flat-scoped records while retaining independent permission checks. */
@RestController
@RequestMapping("/api/v1/rental")
public class RentalOperationsController {
    private static final Map<String,String> VIEW = Map.of("payments","RENT_VIEW", "maintenance","MAINTENANCE_VIEW", "leases","LEASE_VIEW", "viewings","VIEWING_VIEW");
    private static final Map<String,String> CREATE = Map.of("payments","RENT_MANAGE", "maintenance","MAINTENANCE_CREATE", "leases","LEASE_MANAGE", "viewings","VIEWING_BOOK");
    private static final Map<String,String> MANAGE = Map.of("payments","RENT_MANAGE", "maintenance","MAINTENANCE_MANAGE", "leases","LEASE_MANAGE", "viewings","VIEWING_MANAGE");
    private static final Map<String,String> TYPES = Map.of("payments","PAYMENT", "maintenance","MAINTENANCE", "leases","LEASE", "viewings","VIEWING");
    private final JdbcTemplate db;
    private final SocietyAccess access;
    private final ObjectMapper json;
    public RentalOperationsController(JdbcTemplate db, SocietyAccess access, ObjectMapper json) { this.db=db; this.access=access; this.json=json; }

    @GetMapping("/{resource}")
    public List<Map<String,Object>> list(@PathVariable String resource, @RequestParam(required=false) UUID flatId) {
        requireResource(resource); var actor=access.identity();
        boolean society=access.has(VIEW.get(resource),"SOCIETY");
        if(!society&&(!access.has(VIEW.get(resource),"OWN_FLATS")||actor==null||actor.flatIds().isEmpty()||(flatId!=null&&!actor.flatIds().contains(flatId))))throw new AccessDeniedException("Permission denied for this flat");
        String sql="SELECT id,flat_id,member_id,status,details,created_by,created_at,updated_at,version FROM rental_operations WHERE operation_type=?";
        List<Object> args=new ArrayList<>(List.of(TYPES.get(resource)));
        if (access.has(VIEW.get(resource),"SOCIETY")) { if(flatId!=null){sql+=" AND flat_id=?";args.add(flatId);} }
        else { var flats=access.ownFlats(); if(flats.isEmpty()) return List.of(); sql+=" AND flat_id IN ("+String.join(",",Collections.nCopies(flats.size(),"?"))+ ")";args.addAll(flats); }
        sql+=" ORDER BY created_at DESC LIMIT 200";
        return db.query(sql,(rs,n)->row(rs.getObject("id",UUID.class),rs.getObject("flat_id",UUID.class),rs.getObject("member_id",UUID.class),rs.getString("status"),rs.getString("details"),rs.getObject("created_by",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),rs.getLong("version")),args.toArray());
    }

    @PostMapping("/{resource}") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@PathVariable String resource, @RequestBody Map<String,Object> body) {
        requireResource(resource); UUID flat=uuid(body.get("flatId"));
        validatePayload(resource,body);
        if("viewings".equals(resource)&&(access.has("VIEWING_MANAGE","SOCIETY")||access.has("VIEWING_MANAGE","OWN_FLATS")))requirePermission("VIEWING_MANAGE",flat);else requirePermission(CREATE.get(resource),flat);
        UUID id=UUID.randomUUID(); var actor=access.identity(); UUID member=actor.memberId();
        String status=switch(resource){case "payments"->"DUE";case "maintenance"->"OPEN";case "leases"->"DRAFT";default->access.has("VIEWING_MANAGE","SOCIETY")||access.has("VIEWING_MANAGE","OWN_FLATS")?"AVAILABLE":"BOOKED";};
        try { db.update("INSERT INTO rental_operations(id,operation_type,flat_id,member_id,status,details,created_by) VALUES (?,?,?, ?,?,?::jsonb,?)",id,TYPES.get(resource),flat,member,status,json.writeValueAsString(body),actor.adminId()); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid rental workflow data",e);}
        return Map.of("id",id,"flatId",flat,"memberId",member==null?"":member,"status",status,"details",body,"version",0);
    }

    @PatchMapping("/{resource}/{id}")
    public Map<String,Object> update(@PathVariable String resource,@PathVariable UUID id,@RequestBody Map<String,Object> body){
        requireResource(resource); var current=db.query("SELECT flat_id,member_id,status,details,version FROM rental_operations WHERE id=? AND operation_type=?",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getLong(5)},id,TYPES.get(resource)).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID flat=(UUID)current[0];
        long version=((Number)current[4]).longValue(); if(!body.containsKey("version")||!(body.get("version") instanceof Number supplied)||supplied.longValue()!=version)throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,"Supply the current record version");
        String requested=body.getOrDefault("status",current[2]).toString(); String status=requested;
        if(access.has(MANAGE.get(resource),"SOCIETY")||access.has(MANAGE.get(resource),"OWN_FLATS")) requirePermission(MANAGE.get(resource),flat);
        else {
            requirePermission("LEASE_VIEW",flat);
            if(body.keySet().stream().anyMatch(k->!Set.of("status","version").contains(k)))throw new AccessDeniedException("Tenant responses may only include status and version");
            if(!"leases".equals(resource)||!Set.of("RENEWAL_ACCEPTED","RENEWAL_DECLINED","MOVE_OUT_CONFIRMED").contains(requested)||
                    !("RENEWAL_OFFERED".equals(current[2])&&requested.startsWith("RENEWAL_"))&&!("MOVE_OUT_STARTED".equals(current[2])&&"MOVE_OUT_CONFIRMED".equals(requested)))
                throw new AccessDeniedException("Tenants may only respond to their own renewal or move-out workflow");
        }
        if(!validTransition(resource,current[2].toString(),requested))throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid workflow status transition");
        Map<String,Object> details=new LinkedHashMap<>(); String encoded;
        try { details.putAll(json.readValue((String)current[3],new TypeReference<Map<String,Object>>(){})); details.putAll(body); details.remove("status");details.remove("version"); encoded=json.writeValueAsString(details); }
        catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid update",e);}
        int changed=db.update("UPDATE rental_operations SET status=?,details=?::jsonb,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=?",status,encoded,id,version);
        if(changed==0)throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,"Record version is stale");
        return Map.of("id",id,"flatId",flat,"memberId",current[1]==null?"":current[1],"status",status,"details",details,"version",version+1);
    }
    @PostMapping("/viewings/{id}/book")
    public Map<String,Object> bookViewing(@PathVariable UUID id){
        var record=db.query("SELECT flat_id,status,version FROM rental_operations WHERE id=? AND operation_type='VIEWING'",(rs,n)->new Object[]{rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3)},id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        UUID flat=(UUID)record[0];requirePermission("VIEWING_BOOK",flat);if(!"AVAILABLE".equals(record[1]))throw new ResponseStatusException(HttpStatus.CONFLICT,"Viewing slot is no longer available");
        var actor=access.identity();int changed=db.update("UPDATE rental_operations SET status='BOOKED',member_id=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND status='AVAILABLE' AND version=?",actor.memberId(),id,record[2]);if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Viewing slot is no longer available");
        return Map.of("id",id,"flatId",flat,"status","BOOKED","memberId",actor.memberId()==null?"":actor.memberId());
    }
    private void requirePermission(String permission,UUID flat){var actor=access.identity(); if(actor==null)throw new AccessDeniedException("Authentication required");
        if(access.has(permission,"SOCIETY"))return;
        if(flat==null||!access.has(permission,"OWN_FLATS")||!actor.flatIds().contains(flat))throw new AccessDeniedException("Permission denied for this flat");
    }
    private void requireResource(String resource){if(!TYPES.containsKey(resource))throw new ResponseStatusException(HttpStatus.NOT_FOUND);}
    private void validatePayload(String resource,Map<String,Object> body){
        List<String> required=switch(resource){case "payments"->List.of("period","amount","dueDate");case "maintenance"->List.of("summary","description");case "leases"->List.of("leaseEndDate","action");default->List.of("slot");};
        if(required.stream().anyMatch(k->body.get(k)==null||body.get(k).toString().isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing required fields: "+String.join(", ",required));
        try {
            if("payments".equals(resource)){if(new java.math.BigDecimal(body.get("amount").toString()).signum()<=0)throw new IllegalArgumentException();java.time.LocalDate.parse(body.get("dueDate").toString());if(!body.get("period").toString().matches("\\d{4}-(0[1-9]|1[0-2])"))throw new IllegalArgumentException();}
            if("leases".equals(resource)){java.time.LocalDate.parse(body.get("leaseEndDate").toString());if(!Set.of("RENEWAL","MOVE_OUT").contains(body.get("action").toString().toUpperCase()))throw new IllegalArgumentException();}
            if("viewings".equals(resource))java.time.LocalDateTime.parse(body.get("slot").toString());
            if("maintenance".equals(resource)&&body.containsKey("priority")&&!Set.of("LOW","NORMAL","HIGH","URGENT").contains(body.get("priority").toString().toUpperCase()))throw new IllegalArgumentException();
        }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid date, amount, or workflow action");}
    }
    private boolean validTransition(String resource,String from,String to){return switch(resource){
        case "payments" -> Set.of("DUE>PAID","DUE>OVERDUE","OVERDUE>PAID").contains(from+">"+to);
        case "maintenance" -> Set.of("OPEN>IN_PROGRESS","OPEN>RESOLVED","IN_PROGRESS>RESOLVED","RESOLVED>CLOSED").contains(from+">"+to);
        case "leases" -> Set.of("DRAFT>RENEWAL_OFFERED","DRAFT>MOVE_OUT_STARTED","RENEWAL_OFFERED>RENEWED","RENEWAL_OFFERED>MOVE_OUT_STARTED","RENEWAL_OFFERED>RENEWAL_ACCEPTED","RENEWAL_OFFERED>RENEWAL_DECLINED","MOVE_OUT_STARTED>MOVE_OUT_CONFIRMED","MOVE_OUT_STARTED>COMPLETED","RENEWAL_ACCEPTED>RENEWED","RENEWAL_DECLINED>MOVE_OUT_STARTED").contains(from+">"+to);
        case "viewings" -> Set.of("AVAILABLE>CANCELLED","BOOKED>CANCELLED").contains(from+">"+to);
        default -> false;
    };}
    private UUID uuid(Object v){try{return UUID.fromString(Objects.toString(v));}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"flatId must be a UUID");}}
    private Map<String,Object> row(UUID id,UUID flat,UUID member,String status,String details,UUID actor,Object created,Object updated,long version){try{return Map.of("id",id,"flatId",flat,"memberId",member==null?"":member,"status",status,"details",json.readValue(details,new TypeReference<Map<String,Object>>(){}),"createdBy",actor,"createdAt",created,"updatedAt",updated,"version",version);}catch(Exception e){throw new IllegalStateException(e);}}
}
