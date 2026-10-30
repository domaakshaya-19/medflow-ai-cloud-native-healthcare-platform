package com.medflow.prescription.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "patient-service")
public interface PatientClient {
    record PatientReference(UUID id, UUID userId) {}

    @GetMapping("/api/patients/by-auth-user/{authUserId}")
    PatientReference byAuthUser(@PathVariable UUID authUserId, @RequestHeader("Authorization") String authorization);

    @GetMapping("/api/patients/{patientId}")
    PatientReference byId(@PathVariable UUID patientId, @RequestHeader("Authorization") String authorization);
}
