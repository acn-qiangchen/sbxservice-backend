package com.sbxservice.serviceb.model;

import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class BackendResponse {
    private String status;
    private String message;
    private long delayMs;
    private ZonedDateTime timestamp;
    private String serverInstance;
    // The ephemeral TCP source port of the caller (service-a's outgoing port).
    // Same port across two requests = same TCP connection was reused.
    // Different port = Apache HttpClient opened a new connection.
    private int clientPort;
}
