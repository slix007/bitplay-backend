package com.bitplay.controller.config;

import com.bitplay.controller.OkCoinEndpoint;
import com.bitplay.controller.PoloniexEndpoint;
import com.bitplay.controller.CommonEndpoint;
import com.bitplay.controller.error.ExceptionMapper;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(CORSFilter.class);
        register(CommonEndpoint.class);
        register(OkCoinEndpoint.class);
        register(PoloniexEndpoint.class);
        register(ExceptionMapper.class);
    }

}