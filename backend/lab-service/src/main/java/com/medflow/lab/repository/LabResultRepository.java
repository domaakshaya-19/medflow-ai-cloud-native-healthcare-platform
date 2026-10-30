package com.medflow.lab.repository;
import com.medflow.lab.entity.LabResult;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LabResultRepository extends JpaRepository<LabResult,UUID> {
    Optional<LabResult> findByOrderId(UUID orderId);
    boolean existsByOrderId(UUID orderId);
    List<LabResult> findByPatientIdOrderByResultDateDesc(UUID patientId);
    List<LabResult> findByDoctorIdOrderByResultDateDesc(UUID doctorId);
}
