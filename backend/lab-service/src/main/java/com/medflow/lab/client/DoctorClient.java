package com.medflow.lab.client;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
@FeignClient(name="doctor-service")
public interface DoctorClient {
    record DoctorReference(UUID id,UUID userId) {}
    @GetMapping("/api/doctors/me") DoctorReference me(@RequestHeader("Authorization")String authorization);
}
