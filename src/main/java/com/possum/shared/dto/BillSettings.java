package com.possum.shared.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillSettings {
    private String paperWidth = "80mm";
    private String dateFormat = "standard";
    private String timeFormat = "12h";
    private String currency = "₹";
    private List<BillSection> sections = new ArrayList<>();

    public BillSettings() {
        initializeDefaultSections();
    }

    private void initializeDefaultSections() {
        BillSection header = new BillSection("storeHeader", BillSection.SectionType.HEADER, true);
        Map<String, Object> headerOpts = new HashMap<>();
        headerOpts.put("alignment", "center");
        headerOpts.put("fontSize", "medium");
        headerOpts.put("showLogo", false);
        headerOpts.put("logoUrl", "");
        headerOpts.put("storeName", "POS Store Demo");
        headerOpts.put("storeDetails", "123 Main St, Tech City");
        headerOpts.put("phone", "555-0123");
        headerOpts.put("gst", "22AAAAA0000A1Z5");
        header.setOptions(headerOpts);
        sections.add(header);

        BillSection meta = new BillSection("billMeta", BillSection.SectionType.META, true);
        Map<String, Object> metaOpts = new HashMap<>();
        metaOpts.put("alignment", "left");
        metaOpts.put("fontSize", "small");
        meta.setOptions(metaOpts);
        sections.add(meta);

        BillSection items = new BillSection("itemsTable", BillSection.SectionType.ITEMS, true);
        Map<String, Object> itemsOpts = new HashMap<>();
        itemsOpts.put("fontSize", "medium");
        itemsOpts.put("showTax", false);
        items.setOptions(itemsOpts);
        sections.add(items);

        BillSection totals = new BillSection("totals", BillSection.SectionType.TOTALS, true);
        Map<String, Object> totalsOpts = new HashMap<>();
        totalsOpts.put("alignment", "right");
        totalsOpts.put("fontSize", "medium");
        totals.setOptions(totalsOpts);
        sections.add(totals);

        BillSection footer = new BillSection("footer", BillSection.SectionType.FOOTER, true);
        Map<String, Object> footerOpts = new HashMap<>();
        footerOpts.put("alignment", "center");
        footerOpts.put("fontSize", "small");
        footerOpts.put("text", "Thank you for your visit!");
        footer.setOptions(footerOpts);
        sections.add(footer);
    }

    public String getPaperWidth() {
        return normalizePaperWidth(paperWidth);
    }

    public void setPaperWidth(String paperWidth) {
        this.paperWidth = normalizePaperWidth(paperWidth);
    }

    public String getDateFormat() {
        return dateFormat != null ? dateFormat : "standard";
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat != null ? dateFormat : "standard";
    }

    public String getTimeFormat() {
        return timeFormat != null ? timeFormat : "12h";
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat != null ? timeFormat : "12h";
    }

    public String getCurrency() {
        return currency != null ? currency : "₹";
    }

    public void setCurrency(String currency) {
        this.currency = currency != null ? currency : "₹";
    }

    public List<BillSection> getSections() {
        if (sections == null || sections.isEmpty()) {
            sections = new ArrayList<>();
            initializeDefaultSections();
        }
        return sections;
    }

    public void setSections(List<BillSection> sections) {
        this.sections = sections != null ? sections : new ArrayList<>();
        if (this.sections.isEmpty()) {
            initializeDefaultSections();
        }
    }

    private String normalizePaperWidth(String input) {
        if (input != null && "58mm".equalsIgnoreCase(input.trim())) {
            return "58mm";
        }
        return "80mm";
    }
}
