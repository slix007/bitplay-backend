package com.bitplay.api.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoints;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@DependsOn
public class ActuatorController {

    @Autowired
    private MvcEndpoints endpoints;

    @ResponseBody
    @RequestMapping(path = "/actuator/list")
    public String[] getActuatorEndpointPaths() {

        return endpoints.getEndpoints().stream()
                .map(MvcEndpoint::getPath)
                .map(path -> path + "/**")
                .toArray(String[]::new);
    }

    @ResponseBody
    @RequestMapping(path = "/actuator")
    public String home(HttpServletRequest request) {

        String contextPath = request.getContextPath();
        String host = request.getServerName();

        String endpointBasePath = "/actuator";

        StringBuilder sb = new StringBuilder();

        sb.append("<h2>Sprig Boot Actuator</h2>");
        sb.append("<ul>");

        // http://localhost:8090/actuator
        String baseUrl = "http://" + host + ":4031" + contextPath + endpointBasePath;

        endpoints.getEndpoints().stream()
                .map(MvcEndpoint::getPath)
                .map(path -> baseUrl + path)
                .forEach(url -> sb.append("<li><a href='" + url + "'>" + url + "</a></li>"));

        sb.append("</ul>");

        return sb.toString();
    }

}
