package com.codemind.platform.identity;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LocalCurrentUserProvider implements CurrentUserProvider {

    private static final UUID LOCAL_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    public CurrentUser getCurrentUser() {
        return AgentExecutionUserContextHolder.currentUser()
                .orElseGet(this::localUser);
    }

    private CurrentUser localUser() {
        return new CurrentUser(
                LOCAL_USER_ID,
                "local_user",
                "本地开发用户"
        );
    }
}