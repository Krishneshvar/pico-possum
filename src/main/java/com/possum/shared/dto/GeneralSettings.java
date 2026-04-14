package com.possum.shared.dto;

public class GeneralSettings {

    private String storeName = "";
    private String language = "en";
    private String currencyCode = "INR";
    private String currencySymbol = "₹";
    private String dateFormat = "DD/MM/YYYY";
    private String timeFormat = "12 hour format";
    private String defaultPrinterName = "";
    private boolean inventoryAlertsAndRestrictionsEnabled = true;
    private boolean numericalSkuGenerationEnabled = false;

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

    public String getTimeFormat() {
        return timeFormat;
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    public String getDefaultPrinterName() {
        return defaultPrinterName;
    }

    public void setDefaultPrinterName(String defaultPrinterName) {
        this.defaultPrinterName = defaultPrinterName != null ? defaultPrinterName.trim() : "";
    }

    public boolean isInventoryAlertsAndRestrictionsEnabled() {
        return inventoryAlertsAndRestrictionsEnabled;
    }

    public void setInventoryAlertsAndRestrictionsEnabled(boolean inventoryAlertsAndRestrictionsEnabled) {
        this.inventoryAlertsAndRestrictionsEnabled = inventoryAlertsAndRestrictionsEnabled;
    }

    public boolean isNumericalSkuGenerationEnabled() {
        return numericalSkuGenerationEnabled;
    }

    public void setNumericalSkuGenerationEnabled(boolean numericalSkuGenerationEnabled) {
        this.numericalSkuGenerationEnabled = numericalSkuGenerationEnabled;
    }
}
