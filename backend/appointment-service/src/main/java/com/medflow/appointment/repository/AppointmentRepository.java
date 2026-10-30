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
    long countByStatus(com.medflow.appointment.entity.AppointmentStatus status);
    long countByAppointmentDate(LocalDate date);
    boolean existsByPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(UUID patientId,LocalDate date,LocalTime time,java.util.Collection<com.medflow.appointment.entity.AppointmentStatus> excluded);
    boolean existsByPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotInAndIdNot(UUID patientId,LocalDate date,LocalTime time,java.util.Collection<com.medflow.appointment.entity.AppointmentStatus> excluded,UUID id);
    boolean existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(UUID doctorId,LocalDate date,LocalTime time,java.util.Collection<com.medflow.appointment.entity.AppointmentStatus> excluded);
    boolean existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNotInAndIdNot(UUID doctorId,LocalDate date,LocalTime time,java.util.Collection<com.medflow.appointment.entity.AppointmentStatus> excluded,UUID id);
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
