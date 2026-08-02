package fr.lecabellec.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Spring App Integration Tests (Disabled Embedded Server)
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
    "opcua.embedded-server.enabled=false"
})
public class OpcUaDisabledServerIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("Verify Spring context omits QuasarOpcUaServer bean when opcua.embedded-server.enabled=false")
    public void testEmbeddedServerBeanAbsent() {
        assertFalse(applicationContext.containsBean("embeddedOpcUaServer"),
            "embeddedOpcUaServer bean should NOT be registered when enabled=false");
    }
}
