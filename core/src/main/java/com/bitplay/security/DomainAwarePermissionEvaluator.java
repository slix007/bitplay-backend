package com.bitplay.security;

import com.bitplay.Config;
import com.bitplay.arbitrage.ArbitrageService;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collection;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
class DomainAwarePermissionEvaluator implements PermissionEvaluator {

    @Autowired
    private Config config;

    @Autowired
    private ArbitrageService arbitrageService;

    @Override
    public boolean hasPermission(Authentication authentication, Object targetDomainObject, Object permission) {

//        log.info("check permission '{}' for user '{}' for target '{}'", permission, authentication.getName(),
//                targetDomainObject);

//        if ("place-order".equals(permission)) {
//            Order order = (Order) targetDomainObject;
//            if (order.getAmount() > 500) {
//                return hasRole("ROLE_ADMIN", authentication);
//            }
//        }
        if ("e_best_min-check".equals(permission)) {
            return checkEBestMin(authentication, targetDomainObject, permission);
        }

        return true;
    }

    private boolean checkEBestMin(Authentication authentication, Object targetDomainObject, Object permission) {

        try {
            final BigDecimal sumEBestUsd = arbitrageService.getSumEBestUsd();
            final Integer eBestMin = config.getEBestMin();
            log.info("check permission '{}' for user '{}'. sumEBestUsd={}, eBestMin={}",
                    permission, authentication.getName(),
                    sumEBestUsd,
                    eBestMin);

            if (eBestMin == null) {
                log.warn("WARNING: e_best_min is not set");
                return true;
            }

//            log.info("e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
            if (sumEBestUsd.signum() < 0) { // not initialized yet
                return false;
            }
            if (sumEBestUsd.compareTo(BigDecimal.valueOf(eBestMin)) < 0) {
                log.warn("WARNING: e_best_min=" + eBestMin + ", sumEBestUsd=" + sumEBestUsd);
                return false;
            }
        } catch (Exception e) {
            log.error("Check permission exception ", e);
            return false;
        }

        return true; // all validations completed
    }

    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
            Object permission) {
        return hasPermission(authentication, new DomainObjectReference(targetId, targetType), permission);
    }

    private boolean hasRole(String role, Authentication auth) {

        if (auth == null || auth.getPrincipal() == null) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        if (CollectionUtils.isEmpty(authorities)) {
            return false;
        }

        return authorities.stream().filter(ga -> role.equals(ga.getAuthority())).findAny().isPresent();
    }

    @Value
    static class DomainObjectReference {

        private final Serializable targetId;
        private final String targetType;
    }
}
