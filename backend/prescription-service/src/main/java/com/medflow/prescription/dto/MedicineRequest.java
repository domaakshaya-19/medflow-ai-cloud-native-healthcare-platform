package com.medflow.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MedicineRequest(
        @NotBlank @Size(max = 250) String medicineName,
        @NotBlank @Size(max = 150) String dosage,
        @NotBlank @Size(max = 150) String frequency,
        @NotBlank @Size(max = 150) String duration,
        @Size(max = 1000) String instructions) {
}
