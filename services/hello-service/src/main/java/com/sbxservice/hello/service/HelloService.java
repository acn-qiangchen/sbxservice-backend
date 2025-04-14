package com.sbxservice.hello.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service class that handles the greeting logic.
 */
@Service
public class HelloService {

    /**
     * Default greeting message injected from application.yml.
     */
    @Value("${app.greeting.default-message}")
    private String defaultGreeting;

    /**
     * Generates a greeting message for the provided name.
     * If name is null or empty, returns the default greeting.
     *
     * @param name the name to greet (optional)
     * @return a greeting message
     */
    public String generateGreeting(String name) {
        if (name == null || name.trim().isEmpty()) {
            return defaultGreeting;
        }
        return "Hello, " + name.trim() + "!";
    }
} 