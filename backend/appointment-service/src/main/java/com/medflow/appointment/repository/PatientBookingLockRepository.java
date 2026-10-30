package com.medflow.appointment.repository;
import com.medflow.appointment.entity.PatientBookingLock;import jakarta.persistence.LockModeType;import java.util.*;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface PatientBookingLockRepository extends JpaRepository<PatientBookingLock,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from PatientBookingLock l where l.patientId=:patientId") Optional<PatientBookingLock> lock(@Param("patientId") UUID patientId);
}
