package com.possum.ui.sales;

import com.possum.application.auth.AuthContext;
import com.possum.application.auth.AuthUser;
import com.possum.application.sales.SalesService;
import com.possum.domain.model.LegacySale;
import com.possum.domain.model.PaymentMethod;
import com.possum.shared.util.CsvImportUtil;
import com.possum.shared.util.TimeUtil;
import com.possum.ui.common.controls.NotificationService;
import com.possum.ui.common.dialogs.ImportProgressDialog;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LegacySaleImporter {

    private final SalesService salesService;
    private final Window ownerWindow;
    private final Runnable onComplete;

    private static final DateTimeFormatter CSV_DATE_MDY = DateTimeFormatter.ofPattern("M/d/yyyy");
    private static final DateTimeFormatter CSV_DATE_DMY = DateTimeFormatter.ofPattern("d/M/yyyy");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public LegacySaleImporter(SalesService salesService, Window ownerWindow, Runnable onComplete) {
        this.salesService = salesService;
        this.ownerWindow = ownerWindow;
        this.onComplete = onComplete;
    }

    public void handleImport() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Legacy Bills (Serieswise CSV)");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File file = fileChooser.showOpenDialog(ownerWindow);
        if (file == null) {
            return;
        }

        AuthUser currentUser = AuthContext.getCurrentUser();
        if (currentUser == null) {
            NotificationService.error("No active user session found. Please sign in again and retry import.");
            return;
        }

        ImportProgressDialog progressDialog = new ImportProgressDialog(ownerWindow, "Import Legacy Bills");
        progressDialog.show();

        Task<ImportResult> importTask = new Task<>() {
            @Override
            protected ImportResult call() throws Exception {
                AuthContext.setCurrentUser(currentUser);
                try {
                    List<LegacySale> records = parseLegacySalesFromCsv(file.toPath(), file.getName());
                    progressDialog.setTotalRecords(records.size());

                    int processed = 0;
                    int imported = 0;
                    int skipped = 0;
                    for (LegacySale record : records) {
                        processed++;
                        try {
                            boolean saved = salesService.upsertLegacySale(record);
                            if (saved) {
                                imported++;
                            } else {
                                skipped++;
                            }
                        } catch (Exception ex) {
                            skipped++;
                        }
                        progressDialog.updateProgress(processed, imported);
                    }
                    return new ImportResult(records.size(), imported, skipped);
                } finally {
                    AuthContext.clear();
                }
            }
        };

        importTask.setOnSucceeded(event -> {
            ImportResult result = importTask.getValue();
            progressDialog.complete(result.totalRecords(), result.imported(), result.skipped());
            onComplete.run();
            if (result.skipped() == 0) {
                NotificationService.success("Imported " + result.imported() + " legacy bill(s) successfully.");
            } else {
                NotificationService.warning("Imported " + result.imported() + " legacy bill(s). " + result.skipped() + " row(s) skipped.");
            }
        });

        importTask.setOnFailed(event -> {
            Throwable ex = importTask.getException();
            String message = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            progressDialog.fail(message);
            NotificationService.error("Failed to import legacy bills: " + message);
        });

        Thread worker = new Thread(importTask, "legacy-sales-import-task");
        worker.setDaemon(true);
        worker.start();
    }

    private List<LegacySale> parseLegacySalesFromCsv(java.nio.file.Path filePath, String sourceFile) throws Exception {
        List<List<String>> rows = CsvImportUtil.readCsv(filePath);
        int headerIndex = CsvImportUtil.findHeaderRowIndex(rows, "Bill Date", "Bill Number", "Net amount");
        if (headerIndex < 0) {
            throw new IllegalArgumentException("Could not find legacy bill headers in CSV.");
        }

        Map<String, Integer> headers = CsvImportUtil.buildHeaderIndex(rows.get(headerIndex));
        Map<String, PaymentMethod> paymentMethodsByCanonicalName = new HashMap<>();
        for (PaymentMethod method : salesService.getPaymentMethods()) {
            if (method == null || method.name() == null || method.name().isBlank()) {
                continue;
            }
            paymentMethodsByCanonicalName.put(canonicalPaymentKey(method.name()), method);
            if (method.code() != null && !method.code().isBlank()) {
                paymentMethodsByCanonicalName.putIfAbsent(canonicalPaymentKey(method.code()), method);
            }
        }

        List<LegacySale> sales = new ArrayList<>();

        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (CsvImportUtil.isRowEmpty(row)) {
                continue;
            }

            String billNumber = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Bill Number"));
            if (billNumber == null || "Grand Total".equalsIgnoreCase(billNumber)) {
                continue;
            }

            String billDate = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Bill Date"));
            String billTime = CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Bill Time"));
            if (billDate == null) {
                continue;
            }

            LocalDate parsedDate = parseCsvDate(billDate);
            LocalTime parsedTime = parseCsvTime(billTime);
            LocalDateTime localDateTime = LocalDateTime.of(parsedDate, parsedTime);
            LocalDateTime utcDateTime = TimeUtil.toUTC(localDateTime);

            BigDecimal netAmount = CsvImportUtil.parseDecimal(
                    CsvImportUtil.getValue(row, headers, "Net amount", "Net Amount"),
                    BigDecimal.ZERO
            );
            if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
                netAmount = BigDecimal.ZERO;
            }

            String paymentMethodName = inferLegacyPaymentMethodName(billNumber);
            Long paymentMethodId = resolvePaymentMethodId(paymentMethodName, paymentMethodsByCanonicalName);

            sales.add(new LegacySale(
                    billNumber,
                    utcDateTime,
                    CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Customer Code")),
                    CsvImportUtil.emptyToNull(CsvImportUtil.getValue(row, headers, "Customer Name")),
                    netAmount,
                    paymentMethodId,
                    paymentMethodName,
                    sourceFile
            ));
        }
        return sales;
    }

    private String inferLegacyPaymentMethodName(String billNumber) {
        if (billNumber == null || billNumber.isBlank()) {
            return "Legacy Import";
        }
        char prefix = Character.toUpperCase(billNumber.trim().charAt(0));
        return switch (prefix) {
            case 'C', 'X' -> "Cash";
            case 'K' -> "Debit Card";
            default -> "Legacy Import";
        };
    }

    private Long resolvePaymentMethodId(String paymentMethodName, Map<String, PaymentMethod> paymentMethodsByCanonicalName) {
        if (paymentMethodName == null || paymentMethodName.isBlank() || paymentMethodsByCanonicalName.isEmpty()) {
            return null;
        }
        PaymentMethod method = paymentMethodsByCanonicalName.get(canonicalPaymentKey(paymentMethodName));
        return method != null ? method.id() : null;
    }

    private String canonicalPaymentKey(String value) {
        if (value == null) {
            return "";
        }
        return value
                .trim()
                .toLowerCase(Locale.ENGLISH)
                .replace('_', ' ')
                .replaceAll("\\s+", " ");
    }

    private LocalDate parseCsvDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate.trim(), CSV_DATE_MDY);
        } catch (DateTimeParseException ex) {
            return LocalDate.parse(rawDate.trim(), CSV_DATE_DMY);
        }
    }

    private LocalTime parseCsvTime(String rawTime) {
        if (rawTime == null || rawTime.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        return LocalTime.parse(rawTime.trim().toUpperCase(Locale.ENGLISH), CSV_TIME);
    }

    private record ImportResult(int totalRecords, int imported, int skipped) {}
}
