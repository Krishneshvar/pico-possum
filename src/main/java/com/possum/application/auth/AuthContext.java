package com.possum.application.auth;

public final class AuthContext {

    private static final ThreadLocal<AuthUser> currentUser = new ThreadLocal<>();

    public static void setCurrentUser(AuthUser user) {
        if (user != null) {
            org.slf4j.LoggerFactory.getLogger(AuthContext.class).info("Setting current user: {} (ID: {}) on thread {}", user.username(), user.id(), Thread.currentThread().getName());
        } else {
            org.slf4j.LoggerFactory.getLogger(AuthContext.class).info("Setting current user to NULL on thread {}", Thread.currentThread().getName());
        }
        currentUser.set(user);
    }

    public static AuthUser getCurrentUser() {
        return currentUser.get();
    }

    public static void clear() {
        org.slf4j.LoggerFactory.getLogger(AuthContext.class).info("Clearing current user from thread {}", Thread.currentThread().getName());
        currentUser.remove();
    }

    private AuthContext() {
    }
}
