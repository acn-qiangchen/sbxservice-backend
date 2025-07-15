package com.sbxservice.hello.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.Builder;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * Model class representing a greeting response with detailed information.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class GreetingResponse {
    
    /**
     * The greeting message to be returned to the client.
     */
    private String message;
    
    /**
     * The timestamp when the request was processed.
     */
    private ZonedDateTime timestamp;
    
    /**
     * The client's user agent.
     */
    private String userAgent;
    
    /**
     * A map of all request headers.
     */
    private Map<String, String> requestHeaders;
    
    /**
     * Information about the server processing the request.
     */
    private ServerInfo serverInfo;
    
    /**
     * Constructor for backward compatibility.
     * 
     * @param message The greeting message
     */
    public GreetingResponse(String message) {
        this.message = message;
        this.timestamp = ZonedDateTime.now();
    }
    
    /**
     * Nested class containing server information.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ServerInfo {
        private String hostname;
        private String javaVersion;
        private String osName;
        private String osVersion;
        private long freeMemory;
        private long totalMemory;
    }
} 