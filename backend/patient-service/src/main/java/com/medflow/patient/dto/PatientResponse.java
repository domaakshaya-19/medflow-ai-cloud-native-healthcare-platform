package com.medflow.patient.dto;

import java.time.*;
import java.util.UUID;

public record PatientResponse(UUID id, UUID userId, String firstName, String lastName, LocalDate dateOfBirth,
 String gender, String phone, String email, String address, String bloodGroup, String emergencyContactName,
 String emergencyContactPhone, String allergies, String medicalHistorySummary, Instant createdAt, Instant updatedAt) {}
