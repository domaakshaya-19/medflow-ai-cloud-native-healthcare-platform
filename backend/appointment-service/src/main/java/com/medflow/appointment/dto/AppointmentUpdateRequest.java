package com.medflow.appointment.dto;

import com.medflow.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentUpdateRequest(
        LocalDate appointmentDate,
        LocalTime appointmentTime,
        @Size(max = 500) String reason,
        @Size(max = 2000) String notes,
        AppointmentStatus status) {}
