package com.possum.infrastructure.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class PasswordValidator {
    
    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[" + Pattern.quote(PasswordPolicy.SPECIAL_CHARS) + "]");
    
    public static ValidationResult validate(String password) {
        List<String> errors = new ArrayList<>();
        
        if (password == null || password.isEmpty()) {
            errors.add("Password cannot be empty");
            return new ValidationResult(false, errors);
        }
        
        if (password.length() < PasswordPolicy.MIN_LENGTH) {
            errors.add("Password must be at least " + PasswordPolicy.MIN_LENGTH + " characters long");
        }
        
        if (password.length() > PasswordPolicy.MAX_LENGTH) {
            errors.add("Password must not exceed " + PasswordPolicy.MAX_LENGTH + " characters");
        }
        
        if (countMatches(UPPERCASE_PATTERN, password) < PasswordPolicy.MIN_UPPERCASE) {
            errors.add("Password must contain at least " + PasswordPolicy.MIN_UPPERCASE + " uppercase letter(s)");
        }
        
        if (countMatches(LOWERCASE_PATTERN, password) < PasswordPolicy.MIN_LOWERCASE) {
            errors.add("Password must contain at least " + PasswordPolicy.MIN_LOWERCASE + " lowercase letter(s)");
        }
        
        if (countMatches(DIGIT_PATTERN, password) < PasswordPolicy.MIN_DIGITS) {
            errors.add("Password must contain at least " + PasswordPolicy.MIN_DIGITS + " digit(s)");
        }
        
        if (countMatches(SPECIAL_CHAR_PATTERN, password) < PasswordPolicy.MIN_SPECIAL_CHARS) {
            errors.add("Password must contain at least " + PasswordPolicy.MIN_SPECIAL_CHARS + " special character(s) from: " + PasswordPolicy.SPECIAL_CHARS);
        }
        
        if (containsCommonPatterns(password)) {
            errors.add("Password contains common patterns and is too weak");
        }
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    private static int countMatches(Pattern pattern, String text) {
        return (int) pattern.matcher(text).results().count();
    }
    
    private static boolean containsCommonPatterns(String password) {
        String lower = password.toLowerCase();
        String[] commonPatterns = {
            "password", "123456", "qwerty", "admin", "letmein", 
            "welcome", "monkey", "dragon", "master", "abc123"
        };
        
        for (String pattern : commonPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        
        if (hasSequentialChars(password, 4)) {
            return true;
        }
        
        if (hasRepeatingChars(password, 4)) {
            return true;
        }
        
        return false;
    }
    
    private static boolean hasSequentialChars(String password, int length) {
        for (int i = 0; i <= password.length() - length; i++) {
            boolean sequential = true;
            for (int j = 0; j < length - 1; j++) {
                if (password.charAt(i + j + 1) != password.charAt(i + j) + 1) {
                    sequential = false;
                    break;
                }
            }
            if (sequential) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean hasRepeatingChars(String password, int length) {
        for (int i = 0; i <= password.length() - length; i++) {
            char c = password.charAt(i);
            boolean repeating = true;
            for (int j = 1; j < length; j++) {
                if (password.charAt(i + j) != c) {
                    repeating = false;
                    break;
                }
            }
            if (repeating) {
                return true;
            }
        }
        return false;
    }
    
    public static record ValidationResult(boolean valid, List<String> errors) {}
}
