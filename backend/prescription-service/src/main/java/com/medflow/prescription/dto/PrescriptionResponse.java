package com.medflow.prescription.dto;

import com.medflow.prescription.entity.PrescriptionStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(UUID id, UUID patientId, UUID doctorId, UUID appointmentId,
                                   LocalDate prescriptionDate, String diagnosis, String notes,
                                   PrescriptionStatus status, List<MedicineResponse> medicines,
                                   Instant createdAt, Instant updatedAt) {
}
