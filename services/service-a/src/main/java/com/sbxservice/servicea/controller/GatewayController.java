package com.sbxservice.servicea.controller;

import com.sbxservice.servicea.config.HttpClientProperties;
import com.sbxservice.servicea.model.GatewayResponse;
import com.sbxservice.servicea.service.ServiceBClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Gateway API", description = "service-a proxies these calls to service-b — trigger 502/503/504 by varying parameters and configuration")
public class GatewayController {

    private final ServiceBClient client;
    private final HttpClientProperties props;

    public GatewayController(ServiceBClient client, HttpClientProperties props) {
        this.client = client;
        this.props = props;
    }

    @GetMapping("/call")
    @Operation(
        summary = "Happy path — calls service-b /api/data immediately",
        description = "Returns 200 under normal conditions. " +
                      "Returns 503 if service-b is unreachable. " +
                      "Returns 502 if called on a stale keep-alive connection (wait 6s after a previous call)."
    )
    public ResponseEntity<GatewayResponse> call() {
        long start = System.currentTimeMillis();
        Object upstream = client.fetchData();
        return ResponseEntity.ok(GatewayResponse.builder()
                .status("ok")
                .upstream(props.getBaseUrl() + "/api/data")
                .upstreamData(upstream)
                .gatewayTimestamp(ZonedDateTime.now())
                .elapsedMs(System.currentTimeMillis() - start)
                .build());
    }

    @GetMapping("/call-slow")
    @Operation(
        summary = "Slow call — triggers 504 when delayMs exceeds read-timeout-ms",
        description = "Calls service-b /api/slow?delayMs={delayMs}. " +
                      "Default delayMs=10000ms. Default read-timeout-ms=5000ms. " +
                      "Increase delayMs beyond read-timeout-ms to trigger 504. " +
                      "Use /api/config to see the current read-timeout-ms."
    )
    public ResponseEntity<GatewayResponse> callSlow(
            @Parameter(description = "How long service-b should sleep before responding (ms)")
            @RequestParam(defaultValue = "10000") long delayMs) {
        long start = System.currentTimeMillis();
        Object upstream = client.fetchSlow(delayMs);
        return ResponseEntity.ok(GatewayResponse.builder()
                .status("ok")
                .upstream(props.getBaseUrl() + "/api/slow?delayMs=" + delayMs)
                .upstreamData(upstream)
                .gatewayTimestamp(ZonedDateTime.now())
                .elapsedMs(System.currentTimeMillis() - start)
                .build());
    }

    @GetMapping("/config")
    @Operation(
        summary = "Show current HTTP client configuration",
        description = "Read this first to understand the active timeout and keep-alive settings before running demo scenarios."
    )
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "baseUrl", props.getBaseUrl(),
                "connectTimeoutMs", props.getConnectTimeoutMs(),
                "readTimeoutMs", props.getReadTimeoutMs(),
                "pool", Map.of(
                        "maxTotal", props.getPool().getMaxTotal(),
                        "maxPerRoute", props.getPool().getMaxPerRoute()
                ),
                "keepAlive", Map.of(
                        "enabled", props.getKeepAlive().isEnabled(),
                        "idleTimeoutMs", props.getKeepAlive().getIdleTimeoutMs()
                ),
                "automaticRetries", "DISABLED — stale connections surface as 502 (see HttpClientConfig)",
                "scenarios", Map.of(
                        "504", "GET /api/call-slow?delayMs=" + (props.getReadTimeoutMs() + 5000) +
                               "  (current read-timeout-ms=" + props.getReadTimeoutMs() + ")",
                        "503", "Stop service-b container, then GET /api/call",
                        "502", "GET /api/call, wait 6s, GET /api/call again " +
                               "(service-b keep-alive=5s < service-a pool idle=" +
                               props.getKeepAlive().getIdleTimeoutMs() + "ms)"
                )
        ));
    }
}
