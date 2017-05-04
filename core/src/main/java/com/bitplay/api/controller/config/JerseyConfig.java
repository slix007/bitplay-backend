package com.bitplay.api.controller.config;

import com.bitplay.api.controller.BitmexEndpoint;
import com.bitplay.api.controller.OkCoinEndpoint;
import com.bitplay.api.controller.PoloniexEndpoint;
import com.bitplay.api.controller.CommonEndpoint;
import com.bitplay.api.controller.error.ExceptionMapper;

import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Created by Sergey Shurmin on 4/3/17.
 */
@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(CORSFilter.class);
        register(ExceptionMapper.class);
        register(CommonEndpoint.class);
        register(OkCoinEndpoint.class);
        register(PoloniexEndpoint.class);
        register(BitmexEndpoint.class);
    }

}