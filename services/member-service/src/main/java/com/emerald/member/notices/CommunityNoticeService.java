package com.emerald.member.notices;

import static com.emerald.member.notices.NoticeDtos.*;

import com.emerald.member.client.SocietyClient;
import com.emerald.member.dto.SocietyFlatResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityNoticeService {
    private static final Set<String> SCOPES=Set.of("ADMIN_ONLY","SOCIETY","FLATS");
    private final JdbcTemplate db;
    private final SocietyClient society;
    private final CommunityNoticeEventPublisher events;

    public CommunityNoticeService(JdbcTemplate db, SocietyClient society, CommunityNoticeEventPublisher events) {
        this.db=db; this.society=society; this.events=events;
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_CREATE','SOCIETY')")
    public List<FlatOption> flatOptions() {
        return society.noticeFlatOptions().stream().filter(f->"ACTIVE".equals(f.status()))
                .map(f->new FlatOption(f.flatId(),f.flatNumber())).toList();
    }

    @Transactional
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_CREATE','SOCIETY')")
    public Notice create(CreateNotice request, UUID actor, boolean administrator, Set<UUID> actorFlatIds) {
        String title=request.title().trim();
        String message=request.message().trim();
        if(title.isEmpty()||message.isEmpty()) throw bad("Title and message are required");
        String scope=normalizeScope(request.audienceScope(),administrator);
        List<UUID> flatIds=normalizeFlatIds(request.flatIds(),scope);
        if (!administrator && actorFlatIds != null && flatIds.stream().anyMatch(actorFlatIds::contains))
            throw bad("Resident notices must be addressed to other flats");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO community_notices(id,created_by,title,message,audience_scope,status) VALUES (?,?,?,?,?,'SUBMITTED')",
                id,actor,title,message,scope);
        insertAudience(id,flatIds);
        return get(id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public List<Notice> board(Set<UUID> flatIds, boolean administrator) {
        if(administrator) return db.query("SELECT * FROM community_notices WHERE status='PUBLISHED' ORDER BY created_at DESC",this::notice);
        if(flatIds==null||flatIds.isEmpty())
            return db.query("SELECT * FROM community_notices WHERE status='PUBLISHED' AND audience_scope='SOCIETY' ORDER BY created_at DESC",this::notice);
        String marks=placeholders(flatIds.size());
        String sql="""
                SELECT DISTINCT n.* FROM community_notices n
                LEFT JOIN community_notice_flats nf ON nf.notice_id=n.id
                WHERE n.status='PUBLISHED' AND (n.audience_scope='SOCIETY'
                    OR (n.audience_scope='FLATS' AND nf.flat_id IN (%s)))
                ORDER BY n.created_at DESC
                """.formatted(marks);
        return db.query(sql,this::notice,flatIds.toArray());
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public List<ReviewNotice> reviewQueue() {
        return db.query("SELECT * FROM community_notices WHERE status IN ('SUBMITTED','UNDER_REVIEW') ORDER BY created_at",
                (rs,n)->review(rs));
    }

    @Transactional
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public ReviewNotice changeAudience(UUID id, AudienceChange request) {
        var current=get(id);
        if(!Set.of("SUBMITTED","UNDER_REVIEW").contains(current.status()))
            throw conflict("Audience can only be changed before publication");
        String scope=normalizeScope(request.audienceScope(),true);
        List<UUID> flatIds=normalizeFlatIds(request.flatIds(),scope);
        db.update("UPDATE community_notices SET audience_scope=?,updated_at=now() WHERE id=?",scope,id);
        db.update("DELETE FROM community_notice_flats WHERE notice_id=?",id);
        insertAudience(id,flatIds);
        return review(id);
    }

    @Transactional
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public Notice changeStatus(UUID id, String requestedStatus, UUID actor) {
        String next=requestedStatus==null?"":requestedStatus.trim().toUpperCase();
        if(!Set.of("UNDER_REVIEW","PUBLISHED","REJECTED","CLOSED","EXPIRED").contains(next))
            throw bad("Unsupported notice status");
        Notice current=get(id);
        boolean allowed=switch(current.status()) {
            case "SUBMITTED" -> Set.of("UNDER_REVIEW","REJECTED").contains(next);
            case "UNDER_REVIEW" -> Set.of("PUBLISHED","REJECTED").contains(next);
            case "PUBLISHED" -> Set.of("CLOSED","EXPIRED").contains(next);
            default -> false;
        };
        if(!allowed) throw conflict("Invalid notice status transition");
        db.update("UPDATE community_notices SET status=?,reviewed_by=?,reviewed_at=now(),updated_at=now() WHERE id=?",
                next,actor,id);
        if("PUBLISHED".equals(next)) events.publish(CommunityNoticeEvent.create(id,"CommunityNoticePublished",actor));
        return get(id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public List<Map<String,Object>> notifications(UUID recipient, Set<UUID> flatIds, boolean administrator) {
        if(administrator) return db.queryForList("SELECT event_id,notice_id,event_type,created_at,read_at FROM community_notice_notifications WHERE recipient_id=? ORDER BY created_at DESC LIMIT 100",recipient);
        if(flatIds==null||flatIds.isEmpty())
            return db.queryForList("SELECT nn.event_id,nn.notice_id,nn.event_type,nn.created_at,nn.read_at FROM community_notice_notifications nn JOIN community_notices n ON n.id=nn.notice_id WHERE nn.recipient_id=? AND n.status='PUBLISHED' AND n.audience_scope='SOCIETY' ORDER BY nn.created_at DESC LIMIT 100",recipient);
        String marks=placeholders(flatIds.size());
        List<Object> args=new ArrayList<>(); args.add(recipient); args.addAll(flatIds);
        String sql="""
                SELECT DISTINCT nn.event_id,nn.notice_id,nn.event_type,nn.created_at,nn.read_at
                FROM community_notice_notifications nn JOIN community_notices n ON n.id=nn.notice_id
                LEFT JOIN community_notice_flats nf ON nf.notice_id=n.id
                WHERE nn.recipient_id=? AND n.status='PUBLISHED'
                  AND (n.audience_scope='SOCIETY' OR (n.audience_scope='FLATS' AND nf.flat_id IN (%s)))
                ORDER BY nn.created_at DESC LIMIT 100
                """.formatted(marks);
        return db.queryForList(sql,args.toArray());
    }

    @Transactional
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public void markNotificationRead(UUID eventId, UUID recipient, Set<UUID> flatIds, boolean administrator) {
        int updated;
        if(administrator) updated=db.update("UPDATE community_notice_notifications SET read_at=COALESCE(read_at,now()) WHERE event_id=? AND recipient_id=?",eventId,recipient);
        else if(flatIds==null||flatIds.isEmpty()) updated=db.update("""
                UPDATE community_notice_notifications nn SET read_at=COALESCE(nn.read_at,now())
                FROM community_notices n WHERE nn.event_id=? AND nn.recipient_id=? AND n.id=nn.notice_id
                  AND n.status='PUBLISHED' AND n.audience_scope='SOCIETY'
                """,eventId,recipient);
        else {
            String marks=placeholders(flatIds.size());
            List<Object> args=new ArrayList<>(); args.add(eventId); args.add(recipient); args.addAll(flatIds);
            String sql="""
                    UPDATE community_notice_notifications nn SET read_at=COALESCE(nn.read_at,now())
                    FROM community_notices n LEFT JOIN community_notice_flats nf ON nf.notice_id=n.id
                    WHERE nn.event_id=? AND nn.recipient_id=? AND n.id=nn.notice_id AND n.status='PUBLISHED'
                      AND (n.audience_scope='SOCIETY' OR (n.audience_scope='FLATS' AND nf.flat_id IN (%s)))
                    """.formatted(marks);
            updated=db.update(sql,args.toArray());
        }
        if(updated==0) throw notFound();
    }

    private Notice get(UUID id) {
        Notice result=db.query("SELECT * FROM community_notices WHERE id=?",rs->rs.next()?notice(rs,0):null,id);
        if (result == null) throw notFound();
        return result;
    }
    private ReviewNotice review(UUID id) {
        return db.query("SELECT * FROM community_notices WHERE id=?",rs->rs.next()?review(rs):null,id);
    }
    private ReviewNotice review(ResultSet rs) throws SQLException {
        UUID id=rs.getObject("id",UUID.class);
        return new ReviewNotice(notice(rs,0),rs.getObject("created_by",UUID.class),
                db.query("SELECT flat_id FROM community_notice_flats WHERE notice_id=? ORDER BY flat_id",(row,n)->row.getObject(1,UUID.class),id));
    }
    private Notice notice(ResultSet rs,int row) throws SQLException {
        return new Notice(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("message"),
                rs.getString("audience_scope"),rs.getString("status"),rs.getTimestamp("created_at").toInstant());
    }
    private String normalizeScope(String value,boolean administrator) {
        String scope=value==null?"":value.trim().toUpperCase();
        if(!SCOPES.contains(scope)) throw bad("Audience must be ADMIN_ONLY, SOCIETY, or FLATS");
        if(!administrator&&"SOCIETY".equals(scope)) throw bad("Resident notices must be addressed to an administrator or selected flats");
        return scope;
    }
    private List<UUID> normalizeFlatIds(List<UUID> ids,String scope) {
        LinkedHashSet<UUID> unique=new LinkedHashSet<>();
        if(ids!=null) for(UUID id:ids) { if(id==null) throw bad("Flat identifiers cannot be empty"); unique.add(id); }
        if("FLATS".equals(scope)&&(unique.isEmpty()||unique.size()>100)) throw bad("Choose between 1 and 100 target flats");
        if(!"FLATS".equals(scope)&&!unique.isEmpty()) throw bad("Flat identifiers are only used with the FLATS audience");
        return List.copyOf(unique);
    }
    private void insertAudience(UUID id,List<UUID> flatIds) {
        for(UUID flatId:flatIds) db.update("INSERT INTO community_notice_flats(notice_id,flat_id) VALUES (?,?)",id,flatId);
    }
    private static String placeholders(int n) { return String.join(",",java.util.Collections.nCopies(n,"?")); }
    private static org.springframework.web.server.ResponseStatusException bad(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,message);
    }
    private static org.springframework.web.server.ResponseStatusException conflict(String message) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,message);
    }
    private static Notice throwNotFound() {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Notice not found");
    }
    private static org.springframework.web.server.ResponseStatusException notFound() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Notification not found");
    }
}
