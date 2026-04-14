package com.possum.infrastructure.printing;

import com.possum.application.sales.dto.SaleResponse;
import com.possum.domain.model.Sale;
import com.possum.domain.model.SaleItem;
import com.possum.shared.dto.BillSection;
import com.possum.shared.dto.BillSettings;
import com.possum.shared.dto.GeneralSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BillRenderer {

    private static final String STYLES = """
      @import url('https://fonts.googleapis.com/css2?family=Roboto:wght@400;700&display=swap');
      body {
        margin: 0;
        padding: 0;
        font-family: 'Roboto', 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
        font-weight: bold;
        color: black;
        background: white;
      }
      .bill-container {
        padding: 5px;
        box-sizing: border-box;
      }
      .w-58mm { width: 58mm; }
      .w-80mm { width: 80mm; }
      
      .text-left { text-align: left; }
      .text-center { text-align: center; }
      .text-right { text-align: right; }
      
      .text-small { font-size: 11px; }
      .text-medium { font-size: 13px; }
      .text-large { font-size: 18px; }
      .store-name { font-size: 28px; }
      
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; vertical-align: top; padding: 2px 0; }
      
      .col-item { width: 40%; }
      .col-qty { width: 15%; text-align: center; }
      .col-rate { width: 20%; text-align: right; }
      .col-amount { width: 25%; text-align: right; }
      
      .divider { border-top: 1px solid black; margin: 5px 0; }
      .double-divider { border-top: 3px double black; margin: 5px 0; }
      
      .section { margin-bottom: 8px; }
      
      .header-container { display: flex; align-items: center; justify-content: center; gap: 10px; }
      .header-logo { max-width: 50px; max-height: 50px; object-fit: contain; }
      .header-content { flex: 1; }
      
      .footer-text { white-space: pre-wrap; }
    """;

    public static String renderBill(SaleResponse saleResponse, GeneralSettings general, BillSettings billSettings) {
        if (saleResponse == null || saleResponse.sale() == null) {
            throw new IllegalArgumentException("saleResponse must contain a sale");
        }
        if (general == null) {
            general = new GeneralSettings();
        }
        if (billSettings == null) {
            billSettings = new BillSettings();
        }

        Sale sale = saleResponse.sale();
        String paperWidth = billSettings.getPaperWidth();
        String currency = billSettings.getCurrency() != null && !billSettings.getCurrency().isEmpty() 
            ? billSettings.getCurrency() 
            : (general.getCurrencySymbol() != null && !general.getCurrencySymbol().isBlank()
                ? general.getCurrencySymbol()
                : (general.getCurrencyCode().equals("INR") ? "₹" : "$"));
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>")
            .append(STYLES)
            .append("</style></head><body>");
        
        html.append("<div class=\"bill-container w-").append(paperWidth).append("\">");

        List<BillSection> visibleSections = billSettings.getSections().stream()
            .filter(BillSection::isVisible)
            .collect(Collectors.toList());

        for (BillSection section : visibleSections) {
            html.append(renderSection(section, saleResponse, billSettings, currency));
        }

        html.append("</div></body></html>");
        
        return html.toString();
    }

    private static String renderSection(BillSection section, SaleResponse saleResponse, 
                                       BillSettings billSettings, String currency) {
        Sale sale = saleResponse.sale();
        String alignment = section.getOptionAsString("alignment", "left");
        String fontSize = section.getOptionAsString("fontSize", "medium");
        String alignClass = "text-" + alignment;
        String sizeClass = "text-" + fontSize;
        String commonClasses = "section " + alignClass + " " + sizeClass;

        switch (section.getType()) {
            case HEADER:
                return renderHeader(section, commonClasses);
            case META:
                return renderMeta(section, sale, billSettings, commonClasses);
            case ITEMS:
                return renderItems(section, saleResponse, currency, commonClasses);
            case TOTALS:
                return renderTotals(section, sale, currency, commonClasses);
            case FOOTER:
                return renderFooter(section, commonClasses);
            default:
                return "";
        }
    }

    private static String renderHeader(BillSection section, String commonClasses) {
        boolean showLogo = section.getOptionAsBoolean("showLogo", false);
        String logoUrl = section.getOptionAsString("logoUrl", "");
        String storeName = section.getOptionAsString("storeName", "Store Name");
        String storeDetails = section.getOptionAsString("storeDetails", "");
        String phone = section.getOptionAsString("phone", "");
        String gst = section.getOptionAsString("gst", "");
        String alignment = section.getOptionAsString("alignment", "center");

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"").append(commonClasses).append("\">")
            .append("<div class=\"header-container\" style=\"justify-content: ")
            .append(alignment.equals("center") ? "center" : alignment.equals("right") ? "flex-end" : "flex-start")
            .append(";\">");

        if (showLogo && !logoUrl.isEmpty()) {
            html.append("<img src=\"").append(escapeHtml(logoUrl)).append("\" class=\"header-logo\" />");
        }

        html.append("<div class=\"header-content\">");
        html.append("<div class=\"store-name\">").append(escapeHtml(storeName)).append("</div>");
        html.append("</div></div>");

        html.append("<div class=\"text-small\" style=\"margin-top: 4px;\">");
        if (!storeDetails.isEmpty()) {
            html.append("<div style=\"white-space: pre-wrap;\">").append(escapeHtml(storeDetails)).append("</div>");
        }
        if (!phone.isEmpty()) {
            html.append("<div><span class=\"bold\">Ph No:</span> ").append(escapeHtml(phone)).append("</div>");
        }
        if (!gst.isEmpty()) {
            html.append("<div><span class=\"bold\">GSTIN:</span> ").append(escapeHtml(gst)).append("</div>");
        }
        html.append("</div></div><div class=\"divider\"></div>");

        return html.toString();
    }

    private static String renderMeta(BillSection section, Sale sale, BillSettings billSettings, String commonClasses) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"").append(commonClasses).append("\">")
            .append("<div><span class=\"bold\">Bill No:</span> ").append(escapeHtml(sale.shortInvoiceNumber())).append("</div>")
            .append("<div><span class=\"bold\">Date:</span> ").append(formatDate(sale.saleDate(), billSettings)).append("</div>");

        if (sale.billerName() != null) {
            html.append("<div><span class=\"bold\">User:</span> ").append(escapeHtml(sale.billerName())).append("</div>");
        }
        if (sale.customerName() != null) {
            html.append("<div><span class=\"bold\">Customer:</span> ").append(escapeHtml(sale.customerName())).append("</div>");
        }

        html.append("</div><div class=\"divider\"></div>");
        return html.toString();
    }

    private static String renderItems(BillSection section, SaleResponse saleResponse, String currency, String commonClasses) {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"").append(commonClasses).append("\">")
            .append("<table><thead><tr class=\"text-small bold\" style=\"border-bottom: 1px solid black;\">");
        html.append("<th class=\"col-item\">Item</th>")
            .append("<th class=\"col-qty\" style=\"text-align: center;\">Qty</th>")
            .append("<th class=\"col-rate\" style=\"text-align: right;\">Rate</th>")
            .append("<th class=\"col-amount\" style=\"text-align: right;\">Amt</th>")
            .append("</tr></thead><tbody>");

        List<SaleItem> items = saleResponse.items() != null ? saleResponse.items() : Collections.emptyList();
        for (SaleItem item : items) {
            BigDecimal qty = BigDecimal.valueOf(item.quantity());
            BigDecimal rate = item.pricePerUnit();
            BigDecimal amount = rate.multiply(qty);
            
            String itemName = item.productName();
            if (item.variantName() != null && !item.variantName().equals("Default")) {
                itemName += " (" + item.variantName() + ")";
            }
            
            html.append("<tr>")
                .append("<td class=\"col-item\">").append(escapeHtml(itemName)).append("</td>")
                .append("<td class=\"col-qty\">").append(item.quantity()).append("</td>")
                .append("<td class=\"col-rate\">").append(formatAmountOnly(rate)).append("</td>")
                .append("<td class=\"col-amount\">").append(formatAmountOnly(amount)).append("</td>")
                .append("</tr>");
        }
        html.append("</tbody></table></div><div class=\"divider\"></div>");
        return html.toString();
    }

    private static String renderTotals(BillSection section, Sale sale, String currency, String commonClasses) {
        BigDecimal discount = sale.discount() != null ? sale.discount() : BigDecimal.ZERO;
        BigDecimal tax = sale.totalTax() != null ? sale.totalTax() : BigDecimal.ZERO;
        BigDecimal total = sale.totalAmount();
        BigDecimal subtotal = total.subtract(tax).add(discount);

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"").append(commonClasses).append("\">")
            .append("<table style=\"width: 100%\">")
            .append("<tr><td>Subtotal:</td><td class=\"text-right\">").append(formatCurrency(subtotal, currency)).append("</td></tr>");

        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Tax:</td><td class=\"text-right\">").append(formatCurrency(tax, currency)).append("</td></tr>");
        }
        if (discount.compareTo(BigDecimal.ZERO) > 0) {
            html.append("<tr><td>Discount:</td><td class=\"text-right\">-").append(formatCurrency(discount, currency)).append("</td></tr>");
        }

        html.append("<tr class=\"text-large\" style=\"border-top: 1px solid black;\">")
            .append("<td style=\"padding-top: 5px;\">Total:</td>")
            .append("<td class=\"text-right\" style=\"padding-top: 5px;\">").append(formatCurrency(total, currency)).append("</td>")
            .append("</tr></table></div><div class=\"divider\"></div>");

        return html.toString();
    }

    private static String renderFooter(BillSection section, String commonClasses) {
        String text = section.getOptionAsString("text", "Thank you!");
        return "<div class=\"" + commonClasses + " footer-text\">" + escapeHtml(text) + "</div>";
    }

    private static String formatDate(LocalDateTime dateTime, BillSettings billSettings) {
        LocalDateTime localTime = com.possum.shared.util.TimeUtil.toLocal(dateTime);
        if (localTime == null) {
            return "";
        }

        String datePattern = switch (billSettings.getDateFormat()) {
            case "ISO" -> "yyyy-MM-dd";
            case "short" -> "dd/MM/yy";
            case "long" -> "EEEE, dd MMMM yyyy";
            default -> "dd/MM/yyyy";
        };
        String timePattern = "24h".equals(billSettings.getTimeFormat()) ? "HH:mm" : "hh:mm a";

        return localTime.format(DateTimeFormatter.ofPattern(datePattern + " " + timePattern));
    }

    private static String formatCurrency(BigDecimal amount, String currency) {
        if (amount == null) return currency + "0.00";
        return currency + amount.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private static String formatAmountOnly(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, RoundingMode.HALF_UP).toString();
    }

    private static String escapeHtml(String unsafe) {
        if (unsafe == null) return "";
        return unsafe
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#039;");
    }
}
