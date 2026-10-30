package com.medflow.prescription.repository;

import com.medflow.prescription.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    List<Prescription> findByPatientIdOrderByPrescriptionDateDescCreatedAtDesc(UUID patientId);
    List<Prescription> findByDoctorIdOrderByPrescriptionDateDescCreatedAtDesc(UUID doctorId);
    Optional<Prescription> findByAppointmentId(UUID appointmentId);
    boolean existsByAppointmentId(UUID appointmentId);
}
