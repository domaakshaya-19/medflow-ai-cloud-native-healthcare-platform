package com.medflow.appointment.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "appointments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_appointment_queue", columnNames = {"doctor_id", "appointment_date", "queue_number"}),
        @UniqueConstraint(name = "uk_appointment_booking", columnNames = {"patient_id", "doctor_id", "appointment_date", "appointment_time"})
})
public class Appointment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
    @Column(name = "patient_id", nullable = false, updatable = false) public UUID patientId;
    @Column(name = "doctor_id", nullable = false, updatable = false) public UUID doctorId;
    @Column(name = "appointment_date", nullable = false) public LocalDate appointmentDate;
    @Column(name = "appointment_time", nullable = false) public LocalTime appointmentTime;
    @Column(nullable = false, length = 500) public String reason;
    @Column(length = 2000) public String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public AppointmentStatus status;
    @Column(name = "queue_number", nullable = false) public Integer queueNumber;
    @Column(nullable = false, updatable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
    @PrePersist void create() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
