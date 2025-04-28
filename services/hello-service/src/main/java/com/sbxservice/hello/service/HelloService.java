package com.sbxservice.hello.service;

import com.sbxservice.hello.model.GreetingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

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
    
    /**
     * Creates a detailed greeting response with additional information.
     * 
     * @param name the name to greet (optional)
     * @param request the HTTP request
     * @return a detailed greeting response
     */
    public GreetingResponse generateDetailedGreeting(String name, HttpServletRequest request) {
        String greeting = generateGreeting(name);
        
        return GreetingResponse.builder()
                .message(greeting)
                .timestamp(ZonedDateTime.now())
                .userAgent(request.getHeader("User-Agent"))
                .requestHeaders(getHeadersMap(request))
                .serverInfo(collectServerInfo())
                .build();
    }
    
    /**
     * Collects information about the server.
     * 
     * @return server information
     */
    private GreetingResponse.ServerInfo collectServerInfo() {
        Runtime runtime = Runtime.getRuntime();
        String hostname;
        
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = "unknown";
        }
        
        return GreetingResponse.ServerInfo.builder()
                .hostname(hostname)
                .javaVersion(System.getProperty("java.version"))
                .osName(System.getProperty("os.name"))
                .osVersion(System.getProperty("os.version"))
                .freeMemory(runtime.freeMemory() / (1024 * 1024)) // MB
                .totalMemory(runtime.totalMemory() / (1024 * 1024)) // MB
                .build();
    }
    
    /**
     * Converts request headers to a map.
     * 
     * @param request the HTTP request
     * @return map of header names to values
     */
    private Map<String, String> getHeadersMap(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        request.getHeaderNames().asIterator().forEachRemaining(name -> 
            headers.put(name, request.getHeader(name))
        );
        return headers;
    }
} 