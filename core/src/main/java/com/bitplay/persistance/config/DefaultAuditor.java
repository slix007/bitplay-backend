package com.bitplay.persistance.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;


/**
 * Created by Sergey Shurmin on 3/21/18.
 */
@Component
public class DefaultAuditor implements AuditorAware<String> {

    @Override
    public String getCurrentAuditor() {
        // get your user name here
        return "Fplay admin";
    }
}
