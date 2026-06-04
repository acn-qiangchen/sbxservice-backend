package com.sbxservice.servicea.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.http-client")
public class HttpClientProperties {

    private String baseUrl = "http://service-b:8082";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;

    private Pool pool = new Pool();
    private KeepAlive keepAlive = new KeepAlive();

    @Getter
    @Setter
    public static class Pool {
        private int maxTotal = 50;
        private int maxPerRoute = 20;
    }

    @Getter
    @Setter
    public static class KeepAlive {
        private boolean enabled = true;
        // Keep connections alive for 30s — intentionally longer than service-b's 5s keep-alive-timeout
        // to make the 502 stale-connection scenario reproducible.
        private int idleTimeoutMs = 30000;
    }
}
