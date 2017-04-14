package com.crypto.controller.config;

import com.crypto.controller.OkCoinEndpoint;
import com.crypto.controller.PoloniexEndpoint;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(CORSFilter.class);
        register(OkCoinEndpoint.class);
        register(PoloniexEndpoint.class);
    }

}