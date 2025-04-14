package com.sbxservice.hello.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.ToString;

/**
 * Model class representing a greeting response.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class GreetingResponse {
    
    /**
     * The greeting message to be returned to the client.
     */
    private String message;
} 