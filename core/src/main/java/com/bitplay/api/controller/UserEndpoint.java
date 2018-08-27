package com.bitplay.api.controller;

import java.security.Principal;
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
        return getUserInfo(user);
    }

    private Map<String, Object> getUserInfo(@AuthenticationPrincipal Principal user) {
        Map<String, Object> map = new HashMap<>();
        if (user == null) {
            map.put("user", "unauthorized");
        } else {
            map.put("user", user.getName());
            if (user instanceof UsernamePasswordAuthenticationToken) {
                UsernamePasswordAuthenticationToken token = (UsernamePasswordAuthenticationToken) user;
                Object[] objects = token.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toArray();
                map.put("roles", objects);
            }
            map.put("details", user.toString());
        }
        return map;
    }


    @Secured("ROLE_TRADER")
    @GetMapping("/manage")
    Map<String, Object> manage(@AuthenticationPrincipal Principal user) {
        return getUserInfo(user);
    }
}
