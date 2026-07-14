package com.codemind.platform.identity;

import java.util.Optional;

public final class AgentExecutionUserContextHolder {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private AgentExecutionUserContextHolder() {
    }

    public static Optional<CurrentUser> currentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static Scope open(CurrentUser currentUser) {
        CurrentUser previous = CURRENT_USER.get();
        CURRENT_USER.set(currentUser);

        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {

        private final CurrentUser previous;

        private Scope(CurrentUser previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT_USER.remove();
            } else {
                CURRENT_USER.set(previous);
            }
        }
    }
}