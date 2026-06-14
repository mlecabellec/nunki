package com.example.nunki.controller;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * TSK-00105 – JUnit 5 Controller Integration Test
 */

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.nunki.opcua.api.OpcUaClientApi;
import com.example.nunki.opcua.dto.OpcUaNodeDto;

@SpringBootTest
@AutoConfigureMockMvc
public class OpcUaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpcUaClientApi opcUaClient;

    @org.junit.jupiter.api.BeforeEach
    public void setUp() {
        when(opcUaClient.connect()).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void testGetTreeSuccess() throws Exception {
        OpcUaNodeDto mockNode = new OpcUaNodeDto(
            "ns=1;s=Data",
            "Data",
            "Object",
            "",
            Collections.singletonList(
                new OpcUaNodeDto("ns=1;s=Data/MySwitch", "MySwitch", "Variable", "false", Collections.emptyList())
            )
        );

        when(opcUaClient.browseTree(any(NodeId.class)))
            .thenReturn(CompletableFuture.completedFuture(mockNode));

        MvcResult mvcResult = mockMvc.perform(get("/api/opcua/tree"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodeId").value("ns=1;s=Data"))
            .andExpect(jsonPath("$.name").value("Data"))
            .andExpect(jsonPath("$.nodeClass").value("Object"))
            .andExpect(jsonPath("$.children[0].nodeId").value("ns=1;s=Data/MySwitch"))
            .andExpect(jsonPath("$.children[0].name").value("MySwitch"))
            .andExpect(jsonPath("$.children[0].value").value("false"));
    }

    @Test
    public void testWriteValueSuccess() throws Exception {
        when(opcUaClient.write(eq(new NodeId(1, "Data/MySwitch")), any(Variant.class)))
            .thenReturn(CompletableFuture.completedFuture(StatusCode.GOOD));

        String jsonRequest = "{\"nodeId\":\"ns=1;s=Data/MySwitch\",\"value\":\"true\",\"type\":\"Boolean\"}";

        MvcResult mvcResult = mockMvc.perform(post("/api/opcua/write")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.statusCode").value(0));
    }

    @Test
    public void testInvokeMethodSuccess() throws Exception {
        OpcUaClientApi.MethodResult mockResult = new OpcUaClientApi.MethodResult(
            Collections.singletonList(new Variant("True")),
            StatusCode.GOOD
        );

        when(opcUaClient.call(eq(new NodeId(1, "Data")), eq(new NodeId(1, "Data/ToggleSwitch")), any(java.util.List.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResult));

        String jsonRequest = "{\"objectId\":\"ns=1;s=Data\",\"methodId\":\"ns=1;s=Data/ToggleSwitch\",\"arguments\":[]}";

        MvcResult mvcResult = mockMvc.perform(post("/api/opcua/invoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.result").value("True"))
            .andExpect(jsonPath("$.statusCode").value(0));
    }
}
