package com.emerald.society.controller;

import com.emerald.society.dto.NoticeFlatOption;
import com.emerald.society.service.FlatService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/notices")
public class InternalNoticeDirectoryController {
    private final FlatService flats;
    public InternalNoticeDirectoryController(FlatService flats) { this.flats=flats; }

    @GetMapping("/flat-options")
    @PreAuthorize("hasAuthority('SERVICE_MEMBER_FLAT_READ')")
    public List<NoticeFlatOption> flatOptions() { return flats.noticeTargetOptions(); }
}
