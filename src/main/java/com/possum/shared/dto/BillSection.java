package com.possum.shared.dto;

import java.util.HashMap;
import java.util.Map;

public class BillSection {
    private String id;
    private SectionType type;
    private boolean visible;
    private Map<String, Object> options;

    public enum SectionType {
        HEADER, META, ITEMS, TOTALS, FOOTER
    }

    public BillSection() {
        this.options = new HashMap<>();
    }

    public BillSection(String id, SectionType type, boolean visible) {
        this.id = id;
        this.type = type;
        this.visible = visible;
        this.options = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SectionType getType() {
        return type;
    }

    public void setType(SectionType type) {
        this.type = type;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public Object getOption(String key) {
        return options.get(key);
    }

    public String getOptionAsString(String key, String defaultValue) {
        Object value = options.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    public boolean getOptionAsBoolean(String key, boolean defaultValue) {
        Object value = options.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public void setOption(String key, Object value) {
        options.put(key, value);
    }
}
