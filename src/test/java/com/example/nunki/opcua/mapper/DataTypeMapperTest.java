package com.example.nunki.opcua.mapper;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00013 – Unit tests for DataTypeMapper
 */

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;

import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataTypeMapperTest {

    private DataTypeMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DataTypeMapper();
    }

    @Test
    void primitiveTypes() {
        Variant intVar = mapper.toVariant(123);
        assertEquals(Integer.class, intVar.getValue().getClass());
        Variant boolVar = mapper.toVariant(true);
        assertEquals(Boolean.class, boolVar.getValue().getClass());
        Variant strVar = mapper.toVariant("hello");
        assertEquals(String.class, strVar.getValue().getClass());
    }

    @Test
    void instantConversion() {
        Instant now = Instant.now();
        Variant v = mapper.toVariant(now);
        Instant roundTrip = mapper.fromVariant(v, Instant.class);
        assertEquals(now.getEpochSecond(), roundTrip.getEpochSecond());
    }

    @Test
    void byteArrayConversion() {
        byte[] data = {0x01, 0x02, 0x03};
        Variant v = mapper.toVariant(data);
        byte[] result = mapper.fromVariant(v, byte[].class);
        assertArrayEquals(data, result);
    }

    @Test
    void unsupportedTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> mapper.toVariant(UUID.randomUUID()));
    }
}
