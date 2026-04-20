package com.picopossum.ui.reports;

import com.picopossum.application.reports.dto.BreakdownItem;
import com.picopossum.infrastructure.logging.LoggingConfig;
import com.picopossum.ui.common.controls.NotificationService;
import javafx.scene.control.Alert;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

public class SalesReportExporter {

    private final Window ownerWindow;
    private final Function<BreakdownItem, BigDecimal> grossSalesCalculator;
    private final Function<BreakdownItem, BigDecimal> netSalesCalculator;
    private final Function<BreakdownItem, Integer> transactionCalculator;
    private final Function<String, Boolean> columnVisibilityChecker;

    public SalesReportExporter(Window ownerWindow,
                               Function<BreakdownItem, BigDecimal> grossSalesCalculator,
                               Function<BreakdownItem, BigDecimal> netSalesCalculator,
                               Function<BreakdownItem, Integer> transactionCalculator,
                               Function<String, Boolean> columnVisibilityChecker) {
        this.ownerWindow = ownerWindow;
        this.grossSalesCalculator = grossSalesCalculator;
        this.netSalesCalculator = netSalesCalculator;
        this.transactionCalculator = transactionCalculator;
        this.columnVisibilityChecker = columnVisibilityChecker;
    }

    public void exportData(String extension, List<BreakdownItem> items) {
        if (items == null || items.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Data");
            alert.setHeaderText(null);
            alert.setContentText("There is no data to export for the selected filters.");
            alert.showAndWait();
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Sales Report");
        fileChooser.setInitialFileName("sales_report_" + LocalDate.now() + extension);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
            extension.equals(".csv") ? "CSV Files" : "Excel Files", "*" + extension));

        File file = fileChooser.showSaveDialog(ownerWindow);
        if (file == null) return;

        try {
            if (extension.equals(".csv")) {
                writeCsv(file, items);
            } else {
                writeExcel(file, items);
            }
            NotificationService.success("Report exported successfully to " + file.getName());
        } catch (Exception e) {
            LoggingConfig.getLogger().error("Failed to export sales report: {}", e.getMessage(), e);
            NotificationService.error("Export failed: " + com.picopossum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    private void writeCsv(File file, List<BreakdownItem> items) throws IOException {
        List<String> visibleCols = getVisibleColumns();
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println(String.join(",", visibleCols));
            for (BreakdownItem item : items) {
                if (item == null) continue;
                List<String> values = visibleCols.stream()
                    .map(col -> getColumnValueAsString(item, col))
                    .toList();
                writer.println(String.join(",", values));
            }
        }
    }

    private void writeExcel(File file, List<BreakdownItem> items) throws IOException {
        List<String> visibleCols = getVisibleColumns();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sales Report");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            for (int i = 0; i < visibleCols.size(); i++) {
                headerRow.createCell(i).setCellValue(visibleCols.get(i));
            }
            int rowIdx = 1;
            for (BreakdownItem item : items) {
                if (item == null) continue;
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < visibleCols.size(); i++) {
                    Object val = getColumnValue(item, visibleCols.get(i));
                    if (val instanceof Number n) {
                        row.createCell(i).setCellValue(n.doubleValue());
                    } else {
                        row.createCell(i).setCellValue(String.valueOf(val));
                    }
                }
            }
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
        }
    }

    private List<String> getVisibleColumns() {
        List<String> all = List.of("Period", "Transactions", "Cash", "UPI", "Card", "Gift Card", "Gross Sales", "Refunds", "Net Sales");
        return all.stream().filter(columnVisibilityChecker::apply).toList();
    }

    private String getColumnValueAsString(BreakdownItem item, String column) {
        Object val = getColumnValue(item, column);
        if (val instanceof BigDecimal bd) return String.format("%.2f", bd);
        return String.valueOf(val);
    }

    private Object getColumnValue(BreakdownItem item, String column) {
        return switch (column) {
            case "Period", "Date" -> item.name();
            case "Transactions" -> transactionCalculator.apply(item);
            case "Cash" -> item.cash();
            case "UPI" -> item.upi();
            case "Card" -> item.card();
            case "Gift Card" -> item.giftCard();
            case "Gross Sales" -> grossSalesCalculator.apply(item);
            case "Refunds" -> item.refunds();
            case "Net Sales" -> netSalesCalculator.apply(item);
            default -> "";
        };
    }
}
