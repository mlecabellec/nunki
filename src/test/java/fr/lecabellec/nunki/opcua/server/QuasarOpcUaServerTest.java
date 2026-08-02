package fr.lecabellec.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 Embedded OPC-UA Stub Server Test Suite
 * @task TSK-20260311-005 OPC UA Address Space Mocking Verification
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodResult;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class QuasarOpcUaServerTest {

    private static final Logger logger = LoggerFactory.getLogger(QuasarOpcUaServerTest.class);
    private static final int TEST_PORT = 12686;

    private QuasarOpcUaServer server;
    private OpcUaClient client;

    @BeforeEach
    public void setUp() throws Exception {
        logger.info("[Test Setup] Starting embedded QuasarOpcUaServer on test port {}...", TEST_PORT);
        server = new QuasarOpcUaServer(TEST_PORT, 0, true);
        server.start();

        String endpointUrl = "opc.tcp://localhost:" + TEST_PORT + "/opcua";
        logger.info("[Test Setup] Connecting Milo client to endpoint: {}", endpointUrl);

        client = OpcUaClient.create(
            endpointUrl,
            endpoints -> endpoints.stream().findFirst(),
            configBuilder -> configBuilder.build()
        );
        client.connect().get(5, TimeUnit.SECONDS);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (client != null) {
            try {
                client.disconnect().get(3, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Verify server lifecycle, running status, and namespace registration")
    public void testServerLifecycle() {
        assertTrue(server.isRunning(), "Server should report running state");
        assertEquals(TEST_PORT, server.getPort());
        assertNotNull(server.getNamespace(), "Namespace should be instantiated");
    }

    @Test
    @DisplayName("Verify address space browsing and 1,000-node LargeTree structure")
    public void testLargeTreeBrowsing() throws Exception {
        UShort nsIdx = client.getNamespaceTable().getIndex(QuasarNamespace.NAMESPACE_URI);
        assertNotNull(nsIdx, "Namespace URI should be registered");

        NodeId rootNodeId = new NodeId(nsIdx, "Root");
        List<ReferenceDescription> rootChildren = client.getAddressSpace().browse(rootNodeId);
        assertFalse(rootChildren.isEmpty(), "Root node should have children folders");

        boolean foundLargeTree = rootChildren.stream()
            .anyMatch(r -> r.getBrowseName().getName().equals("LargeTree"));
        assertTrue(foundLargeTree, "LargeTree folder should exist under Root");

        // Count total nodes in LargeTree branch
        NodeId largeTreeId = new NodeId(nsIdx, "LargeTree");
        int count = countNodesRecursive(largeTreeId, nsIdx);
        // The LargeTree has 1 + 10 + 50 + 400 + 539 = 1,000 declared nodes.
        // However, every 5th L5 node is a Method (case 4 in the round-robin typeSlot).
        // Milo automatically adds InputArguments and OutputArguments pseudo-variable
        // child nodes for each registered method, adding ~2 nodes per method.
        // ~539/5 = ~108 method nodes × 2 = ~216 extra nodes => actual total ~1,214.
        // We accept anything in [1000, 1500] to be resilient to exact distribution changes.
        assertTrue(count >= 1000 && count <= 1500,
            "LargeTree branch should contain between 1,000 and 1,500 nodes (actual: " + count + "). " +
            "Note: Milo auto-creates InputArguments/OutputArguments nodes for method nodes.");

    }

    private int countNodesRecursive(NodeId parentId, UShort nsIdx) throws Exception {
        int total = 1;
        org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription browseDesc =
            new org.eclipse.milo.opcua.stack.core.types.structured.BrowseDescription(
                parentId,
                org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseDirection.Forward,
                org.eclipse.milo.opcua.stack.core.Identifiers.HierarchicalReferences,
                true,
                org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(0),
                org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(
                    org.eclipse.milo.opcua.stack.core.types.enumerated.BrowseResultMask.All.getValue()
                )
            );
        org.eclipse.milo.opcua.stack.core.types.structured.BrowseResult result = client.browse(browseDesc).get();
        org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription[] children = result.getReferences();
        if (children != null) {
            for (ReferenceDescription child : children) {
                NodeId childId = child.getNodeId().toNodeId(client.getNamespaceTable()).orElse(null);
                if (childId != null && childId.getNamespaceIndex().equals(nsIdx)) {
                    total += countNodesRecursive(childId, nsIdx);
                }
            }
        }
        return total;
    }


    @Test
    @DisplayName("Verify OPC-UA Read and Write operations on Data variables")
    public void testReadWriteVariables() throws Exception {
        UShort nsIdx = client.getNamespaceTable().getIndex(QuasarNamespace.NAMESPACE_URI);
        NodeId myIntId = new NodeId(nsIdx, "Data/MyInt");

        // Initial Read — use TimestampsToReturn.Both to avoid NPE in Milo ReadRequest.Codec.encode
        DataValue initialVal = client.readValue(0.0, TimestampsToReturn.Both, myIntId).get();
        assertEquals(42, ((Number) initialVal.getValue().getValue()).intValue(), "MyInt initial value should be 42");

        // Write new value
        DataValue writeVal = new DataValue(new Variant(100));
        client.writeValue(myIntId, writeVal).get();

        // Verify Write
        DataValue updatedVal = client.readValue(0.0, TimestampsToReturn.Both, myIntId).get();
        assertEquals(100, ((Number) updatedVal.getValue().getValue()).intValue(), "MyInt updated value should be 100");
    }

    @Test
    @DisplayName("Verify CounterControl RPC methods (Increment & Decrement)")
    public void testCounterControlMethods() throws Exception {
        UShort nsIdx = client.getNamespaceTable().getIndex(QuasarNamespace.NAMESPACE_URI);
        NodeId objectId = new NodeId(nsIdx, "CounterControl");
        NodeId incMethodId = new NodeId(nsIdx, "CounterControl/Increment");
        NodeId decMethodId = new NodeId(nsIdx, "CounterControl/Decrement");
        NodeId counterValId = new NodeId(nsIdx, "CounterControl/CounterValue");

        // Initial counter value should be 0 — use TimestampsToReturn.Both
        DataValue initialVal = client.readValue(0.0, TimestampsToReturn.Both, counterValId).get();
        assertEquals(0, ((Number) initialVal.getValue().getValue()).intValue());

        // Invoke Increment method
        CallMethodRequest incRequest = new CallMethodRequest(objectId, incMethodId, new Variant[0]);
        CallMethodResult incResult = client.call(incRequest).get();
        assertTrue(incResult.getStatusCode().isGood());
        assertEquals(1, ((Number) incResult.getOutputArguments()[0].getValue()).intValue());

        // Verify state update
        DataValue afterIncVal = client.readValue(0.0, TimestampsToReturn.Both, counterValId).get();
        assertEquals(1, ((Number) afterIncVal.getValue().getValue()).intValue());

        // Invoke Decrement method
        CallMethodRequest decRequest = new CallMethodRequest(objectId, decMethodId, new Variant[0]);
        CallMethodResult decResult = client.call(decRequest).get();
        assertTrue(decResult.getStatusCode().isGood());
        assertEquals(0, ((Number) decResult.getOutputArguments()[0].getValue()).intValue());
    }

    @Test
    @DisplayName("Verify ToggleSwitch method and Lua ExecuteScript method")
    public void testDataMethodsAndLuaExecution() throws Exception {
        UShort nsIdx = client.getNamespaceTable().getIndex(QuasarNamespace.NAMESPACE_URI);
        NodeId dataObjId = new NodeId(nsIdx, "Data");
        NodeId toggleMethodId = new NodeId(nsIdx, "Data/ToggleSwitch");
        NodeId executeMethodId = new NodeId(nsIdx, "Data/ExecuteScript");
        NodeId switchId = new NodeId(nsIdx, "Data/MySwitch");

        // ToggleSwitch test
        CallMethodRequest toggleReq = new CallMethodRequest(dataObjId, toggleMethodId, new Variant[0]);
        CallMethodResult toggleRes = client.call(toggleReq).get();
        assertTrue(toggleRes.getStatusCode().isGood());
        assertEquals("True", toggleRes.getOutputArguments()[0].getValue().toString());

        DataValue switchVal = client.readValue(0.0, TimestampsToReturn.Both, switchId).get();
        assertTrue((Boolean) switchVal.getValue().getValue());

        // ExecuteScript Lua test
        String luaCode = "print('Test log entry') opcua.write('Data/MyInt', 999) return opcua.read('Data/MyInt')";
        CallMethodRequest luaReq = new CallMethodRequest(dataObjId, executeMethodId, new Variant[] { new Variant(luaCode) });
        CallMethodResult luaRes = client.call(luaReq).get();
        assertTrue(luaRes.getStatusCode().isGood());

        String jsonOutput = luaRes.getOutputArguments()[0].getValue().toString();
        assertTrue(jsonOutput.contains("\"success\":true"), "JSON response should indicate success");
        assertTrue(jsonOutput.contains("\"result\":\"999.0\"") || jsonOutput.contains("\"result\":\"999\""), "JSON result should reflect written value");
        assertTrue(jsonOutput.contains("Test log entry"), "JSON response should contain printed logs");

        // Verify MyInt written by Lua
        DataValue myIntVal = client.readValue(0.0, TimestampsToReturn.Both, new NodeId(nsIdx, "Data/MyInt")).get();
        assertEquals(999, ((Number) myIntVal.getValue().getValue()).intValue());
    }

    @Test
    @DisplayName("Verify Process Mimic Simulation loop")
    public void testProcessMimicSimulation() throws Exception {
        UShort nsIdx = client.getNamespaceTable().getIndex(QuasarNamespace.NAMESPACE_URI);
        NodeId pumpId = new NodeId(nsIdx, "Data/PumpRunning");
        NodeId tankId = new NodeId(nsIdx, "Data/TankLevel");

        // Set PumpRunning = true
        client.writeValue(pumpId, new DataValue(new Variant(true))).get();

        // Wait 300 ms for simulation ticks
        Thread.sleep(300);

        DataValue tankVal = client.readValue(0.0, TimestampsToReturn.Both, tankId).get();
        double level = ((Number) tankVal.getValue().getValue()).doubleValue();
        assertTrue(level > 45.0, "Tank level should increase when pump is running");
    }
}
