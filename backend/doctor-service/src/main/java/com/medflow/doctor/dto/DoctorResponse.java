package com.medflow.doctor.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DoctorResponse(UUID id, UUID userId, String firstName, String lastName,
        String specialization, String qualification, String licenseNumber, String phone,
        String email, String department, int experienceYears, BigDecimal consultationFee,
        boolean available, Instant createdAt, Instant updatedAt) {}
