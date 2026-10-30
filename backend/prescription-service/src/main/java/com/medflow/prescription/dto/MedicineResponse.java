package com.medflow.prescription.dto;

import java.util.UUID;

public record MedicineResponse(UUID id, String medicineName, String dosage, String frequency,
                               String duration, String instructions) {
}
