package com.medflow.gateway;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false", "spring.cloud.loadbalancer.cache.enabled=false"})
@ActiveProfiles("local")
class PatientRoutingTest {
    @LocalServerPort int port;
    @Autowired DisposableServer patientStub;
    @Autowired AtomicReference<List<ServiceInstance>> instances;
    @Autowired Environment environment;
    WebTestClient client;

    @BeforeEach void setup() {
        instances.set(List.of(new DefaultServiceInstance("test-patient", "patient-service",
                "127.0.0.1", patientStub.port(), false)));
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    @Test void localProfileAdvertisesLoopbackWithoutChangingDiscoveryRoute() {
        assertEquals("127.0.0.1", environment.getProperty("eureka.instance.ip-address"));
        assertEquals("true", environment.getProperty("eureka.instance.prefer-ip-address"));
        assertEquals("lb://patient-service", environment.getProperty("spring.cloud.gateway.routes[1].uri"));
    }

    @Test void forwardsPathAndAuthorizationToDiscoveredPatientInstance() {
        client.get().uri("/api/patients/me").header("Authorization", "Bearer synthetic-valid")
                .exchange().expectStatus().isOk().expectBody().jsonPath("$.userId").isEqualTo("synthetic-user");
    }

    @Test void preservesMissingAndInvalidAuthenticationResponses() {
        client.get().uri("/api/patients/me").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/patients/me").header("Authorization", "Bearer invalid")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test void preservesMissingProfileResponseForOnboarding() {
        client.get().uri("/api/patients/me").header("Authorization", "Bearer synthetic-missing")
                .exchange().expectStatus().isNotFound();
    }

    @Test void unavailableRegistrationReturnsServiceUnavailable() {
        instances.set(List.of());
        client.get().uri("/api/patients/me").exchange().expectStatus().isEqualTo(503);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class StubConfiguration {
        @Bean(destroyMethod = "disposeNow") DisposableServer patientStub() {
            return HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
                if (!"/api/patients/me".equals(request.uri())) return response.status(400).send();
                String authorization = request.requestHeaders().get("Authorization");
                if ("Bearer synthetic-valid".equals(authorization))
                    return response.status(200).header("Content-Type", "application/json")
                            .sendString(reactor.core.publisher.Mono.just("{\"userId\":\"synthetic-user\"}"));
                return response.status("Bearer synthetic-missing".equals(authorization) ? 404 : 401).send();
            }).bindNow();
        }
        @Bean AtomicReference<List<ServiceInstance>> instances() { return new AtomicReference<>(List.of()); }
        @Bean ServiceInstanceListSupplier patientInstances(AtomicReference<List<ServiceInstance>> instances) {
            return new ServiceInstanceListSupplier() {
                public String getServiceId() { return "patient-service"; }
                public Flux<List<ServiceInstance>> get() { return Flux.defer(() -> Flux.just(instances.get())); }
            };
        }
    }
}
