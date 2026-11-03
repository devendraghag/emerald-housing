package com.emerald.member.notices;

import static com.emerald.member.notices.NoticeDtos.*;

import com.emerald.member.security.SessionIdentityResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notices")
public class CommunityNoticeController {
    private final CommunityNoticeService service;
    public CommunityNoticeController(CommunityNoticeService service) { this.service=service; }

    @GetMapping("/flat-options")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_CREATE','SOCIETY')")
    public List<FlatOption> flatOptions() { return service.flatOptions(); }

    @PostMapping
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_CREATE','SOCIETY')")
    public Notice create(@Valid @RequestBody CreateNotice request,
                         @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.create(request,actor.adminId(),isAdmin(actor),actor.flatIds());
    }

    @GetMapping("/board")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public List<Notice> board(@AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.board(actor.flatIds(),isAdmin(actor));
    }

    @GetMapping("/admin/review")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public List<ReviewNotice> reviewQueue() { return service.reviewQueue(); }

    @PatchMapping("/admin/{id}/audience")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public ReviewNotice audience(@PathVariable UUID id,@Valid @RequestBody AudienceChange request) {
        return service.changeAudience(id,request);
    }

    @PatchMapping("/admin/{id}/status")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_ADMIN','SOCIETY')")
    public Notice status(@PathVariable UUID id,@Valid @RequestBody StatusChange request,
                         @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.changeStatus(id,request.status(),actor.adminId());
    }

    @GetMapping("/notifications")
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public List<Map<String,Object>> notifications(@AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.notifications(actor.adminId(),actor.flatIds(),isAdmin(actor));
    }

    @PatchMapping("/notifications/{eventId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@memberAccess.has('COMMUNITY_NOTICE_BOARD','SOCIETY')")
    public void read(@PathVariable UUID eventId,@AuthenticationPrincipal SessionIdentityResponse actor) {
        service.markNotificationRead(eventId,actor.adminId(),actor.flatIds(),isAdmin(actor));
    }

    private static boolean isAdmin(SessionIdentityResponse actor) {
        return actor.permissions().contains("COMMUNITY_NOTICE_ADMIN:SOCIETY");
    }
}
