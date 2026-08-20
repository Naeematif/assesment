package org.example.gateway;

import org.example.gateway.config.GatewayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the programmable API monetization gateway.
 *
 * <p>The gateway sits in front of the internal business APIs and, for every inbound call,
 * authenticates the caller, resolves its commercial tier, enforces the tier's rate limit and
 * monthly quota, and records usage so that the billing job can price it later.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
