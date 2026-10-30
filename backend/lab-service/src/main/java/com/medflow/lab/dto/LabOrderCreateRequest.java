package com.medflow.lab.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record LabOrderCreateRequest(@NotNull UUID patientId,@NotNull UUID doctorId,@NotNull UUID appointmentId,
        @NotBlank @Size(max=250) String testName,@Size(max=2000) String instructions) {}
