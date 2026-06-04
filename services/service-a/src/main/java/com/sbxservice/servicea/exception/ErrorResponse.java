package com.sbxservice.servicea.exception;

import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int httpStatus;
    private String error;
    private String message;
    /** One of: TIMEOUT, UNREACHABLE, STALE_CONNECTION, UPSTREAM_ERROR */
    private String scenario;
    private String upstreamUrl;
    private ZonedDateTime timestamp;
}
