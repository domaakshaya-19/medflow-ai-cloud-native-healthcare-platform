package com.medflow.medicalrecords.dto;

import jakarta.validation.constraints.Size;

public record MedicalRecordUpdateRequest(
        @Size(max = 2000) String diagnosis,
        @Size(max = 4000) String symptoms,
        @Size(max = 6000) String treatmentPlan,
        @Size(max = 4000) String medications,
        @Size(max = 6000) String notes) {}
