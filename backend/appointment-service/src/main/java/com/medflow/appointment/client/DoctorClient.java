package com.medflow.appointment.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "doctor-service")
public interface DoctorClient {
    record DoctorReference(UUID id, UUID userId) {}

    record DoctorDirectoryReference(UUID id) {}

    @GetMapping("/api/doctors/directory/{id}")
    DoctorDirectoryReference directoryEntry(@PathVariable("id") UUID id, @RequestHeader("Authorization") String authorization);

    @GetMapping("/api/doctors/me")
    DoctorReference me(@RequestHeader("Authorization") String authorization);
}
