package com.ecovoice.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DatabaseConfig {

    @PostConstruct
    public void ensureDataDirectory() throws IOException {
        Files.createDirectories(Path.of("data"));
    }
}
