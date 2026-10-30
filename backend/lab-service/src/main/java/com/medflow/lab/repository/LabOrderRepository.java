package com.medflow.lab.repository;
import com.medflow.lab.entity.LabOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LabOrderRepository extends JpaRepository<LabOrder,UUID> {
    List<LabOrder> findByPatientIdOrderByOrderedAtDesc(UUID patientId);
    List<LabOrder> findByDoctorIdOrderByOrderedAtDesc(UUID doctorId);
}
