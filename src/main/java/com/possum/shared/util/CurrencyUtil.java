package com.possum.shared.util;

import com.possum.infrastructure.filesystem.SettingsStore;
import com.possum.shared.dto.GeneralSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility for unified currency formatting based on application settings.
 */
public final class CurrencyUtil {

    private static SettingsStore settingsStore;
    private static String cachedSymbol = null;

    private CurrencyUtil() {
    }

    public static void initialize(SettingsStore store) {
        settingsStore = store;
        refreshCache();
    }

    public static void refreshCache() {
        if (settingsStore != null) {
            try {
                GeneralSettings settings = settingsStore.loadGeneralSettings();
                cachedSymbol = settings.getCurrencySymbol();
            } catch (Exception e) {
                cachedSymbol = "₹"; // Fallback
            }
        }
    }

    public static String getSymbol() {
        if (cachedSymbol == null) {
            refreshCache();
        }
        return cachedSymbol != null ? cachedSymbol : "₹";
    }

    /**
     * Formats an amount with the currency symbol from settings.
     * Example: ₹ 1,234.56
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return getSymbol() + " 0.00";
        }
        
        // Use a standard number format for the numeric part
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        
        return getSymbol() + " " + nf.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Formats an amount without the symbol.
     */
    public static String formatPlain(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}
