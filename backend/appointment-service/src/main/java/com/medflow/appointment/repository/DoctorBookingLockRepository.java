package com.medflow.appointment.repository;
import com.medflow.appointment.entity.DoctorBookingLock;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface DoctorBookingLockRepository extends JpaRepository<DoctorBookingLock,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select l from DoctorBookingLock l where l.doctorId=:doctorId")
 Optional<DoctorBookingLock> lock(@Param("doctorId") UUID doctorId);
}
