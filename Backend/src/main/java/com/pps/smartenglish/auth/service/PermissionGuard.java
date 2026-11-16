package com.pps.smartenglish.auth.service;

import com.pps.smartenglish.shared.security.CurrentUser;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/** Luật truy cập theo quyền cho từng nhóm URL trong SecurityConfig. Khách không có người dùng thì bị từ chối. */
@Component
public class PermissionGuard {

    private final PermissionService permissions;

    PermissionGuard(PermissionService permissions) {
        this.permissions = permissions;
    }

    public AuthorizationManager<RequestAuthorizationContext> require(String permission) {
        return (Supplier<Authentication> auth, RequestAuthorizationContext ctx) -> {
            Long userId = CurrentUser.idOrNull(auth.get());
            return new AuthorizationDecision(userId != null && permissions.has(userId, permission));
        };
    }
}
