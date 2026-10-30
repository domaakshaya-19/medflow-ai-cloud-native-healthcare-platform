package com.medflow.prescription.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PrescriptionCreateRequest(
        @NotNull UUID patientId,
        @NotNull UUID doctorId,
        @NotNull UUID appointmentId,
        @NotBlank @Size(max = 2000) String diagnosis,
        @Size(max = 6000) String notes,
        @NotEmpty List<@Valid MedicineRequest> medicines) {
}
