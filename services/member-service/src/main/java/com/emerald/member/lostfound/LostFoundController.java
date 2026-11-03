package com.emerald.member.lostfound;

import static com.emerald.member.lostfound.LostFoundDtos.*;
import com.emerald.member.security.SessionIdentityResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/lost-found")
public class LostFoundController {
    private final LostFoundService service;
    public LostFoundController(LostFoundService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("@memberAccess.has('LOST_FOUND_REPORT','SOCIETY')")
    public Notice create(@Valid @RequestBody CreateReport request, @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.create(request, actor.adminId(), actor.flatIds(), actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY"));
    }

    @GetMapping("/board")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_BOARD','SOCIETY')")
    public List<Notice> board(@AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.board(actor.flatIds(), actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY"));
    }

    @GetMapping("/notifications")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_BOARD','SOCIETY')")
    public List<java.util.Map<String,Object>> notifications(@AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.notifications(actor.adminId(), actor.flatIds(), actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY"));
    }

    @PatchMapping("/notifications/{eventId}/read")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_BOARD','SOCIETY')")
    public ResponseEntity<Void> markNotificationRead(@PathVariable UUID eventId,
            @AuthenticationPrincipal SessionIdentityResponse actor) {
        service.markNotificationRead(eventId, actor.adminId(), actor.flatIds(), actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/claims")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_REPORT','SOCIETY')")
    public AdminClaim claim(@PathVariable UUID id, @Valid @RequestBody Claim request,
                            @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.claim(id,request.identifyingDetails(),actor.adminId(),actor.flatIds());
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public List<AdminReport> reports() { return service.adminReports(); }

    @PatchMapping("/admin/reports/{id}/flat")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public Notice assignFlat(@PathVariable UUID id, @Valid @RequestBody FlatAssignment request,
                             @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.assignFlat(id, request.flatId(), actor.adminId());
    }

    @PatchMapping("/admin/reports/{id}/status")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public Notice status(@PathVariable UUID id,@Valid @RequestBody StatusChange request,
                         @AuthenticationPrincipal SessionIdentityResponse actor) {
        return service.changeStatus(id,request.status(),actor.adminId());
    }

    @GetMapping("/admin/reports/{id}/claims")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public List<AdminClaim> claims(@PathVariable UUID id) { return service.claims(id); }

    @PatchMapping("/admin/reports/{id}/claims/{claimId}")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public AdminClaim decideClaim(@PathVariable UUID id,@PathVariable UUID claimId,
                                  @Valid @RequestBody ClaimDecision request) {
        return service.decideClaim(id,claimId,request.status());
    }

    @PostMapping(value="/{id}/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@memberAccess.has('LOST_FOUND_REPORT','SOCIETY') or @memberAccess.has('LOST_FOUND_ADMIN','SOCIETY')")
    public Notice photo(@PathVariable UUID id,@RequestPart("photo") MultipartFile photo,
                        @AuthenticationPrincipal SessionIdentityResponse actor) {
        boolean admin=actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY");
        return service.savePhoto(id,photo,actor.adminId(),actor.flatIds(),admin);
    }

    @GetMapping("/{id}/photo")
    @PreAuthorize("@memberAccess.has('LOST_FOUND_BOARD','SOCIETY')")
    public ResponseEntity<Resource> photo(@PathVariable UUID id,@AuthenticationPrincipal SessionIdentityResponse actor) {
        boolean admin=actor.permissions().contains("LOST_FOUND_ADMIN:SOCIETY");
        Resource image=service.photo(id,actor.flatIds(),admin);
        MediaType imageType=org.springframework.http.MediaTypeFactory.getMediaType(image).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(imageType)
                .header("Content-Disposition","inline; filename=lost-found-image")
                .header("X-Content-Type-Options","nosniff").body(image);
    }
}
