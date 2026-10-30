package com.medflow.medicalrecords.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "medical_records", indexes = {
        @Index(name = "idx_medical_record_patient", columnList = "patient_id"),
        @Index(name = "idx_medical_record_doctor", columnList = "doctor_id"),
        @Index(name = "idx_medical_record_appointment", columnList = "appointment_id", unique = true)
})
public class MedicalRecord {
    @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
    @Column(name = "patient_id", nullable = false, updatable = false) public UUID patientId;
    @Column(name = "doctor_id", nullable = false, updatable = false) public UUID doctorId;
    @Column(name = "appointment_id", nullable = false, updatable = false) public UUID appointmentId;
    @Column(nullable = false, length = 2000) public String diagnosis;
    @Column(length = 4000) public String symptoms;
    @Column(name = "treatment_plan", length = 6000) public String treatmentPlan;
    @Column(length = 4000) public String medications;
    @Column(length = 6000) public String notes;
    @Column(name = "visit_date", nullable = false) public LocalDate visitDate;
    @Column(nullable = false, updatable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
    @PrePersist void create() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
