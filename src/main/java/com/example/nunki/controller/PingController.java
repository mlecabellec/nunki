package com.example.nunki.controller;

/*
 * REQ-00014 – Support for websocket/STOMP async exchanges
 * TSK-00020.3 – Create Ping Controller
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.time.Instant;
import java.util.Objects;

@Controller
public class PingController {

    private static final Logger logger = LoggerFactory.getLogger(PingController.class);

    /*
     * Data carriers for Ping and Pong messages
     */
    public record PingMessage(String sender, String content, long timestamp) {
        public PingMessage {
            Objects.requireNonNull(sender, "Sender must not be null");
            Objects.requireNonNull(content, "Content must not be null");
        }
    }

    public record PongMessage(String message, long timestamp) {
        public PongMessage {
            Objects.requireNonNull(message, "Message must not be null");
        }
    }

    @MessageMapping("/ping")
    @SendTo("/topic/ping-response")
    public PongMessage handlePing(PingMessage ping) {
        /*
         * TSK-00020.3: Precondition validation, logging, and response formulation
         */
        Objects.requireNonNull(ping, "Ping message must not be null");
        
        logger.info("Received ping from {}: {} at {}", ping.sender(), ping.content(), ping.timestamp());
        
        String responseContent = "Pong from Spring backend! Received: " + ping.content();
        long responseTimestamp = Instant.now().toEpochMilli();
        
        return new PongMessage(responseContent, responseTimestamp);
    }
}
