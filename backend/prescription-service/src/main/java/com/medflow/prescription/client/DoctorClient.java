package com.medflow.prescription.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "doctor-service")
public interface DoctorClient {
    record DoctorReference(UUID id, UUID userId) {}

    @GetMapping("/api/doctors/me")
    DoctorReference me(@RequestHeader("Authorization") String authorization);

    @GetMapping("/api/doctors/{id}")
    DoctorReference byId(@PathVariable UUID id, @RequestHeader("Authorization") String authorization);
}
