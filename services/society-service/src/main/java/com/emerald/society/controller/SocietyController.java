package com.emerald.society.controller;

import com.emerald.society.dto.SocietyResponse;
import com.emerald.society.service.SocietyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/society")
public class SocietyController {

    private final SocietyService societyService;

    public SocietyController(SocietyService societyService) {
        this.societyService = societyService;
    }

    @GetMapping
    public SocietyResponse getSociety() {
        return societyService.getSociety();
    }
}
