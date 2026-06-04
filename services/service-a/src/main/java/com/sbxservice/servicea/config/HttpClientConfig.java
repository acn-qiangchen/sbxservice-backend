package com.sbxservice.servicea.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {

    private final HttpClientProperties props;

    public HttpClientConfig(HttpClientProperties props) {
        this.props = props;
    }

    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        ConnectionConfig connConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(props.getConnectTimeoutMs()))
                // Default socket timeout per connection; overridden per-request by RequestConfig
                .setSocketTimeout(Timeout.ofMilliseconds(props.getReadTimeoutMs()))
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(props.getPool().getMaxTotal());
        cm.setDefaultMaxPerRoute(props.getPool().getMaxPerRoute());
        cm.setDefaultConnectionConfig(connConfig);
        // Disable connection validation before reuse. By default HC5 validates connections
        // that have been idle for >2s — detecting a dead connection and silently opening a
        // fresh one. Disabling this lets the stale-connection error surface as 502.
        cm.setValidateAfterInactivity(TimeValue.ofMilliseconds(-1));
        return cm;
    }

    @Bean
    public CloseableHttpClient httpClient(PoolingHttpClientConnectionManager connectionManager) {
        // Response timeout is the per-request read timeout (how long to wait for data after connection)
        RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofMilliseconds(props.getReadTimeoutMs()))
                .build();

        // Intentionally ignore the server's Keep-Alive: timeout= header.
        // service-b sends "Keep-Alive: timeout=5" (its Tomcat keep-alive-timeout).
        // If we honour that, HC5 expires the pooled connection after 5s and opens a
        // fresh one — bypassing the stale-connection error we want to demonstrate.
        // By always returning idleTimeoutMs (30s), the pool holds the connection for
        // 30s even though service-b already closed the TCP socket at 5s.
        // When HC5 then tries to write on the dead socket → NoHttpResponseException → 502.
        var keepAliveStrategy = (org.apache.hc.client5.http.ConnectionKeepAliveStrategy) (response, context) -> {
            if (!props.getKeepAlive().isEnabled()) {
                return TimeValue.ZERO_MILLISECONDS;
            }
            return TimeValue.ofMilliseconds(props.getKeepAlive().getIdleTimeoutMs());
        };

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setKeepAliveStrategy(keepAliveStrategy)
                // CRITICAL: disabling auto-retry surfaces stale-connection errors as 502 Bad Gateway.
                // Without this, Apache HttpClient silently retries on a fresh connection and the call succeeds.
                // This is intentional for the 502 demo — remove this line to see the "fixed" behaviour.
                .disableAutomaticRetries()
                .build();
    }

    @Bean
    public HttpComponentsClientHttpRequestFactory requestFactory(CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setConnectionRequestTimeout(props.getConnectTimeoutMs());
        return factory;
    }

    @Bean
    public RestClient restClient(HttpComponentsClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(props.getBaseUrl())
                .build();
    }
}
