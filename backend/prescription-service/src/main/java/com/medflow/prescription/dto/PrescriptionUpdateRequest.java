package com.medflow.prescription.dto;

import com.medflow.prescription.entity.PrescriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PrescriptionUpdateRequest(
        @Size(max = 2000) String diagnosis,
        @Size(max = 6000) String notes,
        PrescriptionStatus status,
        @Size(min = 1) List<@Valid MedicineRequest> medicines) {
}
