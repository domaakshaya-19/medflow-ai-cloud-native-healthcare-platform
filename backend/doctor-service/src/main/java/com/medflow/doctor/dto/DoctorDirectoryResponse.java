package com.medflow.doctor.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DoctorDirectoryResponse(
        UUID id,
        String firstName,
        String lastName,
        String specialization,
        String qualification,
        String licenseNumber,
        String department,
        int experienceYears,
        BigDecimal consultationFee,
        boolean available) {
}
