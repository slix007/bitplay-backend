package com.crypto.controller.config;

import com.crypto.controller.BitplayUIEndpoint;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(BitplayUIEndpoint.class);
    }

}