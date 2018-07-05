package com.bitplay.api.controller;

import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/auth")
@RestController
class AuthEndpoint {

    @GetMapping
    Map<String, Object> getToken(HttpSession session) {
        return Collections.singletonMap("session", session.getId());
    }
}
