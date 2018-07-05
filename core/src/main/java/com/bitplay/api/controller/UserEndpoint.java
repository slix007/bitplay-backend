package com.bitplay.api.controller;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/user")
@RestController
public class UserEndpoint {

    @GetMapping
    Map<String, Object> info(@AuthenticationPrincipal Principal user) {

        Map<String, Object> map = new HashMap<>();
        map.put("user", user.getName());
        if (user instanceof UsernamePasswordAuthenticationToken) {
            UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) user;
            for (GrantedAuthority grantedAuthority : token.getAuthorities()) {
                map.put(grantedAuthority.getAuthority(), user.getName());
            }
        }
        return map;
    }


    @GetMapping("/manage")
    @Secured("ROLE_ADMIN")
    Map<String, Object> manage(@AuthenticationPrincipal Principal user) {
        return Collections.singletonMap("user", user.getName());
    }
}
