package com.possum.infrastructure.printing;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class PrinterService {

    private static final double WIDTH_58MM_POINTS = 164.4;
    private static final double WIDTH_80MM_POINTS = 226.8;
    private static final double PAPER_WIDTH_TOLERANCE = 12.0;
    private static final long PRINT_PREP_TIMEOUT_SECONDS = 30;

    public List<String> listPrinters() {
        return Printer.getAllPrinters().stream()
                .map(Printer::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public String getDefaultPrinterName() {
        Printer printer = Printer.getDefaultPrinter();
        return printer != null ? printer.getName() : null;
    }

    public CompletableFuture<Boolean> printInvoice(String htmlContent) {
        return printInvoiceDetailed(htmlContent).thenApply(PrintOutcome::success);
    }

    public CompletableFuture<Boolean> printInvoice(String htmlContent, String printerName) {
        return printInvoiceDetailed(htmlContent, printerName).thenApply(PrintOutcome::success);
    }

    public CompletableFuture<Boolean> printInvoice(String htmlContent, String printerName, String paperWidth) {
        return printInvoiceDetailed(htmlContent, printerName, paperWidth).thenApply(PrintOutcome::success);
    }

    public CompletableFuture<PrintOutcome> printInvoiceDetailed(String htmlContent) {
        return printInvoiceDetailed(htmlContent, null, "80mm");
    }

    public CompletableFuture<PrintOutcome> printInvoiceDetailed(String htmlContent, String printerName) {
        return printInvoiceDetailed(htmlContent, printerName, "80mm");
    }

    public CompletableFuture<PrintOutcome> printInvoiceDetailed(String htmlContent, String printerName, String paperWidth) {
        Objects.requireNonNull(htmlContent, "htmlContent must not be null");

        String normalizedPaperWidth = normalizePaperWidth(paperWidth);
        CompletableFuture<PrintOutcome> future = new CompletableFuture<>();

        Platform.runLater(() -> {
            ResolvedPrinter resolved = resolvePrinter(printerName);
            if (resolved.printer() == null) {
                future.complete(new PrintOutcome(false, resolved.failureMessage(), null));
                return;
            }

            try {
                WebView webView = new WebView();
                WebEngine engine = webView.getEngine();

                ChangeListener<Worker.State> listener = new ChangeListener<>() {
                    @Override
                    public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs,
                                        Worker.State oldState, Worker.State newState) {
                        if (newState == Worker.State.SUCCEEDED) {
                            engine.getLoadWorker().stateProperty().removeListener(this);
                            future.complete(runPrintJob(webView, engine, resolved.printer(), resolved.resolvedName(), normalizedPaperWidth));
                        } else if (newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                            engine.getLoadWorker().stateProperty().removeListener(this);
                            Throwable cause = engine.getLoadWorker().getException();
                            String reason = rootMessage(cause);
                            if (reason == null || reason.isBlank()) {
                                reason = "Failed to prepare the invoice content for printing.";
                            }
                            future.complete(new PrintOutcome(false, reason, resolved.resolvedName()));
                        }
                    }
                };

                engine.getLoadWorker().stateProperty().addListener(listener);
                engine.loadContent(htmlContent);
            } catch (Exception ex) {
                future.complete(new PrintOutcome(false, rootMessage(ex), resolved.resolvedName()));
            }
        });

        return future.completeOnTimeout(
                new PrintOutcome(false, "Printing timed out while preparing the receipt.", null),
                PRINT_PREP_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private PrintOutcome runPrintJob(WebView webView, WebEngine engine, Printer printer, String printerName, String paperWidth) {
        try {
            PrinterJob job = PrinterJob.createPrinterJob(printer);
            if (job == null) {
                return new PrintOutcome(false, "Unable to create a print job for the selected printer.", printerName);
            }

            PageLayout pageLayout = createPageLayout(printer, paperWidth);
            job.getJobSettings().setPageLayout(pageLayout);
            job.getJobSettings().setJobName("POSSUM Receipt");

            engine.print(job);
            boolean completed = job.endJob();
            if (completed) {
                return new PrintOutcome(true, "Invoice sent to printer.", printerName);
            }
            return new PrintOutcome(false, "The printer did not accept the job.", printerName);
        } catch (Exception ex) {
            return new PrintOutcome(false, rootMessage(ex), printerName);
        }
    }



    private PageLayout createPageLayout(Printer printer, String paperWidth) {
        double targetWidth = "58mm".equals(paperWidth) ? WIDTH_58MM_POINTS : WIDTH_80MM_POINTS;

        Paper bestPaper = printer.getPrinterAttributes().getSupportedPapers().stream()
                .min(Comparator.comparingDouble(p -> Math.abs(p.getWidth() - targetWidth)))
                .filter(paper -> Math.abs(paper.getWidth() - targetWidth) <= PAPER_WIDTH_TOLERANCE)
                .orElse(printer.getPrinterAttributes().getDefaultPaper());

        return printer.createPageLayout(
                bestPaper,
                PageOrientation.PORTRAIT,
                0,
                0,
                0,
                0
        );
    }

    private ResolvedPrinter resolvePrinter(String preferredPrinterName) {
        String requested = preferredPrinterName != null ? preferredPrinterName.trim() : "";
        if (!requested.isBlank()) {
            Printer selected = findPrinterByName(requested);
            if (selected == null) {
                return new ResolvedPrinter(
                        null,
                        null,
                        "Configured printer '" + requested + "' is unavailable. Select another printer in Settings."
                );
            }
            return new ResolvedPrinter(selected, selected.getName(), null);
        }

        Printer systemDefault = Printer.getDefaultPrinter();
        if (systemDefault != null) {
            return new ResolvedPrinter(systemDefault, systemDefault.getName(), null);
        }

        Printer firstAvailable = Printer.getAllPrinters().stream().findFirst().orElse(null);
        if (firstAvailable != null) {
            return new ResolvedPrinter(firstAvailable, firstAvailable.getName(), null);
        }

        return new ResolvedPrinter(null, null, "No printers detected. Connect a printer and refresh printer settings.");
    }

    private Printer findPrinterByName(String name) {
        return Printer.getAllPrinters().stream()
                .filter(printer -> printer.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    private String normalizePaperWidth(String paperWidth) {
        return "58mm".equalsIgnoreCase(paperWidth) ? "58mm" : "80mm";
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown printing error.";
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            message = throwable.getMessage();
        }
        if (message == null || message.isBlank()) {
            return "Unknown printing error.";
        }
        return message;
    }

    private record ResolvedPrinter(Printer printer, String resolvedName, String failureMessage) {
    }
}
