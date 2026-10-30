package com.medflow.appointment.entity;
import jakarta.persistence.*;
import java.util.UUID;
/** Database row lock serializes a doctor's booking mutations across service instances. */
@Entity @Table(name="doctor_booking_locks") public class DoctorBookingLock {
 @Id public UUID doctorId;
 public DoctorBookingLock(){}
 public DoctorBookingLock(UUID doctorId){this.doctorId=doctorId;}
}
