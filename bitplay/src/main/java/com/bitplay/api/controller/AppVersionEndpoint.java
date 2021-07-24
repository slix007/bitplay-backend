package com.bitplay.api.controller;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class AppVersionEndpoint {

    @Value("${branch.name}")
    private String branchName;
    @Value("${commit.hash}")
    private String commitHash;
    @Value("${application.version}")
    private String applicationVersion;
    @Value("${build.timestamp}")
    private String buildTimestamp;

    @RequestMapping(value = "/app-version", method = RequestMethod.GET)
    public Map<String,String> help() {
        final Map<String, String> map = new HashMap<>();
        map.put("branch.name", branchName);
        map.put("commit.hash", commitHash);
        map.put("application.version", applicationVersion);
        map.put("build.timestamp", buildTimestamp);
        return map;
    }

}
