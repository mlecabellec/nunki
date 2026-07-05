package com.example.nunki.controller;

/*
 * REQ-00014 – Support for websocket/STOMP async exchanges
 * TSK-00022.1 – Backend Integration Test for Ping/Pong Loop
 */

import com.example.nunki.controller.PingController.PingMessage;
import com.example.nunki.controller.PingController.PongMessage;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PingWebSocketTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.example.nunki.opcua.subscription.SubscriptionManager subscriptionManager;

    private static final Logger logger = LoggerFactory.getLogger(PingWebSocketTest.class);

    @LocalServerPort
    private int port;

    @Test
    public void testPingPongConnection() throws Exception {
        /*
         * TSK-00022.1: Establish a WebSocket client, connect, subscribe to /topic/ping-response,
         * send a ping message, and verify we get a pong response.
         */
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        String wsUrl = "ws://localhost:" + port + "/ws-stomp";
        logger.info("Connecting to WebSocket URL: {}", wsUrl);

        StompSession stompSession = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                logger.info("Received frame: {} with headers: {}", payload, headers);
            }
        }).get(10, TimeUnit.SECONDS);

        assertThat(stompSession).isNotNull();
        logger.info("Connected to WebSocket, session ID: {}", stompSession.getSessionId());

        BlockingQueue<PongMessage> blockingQueue = new LinkedBlockingQueue<>();

        // Subscribe to responses
        stompSession.subscribe("/topic/ping-response", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                Objects.requireNonNull(headers, "StompHeaders must not be null");
                return PongMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Objects.requireNonNull(payload, "Payload must not be null");
                logger.info("Stomp Client received frame: {}", payload);
                blockingQueue.offer((PongMessage) payload);
            }
        });

        // Send a ping
        PingMessage pingMessage = new PingMessage("test-client", "Hello Nunki", Instant.now().toEpochMilli());
        logger.info("Sending ping payload: {}", pingMessage);
        stompSession.send("/app/ping", pingMessage);

        // Await response
        PongMessage pongResponse = blockingQueue.poll(5, TimeUnit.SECONDS);
        logger.info("Received pong response: {}", pongResponse);

        assertThat(pongResponse).isNotNull();
        assertThat(pongResponse.message()).contains("Pong from Spring backend!");
        assertThat(pongResponse.message()).contains("Hello Nunki");
        assertThat(pongResponse.timestamp()).isGreaterThan(0L);

        stompSession.disconnect();
    }
}
