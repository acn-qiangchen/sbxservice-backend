package com.sbxservice.servicea.exception;

import org.apache.hc.core5.http.NoHttpResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.ZonedDateTime;

/**
 * Maps upstream HTTP client exceptions to the appropriate gateway error codes.
 *
 * Exception → HTTP status mapping:
 *   SocketTimeoutException       → 504 Gateway Timeout    (read timeout exceeded)
 *   ConnectException             → 503 Service Unavailable (cannot reach service-b)
 *   NoHttpResponseException      → 502 Bad Gateway        (stale keep-alive connection)
 *   other ResourceAccessException → 502 Bad Gateway       (generic upstream I/O error)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${app.http-client.base-url}")
    private String baseUrl;

    @Value("${app.http-client.read-timeout-ms}")
    private int readTimeoutMs;

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleResourceAccess(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        String causeType = cause == null ? "null" : cause.getClass().getSimpleName();
        log.error("Upstream call to {} failed — cause: {} — {}", baseUrl, causeType, ex.getMessage());

        if (cause instanceof SocketTimeoutException) {
            return build(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "504 Gateway Timeout",
                    "service-b did not respond within " + readTimeoutMs + "ms. " +
                    "Increase app.http-client.read-timeout-ms or decrease the delay in service-b's /api/slow.",
                    "TIMEOUT"
            );
        }

        // ConnectException (connection refused) or SocketException without "reset" in the message
        // covers the case where the container is stopped and Docker/Colima surfaces a "System error"
        if (cause instanceof ConnectException
                || (cause instanceof SocketException
                    && (cause.getMessage() == null
                        || !cause.getMessage().toLowerCase().contains("reset")))) {
            return build(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "503 Service Unavailable",
                    "Could not establish a connection to service-b at " + baseUrl + ". " +
                    "Verify the container is running and app.http-client.base-url is correct.",
                    "UNREACHABLE"
            );
        }

        // NoHttpResponseException: server closed the keep-alive connection before service-a reused it.
        // Triggered when service-b's keep-alive-timeout (5s) < service-a's pool idle-timeout (30s)
        // and automatic retries are disabled (.disableAutomaticRetries() in HttpClientConfig).
        if (cause instanceof NoHttpResponseException
                || (cause != null && cause.getMessage() != null
                    && cause.getMessage().toLowerCase().contains("connection reset"))) {
            return build(
                    HttpStatus.BAD_GATEWAY,
                    "502 Bad Gateway",
                    "service-b closed the keep-alive connection before service-a could reuse it. " +
                    "service-b's keep-alive-timeout (5s) is shorter than service-a's pool idle-timeout (30s). " +
                    "Automatic retries are disabled intentionally — see HttpClientConfig.disableAutomaticRetries().",
                    "STALE_CONNECTION"
            );
        }

        return build(
                HttpStatus.BAD_GATEWAY,
                "502 Bad Gateway",
                "Unexpected upstream I/O error: " + ex.getMessage(),
                "UPSTREAM_ERROR"
        );
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String error, String message, String scenario) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .httpStatus(status.value())
                        .error(error)
                        .message(message)
                        .scenario(scenario)
                        .upstreamUrl(baseUrl)
                        .timestamp(ZonedDateTime.now())
                        .build()
        );
    }
}
