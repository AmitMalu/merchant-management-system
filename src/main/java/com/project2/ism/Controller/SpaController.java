package com.project2.ism.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {
            "/dashboard/**",
            "/login",
            "/forgot-pass",
            "/reset-password",
            "/reset-password-expired"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
