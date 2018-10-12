package com.bitplay.api.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ActuatorController {

//    @ResponseBody
//    @RequestMapping(path = "/actuator")
//    public String home(HttpServletRequest request) {
//
//        String contextPath = request.getContextPath();
//        String host = request.getServerName();
//
//        // Spring Boot >= 2.0.0.M7
//        String endpointBasePath = "/actuator";
//
//        StringBuilder sb = new StringBuilder();
//
//        sb.append("<h2>Sprig Boot Actuator</h2>");
//        sb.append("<ul>");
//
//        // http://localhost:8090/actuator
//        String url = "http://" + host + ":4031" + contextPath + endpointBasePath;
//
//        sb.append("<li><a href='" + url + "'>" + url + "</a></li>");
//
//        sb.append("</ul>");
//
//        return sb.toString();
//    }

}
