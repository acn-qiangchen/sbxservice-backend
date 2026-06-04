package com.sbxservice.serviceb.controller;

import com.sbxservice.serviceb.model.BackendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Backend API", description = "service-b endpoints — used by service-a to demonstrate 502/503/504 error scenarios")
public class BackendController {

    private static final Logger log = LoggerFactory.getLogger(BackendController.class);

    @Value("${server.tomcat.keep-alive-timeout:60000}")
    private long keepAliveTimeoutMs;

    @GetMapping("/data")
    @Operation(summary = "Immediate response — happy path")
    public ResponseEntity<BackendResponse> getData(HttpServletRequest request) {
        log.debug("Returning immediate data response, clientPort={}", request.getRemotePort());
        return ResponseEntity.ok(BackendResponse.builder()
                .status("ok")
                .message("Data returned immediately")
                .delayMs(0)
                .timestamp(ZonedDateTime.now())
                .serverInstance(hostname())
                .clientPort(request.getRemotePort())
                .build());
    }

    @GetMapping("/slow")
    @Operation(
        summary = "Delayed response — triggers 504 in service-a when delayMs > service-a's read-timeout-ms",
        description = "Sleeps for the specified duration before responding. " +
                      "Set delayMs greater than service-a's app.http-client.read-timeout-ms (default 5000) to trigger 504."
    )
    public ResponseEntity<BackendResponse> getSlow(
            @Parameter(description = "How long to sleep in milliseconds before responding")
            @RequestParam(defaultValue = "10000") long delayMs,
            HttpServletRequest request) throws InterruptedException {
        log.info("Sleeping for {}ms, clientPort={}", delayMs, request.getRemotePort());
        Thread.sleep(delayMs);
        log.info("Woke up after {}ms, returning response", delayMs);
        return ResponseEntity.ok(BackendResponse.builder()
                .status("slow-ok")
                .message("Responded after " + delayMs + "ms delay")
                .delayMs(delayMs)
                .timestamp(ZonedDateTime.now())
                .serverInstance(hostname())
                .clientPort(request.getRemotePort())
                .build());
    }

    @GetMapping("/status")
    @Operation(
        summary = "Server status and keep-alive info",
        description = "Shows server info and the configured keep-alive-timeout. " +
                      "This value is intentionally shorter than service-a's pool idle-timeout to enable the 502 stale-connection demo."
    )
    public ResponseEntity<Map<String, Object>> getStatus() {
        Runtime rt = Runtime.getRuntime();
        return ResponseEntity.ok(Map.of(
                "service", "service-b",
                "instance", hostname(),
                "timestamp", ZonedDateTime.now().toString(),
                "keepAliveTimeoutMs", keepAliveTimeoutMs,
                "jvm", Map.of(
                        "freeMemoryMb", rt.freeMemory() / (1024 * 1024),
                        "totalMemoryMb", rt.totalMemory() / (1024 * 1024)
                ),
                "demo502Note", "Tomcat keep-alive-timeout=" + keepAliveTimeoutMs +
                        "ms. service-a pool idle-timeout=30000ms. " +
                        "Wait " + (keepAliveTimeoutMs / 1000 + 1) +
                        "s between calls to trigger 502 in service-a."
        ));
    }

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
