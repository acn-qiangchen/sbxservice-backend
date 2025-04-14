package com.sbxservice.hello.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the HelloService.
 */
@ExtendWith(MockitoExtension.class)
class HelloServiceTest {

    @InjectMocks
    private HelloService helloService;

    @BeforeEach
    void setUp() {
        // Set the default greeting value since we're not loading application.yml in unit tests
        ReflectionTestUtils.setField(helloService, "defaultGreeting", "Hello, World!");
    }

    @Test
    @DisplayName("Should return default greeting when name is null")
    void shouldReturnDefaultGreetingWhenNameIsNull() {
        String result = helloService.generateGreeting(null);
        assertEquals("Hello, World!", result);
    }

    @Test
    @DisplayName("Should return default greeting when name is empty")
    void shouldReturnDefaultGreetingWhenNameIsEmpty() {
        String result = helloService.generateGreeting("");
        assertEquals("Hello, World!", result);
    }

    @Test
    @DisplayName("Should return default greeting when name is blank")
    void shouldReturnDefaultGreetingWhenNameIsBlank() {
        String result = helloService.generateGreeting("   ");
        assertEquals("Hello, World!", result);
    }

    @Test
    @DisplayName("Should return personalized greeting when name is provided")
    void shouldReturnPersonalizedGreetingWhenNameIsProvided() {
        String result = helloService.generateGreeting("John");
        assertEquals("Hello, John!", result);
    }

    @Test
    @DisplayName("Should trim the name when generating greeting")
    void shouldTrimTheNameWhenGeneratingGreeting() {
        String result = helloService.generateGreeting("  Jane  ");
        assertEquals("Hello, Jane!", result);
    }
} 