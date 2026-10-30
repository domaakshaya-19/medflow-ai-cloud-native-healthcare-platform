package com.medflow.appointment.repository;

import com.medflow.appointment.entity.Appointment;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    List<Appointment> findByPatientIdOrderByAppointmentDateAscAppointmentTimeAsc(UUID patientId);
    List<Appointment> findByDoctorIdOrderByAppointmentDateAscQueueNumberAsc(UUID doctorId);
    List<Appointment> findByDoctorIdAndAppointmentDateOrderByQueueNumberAsc(UUID doctorId, LocalDate date);
    boolean existsByPatientIdAndDoctorIdAndAppointmentDateAndAppointmentTime(
            UUID patientId, UUID doctorId, LocalDate date, LocalTime time);
    boolean existsByPatientIdAndDoctorIdAndAppointmentDateAndAppointmentTimeAndIdNot(
            UUID patientId, UUID doctorId, LocalDate date, LocalTime time, UUID id);
    @Query("select coalesce(max(a.queueNumber), 0) from Appointment a where a.doctorId = :doctorId and a.appointmentDate = :date")
    int maxQueueNumber(@Param("doctorId") UUID doctorId, @Param("date") LocalDate date);
}
