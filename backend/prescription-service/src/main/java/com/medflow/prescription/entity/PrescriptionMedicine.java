package com.medflow.prescription.entity;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="prescription_medicines",indexes=@Index(name="idx_medicine_prescription",columnList="prescription_id"))
public class PrescriptionMedicine{@Id @GeneratedValue(strategy=GenerationType.UUID)public UUID id;@ManyToOne(fetch=FetchType.LAZY,optional=false)@JoinColumn(name="prescription_id",nullable=false)public Prescription prescription;@Column(name="medicine_name",nullable=false,length=250)public String medicineName;@Column(nullable=false,length=150)public String dosage;@Column(nullable=false,length=150)public String frequency;@Column(nullable=false,length=150)public String duration;@Column(length=1000)public String instructions;}
