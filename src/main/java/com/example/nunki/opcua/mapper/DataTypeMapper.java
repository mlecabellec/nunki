package com.example.nunki.opcua.mapper;

/*
 * REQ-00010 – Full‑featured OPC‑UA client service
 * FR-00010 – Async API for read/write/call/subscribe
 * TSK-00007 – DataTypeMapper implementation
 */

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.springframework.stereotype.Component;

/**
 * Maps Java values to Milo {@link Variant}/{@link DataValue} and back. Supports the
 * most common built‑in types and provides a registration hook for custom structs.
 */
@Component
public class DataTypeMapper {

    private final Map<Class<?>, Function<Object, Variant>> toVariantRegistry = new HashMap<>();
    private final Map<Class<?>, Function<Variant, Object>> fromVariantRegistry = new HashMap<>();

    public DataTypeMapper() {
        // Register default mappings
        registerDefaultMappings();
    }

    private void registerDefaultMappings() {
        // Primitive wrappers and String – identity mapping
        registerMapping(Number.class, v -> new Variant(v));
        registerMapping(Boolean.class, v -> new Variant(v));
        registerMapping(String.class, v -> new Variant(v));
        registerMapping(byte[].class, v -> new Variant(v));
        // Instant ↔ DateTime
        registerMapping(Instant.class, v -> new Variant(new DateTime(java.util.Date.from((Instant) v))));
        registerFromVariant(Instant.class, v -> ((DateTime) v.getValue()).getJavaInstant());
    }

    /** Register a conversion from {@code javaClass} to {@link Variant}. */
    public <T> void registerMapping(Class<T> javaClass, Function<T, Variant> mapper) {
        toVariantRegistry.put(javaClass, (Function<Object, Variant>) mapper);
    }

    /** Register a conversion from {@link Variant} to {@code javaClass}. */
    public <T> void registerFromVariant(Class<T> javaClass, Function<Variant, T> mapper) {
        fromVariantRegistry.put(javaClass, (Function<Variant, Object>) mapper);
    }

    /** Convert a Java value to a Milo {@link Variant}. */
    public Variant toVariant(Object value) {
        if (value == null) {
            return new Variant((Object) null);
        }
        // Look for direct class match
        Function<Object, Variant> mapper = toVariantRegistry.get(value.getClass());
        if (mapper != null) {
            return mapper.apply(value);
        }
        // Check assignable types (e.g., Number subclasses)
        for (Map.Entry<Class<?>, Function<Object, Variant>> e : toVariantRegistry.entrySet()) {
            if (e.getKey().isAssignableFrom(value.getClass())) {
                return e.getValue().apply(value);
            }
        }
        throw new IllegalArgumentException("Unsupported type for OPC-UA Variant conversion: " + value.getClass());
    }

    /** Convert a Milo {@link Variant} to a Java value of the requested type. */
    public <T> T fromVariant(Variant variant, Class<T> target) {
        if (variant == null || variant.getValue() == null) {
            return null;
        }
        // Direct registry lookup
        Function<Variant, Object> mapper = fromVariantRegistry.get(target);
        if (mapper != null) {
            return target.cast(mapper.apply(variant));
        }
        // Fallback: try simple cast if compatible
        Object raw = variant.getValue();
        if (target.isInstance(raw)) {
            return target.cast(raw);
        }
        // Special handling for DateTime → Instant mapping
        if (target == Instant.class && raw instanceof DateTime) {
            return target.cast(((DateTime) raw).getJavaInstant());
        }
        throw new IllegalArgumentException("Cannot convert Variant of type " + raw.getClass() + " to " + target);
    }
}
