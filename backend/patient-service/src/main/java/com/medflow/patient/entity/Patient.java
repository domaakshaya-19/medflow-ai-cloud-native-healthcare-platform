package com.medflow.patient.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity @Table(name="patients", uniqueConstraints=@UniqueConstraint(name="uk_patient_user",columnNames="user_id"))
public class Patient {
 @Id @GeneratedValue(strategy=GenerationType.UUID) public UUID id;
 @Column(name="user_id",nullable=false,updatable=false) public UUID userId;
 @Column(nullable=false,length=100) public String firstName;
 @Column(nullable=false,length=100) public String lastName;
 @Column(nullable=false) public LocalDate dateOfBirth;
 @Column(length=30) public String gender;
 @Column(nullable=false,length=30) public String phone;
 @Column(nullable=false,length=190) public String email;
 @Column(length=500) public String address;
 @Column(length=5) public String bloodGroup;
 @Column(length=100) public String emergencyContactName;
 @Column(length=30) public String emergencyContactPhone;
 @Column(length=2000) public String allergies;
 @Column(length=4000) public String medicalHistorySummary;
 @Column(nullable=false,updatable=false) public Instant createdAt;
 @Column(nullable=false) public Instant updatedAt;
 @PrePersist void create(){createdAt=updatedAt=Instant.now();}
 @PreUpdate void update(){updatedAt=Instant.now();}
}
