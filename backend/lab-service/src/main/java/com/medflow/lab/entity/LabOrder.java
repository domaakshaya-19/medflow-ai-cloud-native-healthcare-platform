package com.medflow.lab.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="lab_orders", indexes={
        @Index(name="idx_lab_order_patient",columnList="patient_id"),
        @Index(name="idx_lab_order_doctor",columnList="doctor_id"),
        @Index(name="idx_lab_order_appointment",columnList="appointment_id")})
public class LabOrder {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @Column(name="patient_id",nullable=false,updatable=false) public UUID patientId;
    @Column(name="doctor_id",nullable=false,updatable=false) public UUID doctorId;
    @Column(name="appointment_id",nullable=false,updatable=false) public UUID appointmentId;
    @Column(name="test_name",nullable=false,length=250) public String testName;
    @Column(length=2000) public String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) public LabOrderStatus status;
    @Column(name="ordered_at",nullable=false,updatable=false) public Instant orderedAt;
    @Column(nullable=false,updatable=false) public Instant createdAt;
    @Column(nullable=false) public Instant updatedAt;
    @PrePersist void create(){createdAt=updatedAt=orderedAt=Instant.now();}
    @PreUpdate void update(){updatedAt=Instant.now();}
}
