package com.sbxservice.servicea.model;

import lombok.*;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GatewayResponse {
    private String status;
    private String upstream;
    private Object upstreamData;
    private ZonedDateTime gatewayTimestamp;
    private long elapsedMs;
}
