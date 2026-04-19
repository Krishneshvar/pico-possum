package com.picopossum.infrastructure.printing;

public record PrintOutcome(boolean success, String message, String printerName) {
}
