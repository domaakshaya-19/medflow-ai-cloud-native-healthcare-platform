package com.medflow.appointment.entity;
import jakarta.persistence.*;import java.util.UUID;
@Entity @Table(name="patient_booking_locks") public class PatientBookingLock{
 @Id public UUID patientId;
 public PatientBookingLock(){}
 public PatientBookingLock(UUID patientId){this.patientId=patientId;}
}
