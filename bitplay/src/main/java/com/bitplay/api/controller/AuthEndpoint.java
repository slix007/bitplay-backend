package com.bitplay.api.controller;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpSession;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/auth")
@RestController
class AuthEndpoint {

    @Getter
    @Setter
    @ToString
    public static class Cred {
        String username;
        String password;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> auth(@RequestBody Cred user) {
        String credString = user.username + ":" + user.password;
        String encoded = Base64.getEncoder().encodeToString(credString.getBytes());
        return Collections.singletonMap("xAuthString", encoded);
    }

//    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
//    Map<String, Object> getToken(@RequestBody Cred cred, HttpSession session) {
//        System.out.println(cred);
//
//        return Collections.singletonMap("session", session.getId());
//    }

    @RequestMapping(value = "/logout", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) {
        session.invalidate();
    }
}
