package com.medflow.doctor.repository;

import com.medflow.doctor.entity.Doctor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    Optional<Doctor> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    boolean existsByLicenseNumber(String licenseNumber);
    List<Doctor> findAllByOrderByLastNameAscFirstNameAsc();
    List<Doctor> findBySpecializationContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(String specialization);
}
