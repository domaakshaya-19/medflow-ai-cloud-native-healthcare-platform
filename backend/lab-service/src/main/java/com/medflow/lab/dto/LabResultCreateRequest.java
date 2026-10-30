package com.medflow.lab.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record LabResultCreateRequest(@NotNull UUID orderId,@NotBlank @Size(max=2000) String resultSummary,
        @Size(max=6000) String details,@Size(max=1000) String referenceRange,boolean abnormal,
        @PastOrPresent Instant resultDate) {}
