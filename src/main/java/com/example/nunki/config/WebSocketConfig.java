package com.example.nunki.config;

/*
 * REQ-00014 – Support for websocket/STOMP async exchanges
 * TSK-00020.1 – Configure STOMP Broker
 */

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.Objects;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        /*
         * TSK-00020.1: Register the endpoint for clients to connect
         */
        Objects.requireNonNull(registry, "StompEndpointRegistry must not be null");
        registry.addEndpoint("/ws-stomp")
                .setAllowedOriginPatterns("*"); // Allow all origins for dev/testing
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        /*
         * TSK-00020.1: Enable a simple broker for topic destinations and set app prefixes
         */
        Objects.requireNonNull(registry, "MessageBrokerRegistry must not be null");
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }
}
