package com.medflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalDiscoveryConfigurationTest {
    @Test void localProfileAdvertisesLoopbackAndKeepsTestDiscoveryDisabled() {
        new ApplicationContextRunner().withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=local").run(context -> {
                    assertEquals("127.0.0.1", context.getEnvironment().getProperty("eureka.instance.ip-address"));
                    assertEquals("true", context.getEnvironment().getProperty("eureka.instance.prefer-ip-address"));
                    assertEquals("false", context.getEnvironment().getProperty("eureka.client.enabled"));
                });
    }
}
