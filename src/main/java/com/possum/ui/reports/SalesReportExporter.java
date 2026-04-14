package com.possum.ui.reports;

import com.possum.application.reports.dto.BreakdownItem;
import com.possum.infrastructure.logging.LoggingConfig;
import com.possum.ui.common.controls.NotificationService;
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
    private final Function<String, Boolean> columnVisibilityChecker;

    public SalesReportExporter(Window ownerWindow,
                               Function<BreakdownItem, BigDecimal> grossSalesCalculator,
                               Function<BreakdownItem, BigDecimal> netSalesCalculator,
                               Function<String, Boolean> columnVisibilityChecker) {
        this.ownerWindow = ownerWindow;
        this.grossSalesCalculator = grossSalesCalculator;
        this.netSalesCalculator = netSalesCalculator;
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
            NotificationService.error("Export failed: " + com.possum.ui.common.ErrorHandler.toUserMessage(e));
        }
    }

    private void writeCsv(File file, List<BreakdownItem> items) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("Period,Transactions,Cash,UPI,Debit Card,Credit Card,Gift Card,Gross Sales,Refunds,Net Sales");
            for (BreakdownItem item : items) {
                if (item == null) continue;
                writer.printf("%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                    item.name(), item.totalTransactions(), 
                    columnVisibilityChecker.apply("Cash") ? item.cash() : BigDecimal.ZERO,
                    columnVisibilityChecker.apply("UPI") ? item.upi() : BigDecimal.ZERO,
                    columnVisibilityChecker.apply("Debit Card") ? item.debitCard() : BigDecimal.ZERO,
                    columnVisibilityChecker.apply("Credit Card") ? item.creditCard() : BigDecimal.ZERO,
                    columnVisibilityChecker.apply("Gift Card") ? item.giftCard() : BigDecimal.ZERO,
                    grossSalesCalculator.apply(item),
                    item.refunds(),
                    netSalesCalculator.apply(item));
            }
        }
    }

    private void writeExcel(File file, List<BreakdownItem> items) throws IOException {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sales Report");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"Period", "Transactions", "Cash", "UPI", "Debit Card", "Credit Card", "Gift Card", "Gross Sales", "Refunds", "Net Sales"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            int rowIdx = 1;
            for (BreakdownItem item : items) {
                if (item == null) continue;
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(item.name());
                row.createCell(1).setCellValue(item.totalTransactions());
                row.createCell(2).setCellValue((columnVisibilityChecker.apply("Cash") ? item.cash() : BigDecimal.ZERO).doubleValue());
                row.createCell(3).setCellValue((columnVisibilityChecker.apply("UPI") ? item.upi() : BigDecimal.ZERO).doubleValue());
                row.createCell(4).setCellValue((columnVisibilityChecker.apply("Debit Card") ? item.debitCard() : BigDecimal.ZERO).doubleValue());
                row.createCell(5).setCellValue((columnVisibilityChecker.apply("Credit Card") ? item.creditCard() : BigDecimal.ZERO).doubleValue());
                row.createCell(6).setCellValue((columnVisibilityChecker.apply("Gift Card") ? item.giftCard() : BigDecimal.ZERO).doubleValue());
                row.createCell(7).setCellValue(grossSalesCalculator.apply(item).doubleValue());
                row.createCell(8).setCellValue((columnVisibilityChecker.apply("Refunds") ? item.refunds() : BigDecimal.ZERO).doubleValue());
                row.createCell(9).setCellValue(netSalesCalculator.apply(item).doubleValue());
            }
            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                workbook.write(fileOut);
            }
        }
    }
}
