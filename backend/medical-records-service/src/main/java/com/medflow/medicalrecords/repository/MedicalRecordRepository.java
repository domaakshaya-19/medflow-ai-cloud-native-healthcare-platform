package com.medflow.medicalrecords.repository;

import com.medflow.medicalrecords.entity.MedicalRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
    List<MedicalRecord> findByPatientIdOrderByVisitDateDescCreatedAtDesc(UUID patientId);
    List<MedicalRecord> findByDoctorIdOrderByVisitDateDescCreatedAtDesc(UUID doctorId);
    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);
    boolean existsByAppointmentId(UUID appointmentId);
}
