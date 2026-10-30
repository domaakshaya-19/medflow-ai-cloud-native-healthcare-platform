package com.medflow.patient.repository;

import com.medflow.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PatientRepository extends JpaRepository<Patient,UUID> {
 Optional<Patient> findByUserId(UUID userId);
 boolean existsByUserId(UUID userId);
}
