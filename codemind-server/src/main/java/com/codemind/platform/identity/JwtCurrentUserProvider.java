package com.codemind.platform.identity;

import org.springframework.context.annotation.Primary;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Primary
@Service
public class JwtCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser getCurrentUser() {
        return AgentExecutionUserContextHolder.currentUser()
                .orElseGet(this::resolveFromSecurityContext);
    }

    private CurrentUser resolveFromSecurityContext() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new IllegalStateException("Current user is not authenticated.");
        }

        return currentUser;
    }
}