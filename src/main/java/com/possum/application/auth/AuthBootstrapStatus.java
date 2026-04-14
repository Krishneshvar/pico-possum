package com.possum.application.auth;

public record AuthBootstrapStatus(
        boolean requiresInitialSetup,
        boolean requiresPasswordRotation
) {
}
