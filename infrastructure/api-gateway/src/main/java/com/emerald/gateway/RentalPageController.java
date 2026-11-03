package com.emerald.gateway;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RentalPageController {
    @GetMapping({"/rental", "/rental/"})
    public String rentalPage() {
        return "forward:/portal.html";
    }
}
