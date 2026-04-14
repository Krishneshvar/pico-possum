package com.possum.ui.auth;

import com.possum.infrastructure.filesystem.AppPaths;
import com.possum.infrastructure.serialization.JsonService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class SessionStore {
    
    private final Path sessionFile;
    private final JsonService jsonService;
    private String currentToken;

    public SessionStore(AppPaths appPaths, JsonService jsonService) {
        this.sessionFile = appPaths.getSettingsDir().resolve("session.json");
        this.jsonService = jsonService;
        loadSession();
    }

    public void saveSession(String token) {
        this.currentToken = token;
        try {
            SessionData data = new SessionData(token);
            String json = jsonService.toJson(data);
            Files.writeString(sessionFile, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session", e);
        }
    }

    public Optional<String> getToken() {
        return Optional.ofNullable(currentToken);
    }

    public void clearSession() {
        this.currentToken = null;
        try {
            Files.deleteIfExists(sessionFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to clear session", e);
        }
    }

    private void loadSession() {
        try {
            if (Files.exists(sessionFile)) {
                String json = Files.readString(sessionFile);
                SessionData data = jsonService.fromJson(json, SessionData.class);
                this.currentToken = data.token();
            }
        } catch (IOException e) {
            this.currentToken = null;
        }
    }

    private record SessionData(String token) {}
}
