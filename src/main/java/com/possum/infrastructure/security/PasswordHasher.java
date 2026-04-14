package com.possum.infrastructure.security;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Objects;

public final class PasswordHasher {

    private static final int DEFAULT_WORK_FACTOR = 10;

    public String hashPassword(String password) {
        validatePasswordInput(password);
        return BCrypt.hashpw(password, BCrypt.gensalt(DEFAULT_WORK_FACTOR));
    }

    public boolean verifyPassword(String password, String hash) {
        validatePasswordInput(password);
        Objects.requireNonNull(hash, "hash must not be null");
        return BCrypt.checkpw(password, hash);
    }

    private static void validatePasswordInput(String password) {
        Objects.requireNonNull(password, "password must not be null");
        if (password.isBlank()) {
            throw new IllegalArgumentException("password must not be blank");
        }
    }
}
