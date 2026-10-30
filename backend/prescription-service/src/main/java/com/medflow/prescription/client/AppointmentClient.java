package com.medflow.prescription.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "appointment-service")
public interface AppointmentClient {
    record AppointmentReference(UUID id, UUID patientId, UUID doctorId, String status) {}

    @GetMapping("/api/appointments/{id}")
    AppointmentReference get(@PathVariable UUID id, @RequestHeader("Authorization") String authorization);
}
