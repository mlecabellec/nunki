package fr.cea.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @requirement REQ-00014 Support for websocket/STOMP async exchanges
 * @feature FR-00010 Full Chain End-to-End REST & STOMP Integration Test
 * @task TSK-20260311-005 End-to-End Verification Pipeline
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "opcua.embedded-server.enabled=true",
    "opcua.embedded-server.port=12688",
    "opcua.embedded-server.warmup-delay-seconds=0",
    "opcua.client.endpoint-url=opc.tcp://localhost:12688/opcua"
})
public class OpcUaEndToEndRestStompTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Verify full chain REST tree browsing, write, invoke, and STOMP telemetry broadcast against embedded OPC-UA server")
    public void testFullChainRestAndStomp() throws Exception {
        // 1. Verify GET /api/opcua/tree REST Endpoint
        ResponseEntity<Map> treeResp = restTemplate.getForEntity("/api/opcua/tree", Map.class);
        assertEquals(HttpStatus.OK, treeResp.getStatusCode());
        assertNotNull(treeResp.getBody());
        assertTrue(treeResp.getBody().containsKey("children"));

        // 2. Setup WebSocket STOMP Client subscriber
        String wsUrl = "ws://localhost:" + port + "/ws-stomp";
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        CompletableFuture<Map> stompMessageFuture = new CompletableFuture<>();
        StompSession stompSession = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        stompSession.subscribe("/topic/opcua-tree", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                stompMessageFuture.complete((Map) payload);
            }
        });

        // 3. Verify POST /api/opcua/write REST Endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String writeBody = "{\"nodeId\":\"ns=2;s=Data/MyInt\",\"value\":\"777\",\"type\":\"Int32\"}";
        HttpEntity<String> writeEntity = new HttpEntity<>(writeBody, headers);

        ResponseEntity<Map> writeResp = restTemplate.postForEntity("/api/opcua/write", writeEntity, Map.class);
        assertEquals(HttpStatus.OK, writeResp.getStatusCode());
        assertEquals(true, writeResp.getBody().get("success"));

        // 4. Verify STOMP broadcast was triggered on /topic/opcua-tree
        Map stompMsg = stompMessageFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(stompMsg);
        assertEquals("ns=2;s=Data/MyInt", stompMsg.get("nodeId"));
        assertEquals("777", stompMsg.get("value"));

        // 5. Verify POST /api/opcua/invoke REST Endpoint (Calling ToggleSwitch)
        String invokeBody = "{\"objectId\":\"ns=2;s=Data\",\"methodId\":\"ns=2;s=Data/ToggleSwitch\",\"arguments\":[]}";
        HttpEntity<String> invokeEntity = new HttpEntity<>(invokeBody, headers);

        ResponseEntity<Map> invokeResp = restTemplate.postForEntity("/api/opcua/invoke", invokeEntity, Map.class);
        assertEquals(HttpStatus.OK, invokeResp.getStatusCode());
        assertEquals(true, invokeResp.getBody().get("success"));
        assertEquals("True", invokeResp.getBody().get("result"));

        stompSession.disconnect();
    }
}
