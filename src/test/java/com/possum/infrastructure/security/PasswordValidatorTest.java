package com.possum.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordValidatorTest {

    @Test
    void validate_validPassword_returnsSuccess() {
        String valid = "StrongPass1!23";
        PasswordValidator.ValidationResult result = PasswordValidator.validate(valid);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void validate_tooShort_returnsError() {
        String shortPass = "Ab1!"; // 4 chars, too short for PasswordPolicy.MIN_LENGTH (12)
        PasswordValidator.ValidationResult result = PasswordValidator.validate(shortPass);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("at least 12 characters")));
    }

    @Test
    void validate_missingUppercase_returnsError() {
        String noUpper = "lowercase1!23456"; 
        PasswordValidator.ValidationResult result = PasswordValidator.validate(noUpper);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("uppercase")));
    }

    @Test
    void validate_missingLowercase_returnsError() {
        String noLower = "UPPERCASE1!23456";
        PasswordValidator.ValidationResult result = PasswordValidator.validate(noLower);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("lowercase")));
    }

    @Test
    void validate_missingDigit_returnsError() {
        String noDigit = "NoDigitPass!@#$";
        PasswordValidator.ValidationResult result = PasswordValidator.validate(noDigit);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("digit")));
    }

    @Test
    void validate_missingSpecialChar_returnsError() {
        String noSpecial = "NoSpecialChar123";
        PasswordValidator.ValidationResult result = PasswordValidator.validate(noSpecial);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("special character")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "mypassword123!",
        "qwerty123456!",
        "adminpassword1!",
        "welcome2025!"
    })
    void validate_commonPatterns_returnsError(String common) {
        PasswordValidator.ValidationResult result = PasswordValidator.validate(common);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("common patterns")));
    }

    @Test
    void validate_sequentialCharacters_returnsError() {
        String sequential = "Abcd1!efghijk"; // "abcd" and "efgh" are sequential
        PasswordValidator.ValidationResult result = PasswordValidator.validate(sequential);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("common patterns")));
    }

    @Test
    void validate_repeatingCharacters_returnsError() {
        String repeating = "Aaaaa1!Bcccc2@"; // "aaaa" is repeating
        PasswordValidator.ValidationResult result = PasswordValidator.validate(repeating);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("common patterns")));
    }

    @Test
    void validate_nullInput_returnsError() {
        PasswordValidator.ValidationResult result = PasswordValidator.validate(null);
        
        assertFalse(result.valid());
        assertEquals("Password cannot be empty", result.errors().get(0));
    }
}
