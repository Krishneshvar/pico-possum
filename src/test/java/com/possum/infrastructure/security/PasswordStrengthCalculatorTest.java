package com.possum.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PasswordStrengthCalculatorTest {

    @ParameterizedTest
    @CsvSource({
        "'', VERY_WEAK, 0",
        "123, VERY_WEAK, 0",
        "abcdefgh, VERY_WEAK, 0",
        "Abcdefgh1!, MODERATE, 50",
        "Abcdefgh1!A1!, VERY_STRONG, 100",
        "VeryLongPasswordWithMixedChars123!@#, VERY_STRONG, 100"
    })
    void calculateStrength_returnsCorrectLevelAndScore(String password, PasswordStrengthCalculator.Strength expectedLevel, int expectedScore) {
        PasswordStrengthCalculator.StrengthResult result = PasswordStrengthCalculator.calculateStrength(password);
        
        assertEquals(expectedLevel, result.level());
        assertEquals(expectedScore, result.score());
    }

    @Test
    void calculate_nullInput_returnsVeryWeak() {
        assertEquals(PasswordStrengthCalculator.Strength.VERY_WEAK, PasswordStrengthCalculator.calculate(null));
    }

    @Test
    void calculate_shortPassword_lowStrength() {
        // Less than 8 chars, only lowercase
        assertEquals(PasswordStrengthCalculator.Strength.VERY_WEAK, PasswordStrengthCalculator.calculate("abc"));
    }

    @Test
    void calculate_longMixedPassword_highStrength() {
        // At least 16 chars, upper, lower, digit, special, and repeats of categories
        String password = "Ab1!Cd2@Ef3#Gh4$";
        assertEquals(PasswordStrengthCalculator.Strength.VERY_STRONG, PasswordStrengthCalculator.calculate(password));
    }

    @Test
    void calculate_repetition_lowersUniquenessBonus() {
        // Long but lot of repeating chars
        String lowUnique = "aaaaaaaaaaaaaaaaAAAAA";
        
        PasswordStrengthCalculator.Strength result = PasswordStrengthCalculator.calculate(lowUnique);
        assertTrue(result.getLevel() < PasswordStrengthCalculator.Strength.STRONG.getLevel());
    }
}
