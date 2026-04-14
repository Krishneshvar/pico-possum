package com.possum.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        passwordHasher = new PasswordHasher();
    }

    @Test
    void hashPassword_generatesValidHash() {
        String password = "StrongPassword123!";
        String hash = passwordHasher.hashPassword(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$")); // BCrypt format
    }

    @Test
    void verifyPassword_correctPassword_returnsTrue() {
        String password = "SecurePassword@2025";
        String hash = passwordHasher.hashPassword(password);

        assertTrue(passwordHasher.verifyPassword(password, hash));
    }

    @Test
    void verifyPassword_incorrectPassword_returnsFalse() {
        String password = "CorrectPassword";
        String hash = passwordHasher.hashPassword(password);

        assertFalse(passwordHasher.verifyPassword("WrongPassword", hash));
    }

    @Test
    void hashPassword_nullInput_throwsException() {
        assertThrows(NullPointerException.class, () -> passwordHasher.hashPassword(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void hashPassword_blankInput_throwsException(String blank) {
        assertThrows(IllegalArgumentException.class, () -> passwordHasher.hashPassword(blank));
    }

    @Test
    void verifyPassword_nullHash_throwsException() {
        assertThrows(NullPointerException.class, () -> passwordHasher.verifyPassword("password", null));
    }

    @Test
    void hashPassword_unicodeCharacters_handledCorrectly() {
        String password = "PasswordWithEmoji🚀";
        String hash = passwordHasher.hashPassword(password);

        assertTrue(passwordHasher.verifyPassword(password, hash));
    }

    @Test
    void hashPassword_specialCharacters_handledCorrectly() {
        String password = "!@#$%^&*()_+=-`~[]\\{}|;':\",./<>?";
        String hash = passwordHasher.hashPassword(password);

        assertTrue(passwordHasher.verifyPassword(password, hash));
    }
}
