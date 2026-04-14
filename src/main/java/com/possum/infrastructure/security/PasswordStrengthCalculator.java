package com.possum.infrastructure.security;

public final class PasswordStrengthCalculator {
    
    public enum Strength {
        VERY_WEAK(0, "Very Weak"),
        WEAK(1, "Weak"),
        MODERATE(2, "Moderate"),
        STRONG(3, "Strong"),
        VERY_STRONG(4, "Very Strong");
        
        private final int level;
        private final String label;
        
        Strength(int level, String label) {
            this.level = level;
            this.label = label;
        }
        
        public int getLevel() {
            return level;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    public static Strength calculate(String password) {
        if (password == null || password.isEmpty()) {
            return Strength.VERY_WEAK;
        }
        
        int score = 0;
        
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;
        
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*")) score++;
        
        if (password.matches(".*[A-Z].*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*[0-9].*")) score++;
        if (password.matches(".*[^A-Za-z0-9].*[^A-Za-z0-9].*")) score++;
        
        long uniqueChars = password.chars().distinct().count();
        if (uniqueChars >= password.length() * 0.7) score++;
        
        if (score <= 3) return Strength.VERY_WEAK;
        if (score <= 5) return Strength.WEAK;
        if (score <= 7) return Strength.MODERATE;
        if (score <= 9) return Strength.STRONG;
        return Strength.VERY_STRONG;
    }
    
    public static StrengthResult calculateStrength(String password) {
        Strength strength = calculate(password);
        int score = calculateScore(password);
        return new StrengthResult(strength, score);
    }
    
    private static int calculateScore(String password) {
        if (password == null || password.isEmpty()) return 0;
        Strength strength = calculate(password);
        return strength.getLevel() * 25;
    }
    
    public record StrengthResult(Strength level, int score) {}
}
