package com.emerald.member.audit;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
@RestController
@RequestMapping("/api/v1/members/audit")
public class AuditController {
 private final AuditTrail audit;
 public AuditController(AuditTrail audit) { this.audit=audit; }
 @GetMapping @PreAuthorize("@memberAccess.manage()")
 public Object list(@RequestParam(required=false) String entityType,@RequestParam(required=false) UUID entityId,@RequestParam(defaultValue="50") int limit,@RequestParam(defaultValue="0") long before) { return audit.list(entityType,entityId,limit,before); }
}
