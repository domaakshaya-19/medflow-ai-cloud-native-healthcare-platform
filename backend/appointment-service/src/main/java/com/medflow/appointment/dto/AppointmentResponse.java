package com.medflow.appointment.dto;

import com.medflow.appointment.entity.AppointmentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record AppointmentResponse(UUID id, UUID patientId, UUID doctorId, LocalDate appointmentDate,
        LocalTime appointmentTime, String reason, String notes, AppointmentStatus status,
        Integer queueNumber, Instant createdAt, Instant updatedAt) {}
