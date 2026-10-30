package com.medflow.medicalrecords.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordCreateRequest(
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        @NotNull UUID appointmentId,
        @NotBlank @Size(max = 2000) String diagnosis,
        @Size(max = 4000) String symptoms,
        @Size(max = 6000) String treatmentPlan,
        @Size(max = 4000) String medications,
        @Size(max = 6000) String notes,
        @NotNull @PastOrPresent LocalDate visitDate) {}
