package com.sbxservice.hello.controller;

import com.sbxservice.hello.model.GreetingResponse;
import com.sbxservice.hello.service.HelloService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes the hello API endpoint.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Hello API", description = "API for generating greeting messages")
public class HelloController {

    private final HelloService helloService;

    @Autowired
    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    /**
     * Returns a greeting message.
     *
     * @param name optional name to include in the greeting
     * @return a response entity containing the greeting message
     */
    @GetMapping("/hello")
    @Operation(
        summary = "Get a greeting message",
        description = "Returns a customized greeting message based on the provided name. " +
                     "If no name is provided, returns a default greeting."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Greeting message generated successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = GreetingResponse.class)
            )
        )
    })
    public ResponseEntity<GreetingResponse> getGreeting(
            @Parameter(description = "Name to include in the greeting")
            @RequestParam(required = false) String name) {
        
        String greeting = helloService.generateGreeting(name);
        return ResponseEntity.ok(new GreetingResponse(greeting));
    }
} 