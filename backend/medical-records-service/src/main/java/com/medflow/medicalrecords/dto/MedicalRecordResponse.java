package com.medflow.medicalrecords.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MedicalRecordResponse(UUID id, UUID patientId, UUID doctorId, UUID appointmentId,
        String diagnosis, String symptoms, String treatmentPlan, String medications, String notes,
        LocalDate visitDate, Instant createdAt, Instant updatedAt) {}
