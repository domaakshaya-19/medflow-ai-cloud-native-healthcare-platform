package com.medflow.doctor.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record DoctorUpdateRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Size(max = 120) String specialization,
        @NotBlank @Size(max = 200) String qualification,
        @NotBlank @Size(max = 80) String licenseNumber,
        @Pattern(regexp = "^[+0-9 ()-]{7,30}$") String phone,
        @NotBlank @Email @Size(max = 190) String email,
        @Size(max = 120) String department,
        @Min(0) int experienceYears,
        @NotNull @DecimalMin("0.00") BigDecimal consultationFee,
        boolean available) {}
