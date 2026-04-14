package com.possum.shared.util;

import java.util.regex.Pattern;

public final class DomainValidators {
    // Shared regex patterns for consistent validation across UI and domain
    public static final Pattern EMAIL = Pattern.compile("^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$");
    public static final Pattern PHONE = Pattern.compile("^[+]?[0-9\\s\\-().]{7,20}$");
    
    // Shared constants
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MIN_USERNAME_LENGTH = 3;

    private DomainValidators() {
        // Utility class
    }
}
