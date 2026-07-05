package com.example.nunki;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.example.nunki.opcua.subscription.SubscriptionManager;

/**
 * Test configuration providing a lightweight stub for {@link SubscriptionManager}.
 * This avoids Mockito's inline mock generation, which is incompatible with the
 * Java version used in the CI environment.
 */
@TestConfiguration
public class TestConfig {
    @Bean
    public SubscriptionManager subscriptionManager() {
        // Return a minimal stub that satisfies the constructor requirements.
        return new SubscriptionManager(null, null) {
            // No-op implementations – real functionality is not required for unit tests.
        };
    }
}
