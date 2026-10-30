package com.medflow.lab.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record LabResultUpdateRequest(@Size(max=2000) String resultSummary,@Size(max=6000) String details,
        @Size(max=1000) String referenceRange,Boolean abnormal,@PastOrPresent Instant resultDate) {}
