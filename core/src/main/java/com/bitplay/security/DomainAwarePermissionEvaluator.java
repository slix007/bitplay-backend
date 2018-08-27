package com.bitplay.security;

import java.io.Serializable;
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
    private TraderPermissionsService traderPermissionsService;

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
//            log.info("check permission '{}' for user '{}'", permission, authentication.getName());
            return traderPermissionsService.hasPermissionByEBestMin();
        }

        return true;
    }


    @Override
    public boolean hasPermission(Authentication authentication, Serializable targetId, String targetType,
            Object permission) {
        boolean permissionForAll = hasPermission(authentication, new DomainObjectReference(targetId, targetType), permission);
        boolean roleAdmin = hasRole("ROLE_ADMIN", authentication);

        return permissionForAll || roleAdmin;
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
