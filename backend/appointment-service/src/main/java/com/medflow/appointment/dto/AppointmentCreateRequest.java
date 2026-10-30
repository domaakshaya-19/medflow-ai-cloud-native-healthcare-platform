package com.medflow.appointment.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentCreateRequest(
        @NotNull UUID doctorId,
        @NotNull LocalDate appointmentDate,
        @NotNull LocalTime appointmentTime,
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 2000) String notes) {}
