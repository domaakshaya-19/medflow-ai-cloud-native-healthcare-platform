package com.medflow.lab.dto;
import com.medflow.lab.entity.LabOrderStatus;
import java.time.Instant;
import java.util.UUID;
public record LabOrderResponse(UUID id,UUID patientId,UUID doctorId,UUID appointmentId,String testName,
        String instructions,LabOrderStatus status,Instant orderedAt,Instant createdAt,Instant updatedAt) {}
