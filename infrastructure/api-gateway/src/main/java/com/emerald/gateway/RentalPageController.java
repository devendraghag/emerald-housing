package com.emerald.gateway;

import org.springframework.stereotype.Controller;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RentalPageController {
    @GetMapping({"/rental", "/rental/"})
    public ResponseEntity<Resource> rentalPage() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/portal.html"));
    }
}
