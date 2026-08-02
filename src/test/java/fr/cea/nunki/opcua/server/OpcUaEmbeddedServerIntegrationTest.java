package fr.cea.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Spring App Integration Tests (With/Without Embedded Server)
 * @task TSK-20260311-005 Integrated Server Launch Options Verification
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "opcua.embedded-server.enabled=true",
    "opcua.embedded-server.port=12687",
    "opcua.embedded-server.warmup-delay-seconds=0"
})
public class OpcUaEmbeddedServerIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Verify Spring context initializes QuasarOpcUaServer bean when opcua.embedded-server.enabled=true")
    public void testEmbeddedServerBeanPresent() {
        assertTrue(applicationContext.containsBean("embeddedOpcUaServer"),
            "embeddedOpcUaServer bean should be registered when enabled=true");
        
        QuasarOpcUaServer server = applicationContext.getBean(QuasarOpcUaServer.class);
        assertNotNull(server, "QuasarOpcUaServer bean instance should be non-null");
        assertTrue(server.isRunning(), "Embedded server should be actively running");
        assertEquals(12687, server.getPort(), "Embedded server port should match configured test port");
    }
}
