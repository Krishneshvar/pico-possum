package com.possum.infrastructure.printing;

public record PrintOutcome(boolean success, String message, String printerName) {
}
