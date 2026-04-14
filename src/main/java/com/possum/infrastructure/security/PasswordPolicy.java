package com.possum.infrastructure.security;

public final class PasswordPolicy {
    
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;
    public static final int MIN_UPPERCASE = 1;
    public static final int MIN_LOWERCASE = 1;
    public static final int MIN_DIGITS = 1;
    public static final int MIN_SPECIAL_CHARS = 1;
    public static final int PASSWORD_HISTORY_SIZE = 5;
    public static final int PASSWORD_EXPIRY_DAYS = 90;
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int LOCKOUT_DURATION_MINUTES = 30;
    
    public static final String SPECIAL_CHARS = "!@#$%^&*()_+-=[]{}|;:,.<>?";
    
    private PasswordPolicy() {}
}
