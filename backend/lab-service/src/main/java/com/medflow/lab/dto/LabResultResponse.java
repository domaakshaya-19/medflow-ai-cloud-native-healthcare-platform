package com.medflow.lab.dto;
import java.time.Instant;
import java.util.UUID;
public record LabResultResponse(UUID id,UUID orderId,UUID patientId,UUID doctorId,String resultSummary,
        String details,String referenceRange,boolean abnormal,Instant resultDate,Instant createdAt,Instant updatedAt) {}
