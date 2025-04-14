package com.sbxservice.hello.controller;

import com.sbxservice.hello.model.GreetingResponse;
import com.sbxservice.hello.service.HelloService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web layer tests for the HelloController.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HelloControllerTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HelloService helloService;

    @Test
    @DisplayName("GET /api/hello should return default greeting when no name is provided")
    void shouldReturnDefaultGreetingWhenNoNameIsProvided() {
        // When
        ResponseEntity<GreetingResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/hello", 
                GreetingResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Hello");
    }

    @Test
    @DisplayName("GET /api/hello with name parameter should return personalized greeting")
    void shouldReturnPersonalizedGreetingWhenNameIsProvided() {
        // When
        ResponseEntity<GreetingResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/hello?name=John", 
                GreetingResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("John");
    }

    @Test
    @DisplayName("GET /api/hello with empty name parameter should return default greeting")
    void shouldReturnDefaultGreetingWhenEmptyNameIsProvided() {
        // When
        ResponseEntity<GreetingResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/hello?name=", 
                GreetingResponse.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Hello");
    }
} 