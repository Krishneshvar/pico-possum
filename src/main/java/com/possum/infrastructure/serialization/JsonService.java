package com.possum.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class JsonService {

    private final ObjectMapper objectMapper;

    public JsonService() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        objectMapper.registerModule(new com.fasterxml.jackson.module.paramnames.ParameterNamesModule());
        objectMapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public <T> T read(Path path, Class<T> type) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(type, "type must not be null");

        if (Files.notExists(path)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return objectMapper.readValue(reader, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new com.possum.domain.exceptions.DataCorruptionException("JSON corruption detected in file: " + path, ex);
        } catch (IOException ex) {
            throw new IllegalStateException("System IO error while reading file: " + path, ex);
        }
    }

    public void write(Path path, Object value) {
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(value, "value must not be null");

        try {
            Path parent = path.getParent();
            if (parent != null && Files.notExists(parent)) {
                Files.createDirectories(parent);
            }

            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(writer, value);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write JSON file: " + path, ex);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize to JSON", ex);
        }
    }

    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to deserialize from JSON", ex);
        }
    }
}
