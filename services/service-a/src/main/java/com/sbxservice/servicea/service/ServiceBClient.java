package com.sbxservice.servicea.service;

import com.sbxservice.servicea.config.HttpClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ServiceBClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceBClient.class);

    private final RestClient restClient;
    private final HttpClientProperties props;

    public ServiceBClient(RestClient restClient, HttpClientProperties props) {
        this.restClient = restClient;
        this.props = props;
    }

    public Object fetchData() {
        log.debug("Calling service-b GET /api/data");
        return restClient.get()
                .uri("/api/data")
                .retrieve()
                .body(Object.class);
    }

    public Object fetchSlow(long delayMs) {
        log.debug("Calling service-b GET /api/slow?delayMs={}", delayMs);
        return restClient.get()
                .uri("/api/slow?delayMs={d}", delayMs)
                .retrieve()
                .body(Object.class);
    }

    public String getBaseUrl() {
        return props.getBaseUrl();
    }
}
