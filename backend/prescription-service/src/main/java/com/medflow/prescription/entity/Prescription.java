package com.medflow.prescription.entity;
import jakarta.persistence.*;import java.time.*;import java.util.*;
@Entity @Table(name="prescriptions",indexes={@Index(name="idx_prescription_patient",columnList="patient_id"),@Index(name="idx_prescription_doctor",columnList="doctor_id"),@Index(name="idx_prescription_appointment",columnList="appointment_id",unique=true)})
public class Prescription{
 @Id @GeneratedValue(strategy=GenerationType.UUID)public UUID id;@Column(name="patient_id",nullable=false,updatable=false)public UUID patientId;@Column(name="doctor_id",nullable=false,updatable=false)public UUID doctorId;@Column(name="appointment_id",nullable=false,updatable=false)public UUID appointmentId;@Column(name="prescription_date",nullable=false)public LocalDate prescriptionDate;@Column(nullable=false,length=2000)public String diagnosis;@Column(length=6000)public String notes;@Enumerated(EnumType.STRING)@Column(nullable=false,length=30)public PrescriptionStatus status;@Column(nullable=false,updatable=false)public Instant createdAt;@Column(nullable=false)public Instant updatedAt;
 @OneToMany(mappedBy="prescription",cascade=CascadeType.ALL,orphanRemoval=true)@OrderBy("id ASC")public List<PrescriptionMedicine> medicines=new ArrayList<>();
 public void replaceMedicines(Collection<PrescriptionMedicine> replacements){medicines.clear();replacements.forEach(m->{m.prescription=this;medicines.add(m);});}
 @PrePersist void create(){createdAt=updatedAt=Instant.now();if(prescriptionDate==null)prescriptionDate=LocalDate.now();}@PreUpdate void update(){updatedAt=Instant.now();}
}
