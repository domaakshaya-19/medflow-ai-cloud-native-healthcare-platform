package com.medflow.doctor.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "doctors", uniqueConstraints = {
        @UniqueConstraint(name = "uk_doctor_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_doctor_license", columnNames = "license_number")
})
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.UUID) public UUID id;
    @Column(name = "user_id", nullable = false, updatable = false) public UUID userId;
    @Column(nullable = false, length = 100) public String firstName;
    @Column(nullable = false, length = 100) public String lastName;
    @Column(nullable = false, length = 120) public String specialization;
    @Column(nullable = false, length = 200) public String qualification;
    @Column(name = "license_number", nullable = false, length = 80) public String licenseNumber;
    @Column(length = 30) public String phone;
    @Column(nullable = false, length = 190) public String email;
    @Column(length = 120) public String department;
    @Column(nullable = false) public int experienceYears;
    @Column(nullable = false, precision = 12, scale = 2) public BigDecimal consultationFee;
    @Column(nullable = false) public boolean available;
    @Column(nullable = false, updatable = false) public Instant createdAt;
    @Column(nullable = false) public Instant updatedAt;
    @PrePersist void create() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void update() { updatedAt = Instant.now(); }
}
