package com.possum.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DomainValidatorsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "user@example.com",
        "first.last@domain.co.uk",
        "name123@sub.domain.org",
        "a@b.cd"
    })
    @DisplayName("Should validate correct email formats")
    void validateEmail_correct(String email) {
        assertTrue(DomainValidators.EMAIL.matcher(email).matches(), "Email should be valid: " + email);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "plainaddress",
        "#@%^%#$@#$@#.com",
        "@example.com",
        "Joe Smith <email@example.com>",
        "email.example.com",
        "email@example@example.com",
        ".email@example.com",
        "email.@example.com",
        "email..email@example.com",
        "あいうえお@example.com",
        "email@example.com (Joe Smith)",
        "email@example",
        "email@-example.com",
        "email@111.222.333.44444",
        "email@example..com",
        "Abc..123@example.com"
    })
    @DisplayName("Should invalidate incorrect email formats")
    void validateEmail_incorrect(String email) {
        assertFalse(DomainValidators.EMAIL.matcher(email).matches(), "Email should be invalid: " + email);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1234567",
        "+1234567890",
        "0123-456-789",
        "(123) 456-7890",
        "123 456 7890",
        "+44 20 7946 0958"
    })
    @DisplayName("Should validate correct phone formats")
    void validatePhone_correct(String phone) {
        assertTrue(DomainValidators.PHONE.matcher(phone).matches(), "Phone should be valid: " + phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "123456", // Too short
        "123456789012345678901", // Too long (21 chars)
        "phone123",
        "+++123",
        "12-34-56-78-90-12-34-56-78-90-12"
    })
    @DisplayName("Should invalidate incorrect phone formats")
    void validatePhone_incorrect(String phone) {
        assertFalse(DomainValidators.PHONE.matcher(phone).matches(), "Phone should be invalid: " + phone);
    }

    @Test
    @DisplayName("Should check constant values")
    void checkConstants() {
        assertEquals(8, DomainValidators.MIN_PASSWORD_LENGTH);
        assertEquals(3, DomainValidators.MIN_USERNAME_LENGTH);
    }
}
