package com.medflow.lab.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="lab_results",indexes={
        @Index(name="idx_lab_result_order",columnList="order_id",unique=true),
        @Index(name="idx_lab_result_patient",columnList="patient_id"),
        @Index(name="idx_lab_result_doctor",columnList="doctor_id")})
public class LabResult {
    @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
    @Column(name="order_id",nullable=false,updatable=false) public UUID orderId;
    @Column(name="patient_id",nullable=false,updatable=false) public UUID patientId;
    @Column(name="doctor_id",nullable=false,updatable=false) public UUID doctorId;
    @Column(name="result_summary",nullable=false,length=2000) public String resultSummary;
    @Column(length=6000) public String details;
    @Column(name="reference_range",length=1000) public String referenceRange;
    @Column(nullable=false) public boolean abnormal;
    @Column(name="result_date",nullable=false) public Instant resultDate;
    @Column(nullable=false,updatable=false) public Instant createdAt;
    @Column(nullable=false) public Instant updatedAt;
    @PrePersist void create(){createdAt=updatedAt=Instant.now();if(resultDate==null)resultDate=createdAt;}
    @PreUpdate void update(){updatedAt=Instant.now();}
}
