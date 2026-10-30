package com.medflow.appointment.service;

import com.medflow.appointment.client.DoctorReferenceService;
import com.medflow.appointment.dto.*;
import com.medflow.appointment.entity.Appointment;
import com.medflow.appointment.entity.AppointmentStatus;
import com.medflow.appointment.exception.ApiException;
import com.medflow.appointment.repository.AppointmentRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {
    private final AppointmentRepository repository;
    private final DoctorReferenceService doctorReferences;
    private final com.medflow.appointment.repository.DoctorBookingLockRepository locks;
    private final com.medflow.appointment.repository.PatientBookingLockRepository patientLocks;
    private final jakarta.persistence.EntityManager entityManager;

    public AppointmentService(AppointmentRepository repository, DoctorReferenceService doctorReferences,com.medflow.appointment.repository.DoctorBookingLockRepository locks,com.medflow.appointment.repository.PatientBookingLockRepository patientLocks,jakarta.persistence.EntityManager entityManager) {
        this.repository = repository;
        this.doctorReferences = doctorReferences;
        this.locks=locks;this.patientLocks=patientLocks;this.entityManager=entityManager;
    }

    @Transactional
    public AppointmentResponse create(UUID patientId, AppointmentCreateRequest request, String authorization) {
        requireFuture(request.appointmentDate(), request.appointmentTime());
        doctorReferences.requireDoctor(request.doctorId(), authorization);
        lockDoctor(request.doctorId());
        lockPatient(patientId);
        if(repository.existsByPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(patientId,request.appointmentDate(),request.appointmentTime(),List.of(AppointmentStatus.CANCELLED,AppointmentStatus.NO_SHOW)))throw new ApiException(HttpStatus.CONFLICT,"Patient already has an appointment at this start time");
        if(repository.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNotIn(request.doctorId(),request.appointmentDate(),request.appointmentTime(),List.of(AppointmentStatus.CANCELLED,AppointmentStatus.NO_SHOW)))
            throw new ApiException(HttpStatus.CONFLICT,"Doctor already has an appointment at this start time");
        if (repository.existsByPatientIdAndDoctorIdAndAppointmentDateAndAppointmentTime(
                patientId, request.doctorId(), request.appointmentDate(), request.appointmentTime()))
            throw new ApiException(HttpStatus.CONFLICT, "Duplicate appointment booking");
        Appointment a = new Appointment();
        a.patientId = patientId;
        a.doctorId = request.doctorId();
        a.appointmentDate = request.appointmentDate();
        a.appointmentTime = request.appointmentTime();
        a.reason = request.reason().trim();
        a.notes = request.notes();
        a.status = AppointmentStatus.SCHEDULED;
        a.queueNumber = repository.maxQueueNumber(a.doctorId, a.appointmentDate) + 1;
        return view(repository.saveAndFlush(a));
    }

    @Transactional(readOnly = true)
    public Appointment appointment(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Appointment not found"));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) { return view(appointment(id)); }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> patientAppointments(UUID patientId) {
        return repository.findByPatientIdOrderByAppointmentDateAscAppointmentTimeAsc(patientId)
                .stream().map(AppointmentService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> doctorAppointments(UUID doctorId) {
        return repository.findByDoctorIdOrderByAppointmentDateAscQueueNumberAsc(doctorId)
                .stream().map(AppointmentService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> doctorQueue(UUID doctorId) {
        return repository.findByDoctorIdAndAppointmentDateOrderByQueueNumberAsc(doctorId, LocalDate.now())
                .stream().filter(a -> a.status == AppointmentStatus.SCHEDULED
                        || a.status == AppointmentStatus.CONFIRMED
                        || a.status == AppointmentStatus.IN_PROGRESS)
                .map(AppointmentService::view).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> all() { return repository.findAll().stream().map(AppointmentService::view).toList(); }

    @Transactional
    public AppointmentResponse update(UUID id, AppointmentUpdateRequest request, String role) {
        Appointment a = appointment(id);
        lockDoctor(a.doctorId);lockPatient(a.patientId);entityManager.refresh(a);
        if ("PATIENT".equals(role)) {
            if (request.status() != null)
                throw new ApiException(HttpStatus.FORBIDDEN, "Patients cannot set appointment status");
            if (a.status != AppointmentStatus.SCHEDULED && a.status != AppointmentStatus.CONFIRMED)
                throw new ApiException(HttpStatus.CONFLICT, "Only scheduled or confirmed appointments can be changed by a patient");
            updateDetails(a, request);
        } else if ("DOCTOR".equals(role)) {
            if (request.appointmentDate() != null || request.appointmentTime() != null
                    || request.reason() != null || request.notes() != null)
                throw new ApiException(HttpStatus.FORBIDDEN, "Doctors may change status only");
            if (request.status() == null)
                throw new ApiException(HttpStatus.BAD_REQUEST, "status is required");
            requireDoctorTransition(a.status, request.status());
            a.status = request.status();
        } else {
            updateDetails(a, request);
            if (request.status() != null) a.status = request.status();
        }
        if(a.status!=AppointmentStatus.CANCELLED&&a.status!=AppointmentStatus.NO_SHOW&&repository.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndStatusNotInAndIdNot(a.doctorId,a.appointmentDate,a.appointmentTime,List.of(AppointmentStatus.CANCELLED,AppointmentStatus.NO_SHOW),a.id))
            throw new ApiException(HttpStatus.CONFLICT,"Doctor already has an appointment at this start time");
        if(a.status!=AppointmentStatus.CANCELLED&&a.status!=AppointmentStatus.NO_SHOW&&repository.existsByPatientIdAndAppointmentDateAndAppointmentTimeAndStatusNotInAndIdNot(a.patientId,a.appointmentDate,a.appointmentTime,List.of(AppointmentStatus.CANCELLED,AppointmentStatus.NO_SHOW),a.id))throw new ApiException(HttpStatus.CONFLICT,"Patient already has an appointment at this start time");
        return view(repository.saveAndFlush(a));
    }

    @Transactional
    public void cancel(UUID id, String role) {
        Appointment a = appointment(id);
        lockDoctor(a.doctorId);lockPatient(a.patientId);entityManager.refresh(a);
        if (a.status == AppointmentStatus.CANCELLED)
            throw new ApiException(HttpStatus.CONFLICT, "Appointment is already cancelled");
        if ("PATIENT".equals(role) && a.status != AppointmentStatus.SCHEDULED
                && a.status != AppointmentStatus.CONFIRMED)
            throw new ApiException(HttpStatus.CONFLICT, "Only scheduled or confirmed appointments can be cancelled by a patient");
        if ("DOCTOR".equals(role) && (a.status == AppointmentStatus.COMPLETED || a.status == AppointmentStatus.NO_SHOW))
            throw new ApiException(HttpStatus.CONFLICT, "Completed appointments cannot be cancelled by a doctor");
        a.status = AppointmentStatus.CANCELLED;
        repository.saveAndFlush(a);
    }

    private void updateDetails(Appointment a, AppointmentUpdateRequest request) {
        LocalDate date = request.appointmentDate() == null ? a.appointmentDate : request.appointmentDate();
        LocalTime time = request.appointmentTime() == null ? a.appointmentTime : request.appointmentTime();
        boolean scheduleChanged = !date.equals(a.appointmentDate) || !time.equals(a.appointmentTime);
        if (scheduleChanged) {
            requireFuture(date, time);
            if (repository.existsByPatientIdAndDoctorIdAndAppointmentDateAndAppointmentTimeAndIdNot(
                    a.patientId, a.doctorId, date, time, a.id))
                throw new ApiException(HttpStatus.CONFLICT, "Duplicate appointment booking");
            if (!date.equals(a.appointmentDate))
                a.queueNumber = repository.maxQueueNumber(a.doctorId, date) + 1;
            a.appointmentDate = date;
            a.appointmentTime = time;
        }
        if (request.reason() != null) {
            if (request.reason().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "reason must not be blank");
            a.reason = request.reason().trim();
        }
        if (request.notes() != null) a.notes = request.notes();
    }

    private void lockDoctor(UUID doctorId){
        if(locks.lock(doctorId).isEmpty()){
            try{locks.saveAndFlush(new com.medflow.appointment.entity.DoctorBookingLock(doctorId));}
            catch(org.springframework.dao.DataIntegrityViolationException e){throw new ApiException(HttpStatus.CONFLICT,"Concurrent booking initialization; retry with fresh availability");}
        }
    }
    private void lockPatient(UUID patientId){
        if(patientLocks.lock(patientId).isEmpty())try{patientLocks.saveAndFlush(new com.medflow.appointment.entity.PatientBookingLock(patientId));}catch(org.springframework.dao.DataIntegrityViolationException e){throw new ApiException(HttpStatus.CONFLICT,"Concurrent patient booking initialization; retry");}
    }

    @Transactional(readOnly=true)
    public java.util.Map<String,Object> availability(UUID doctorId,LocalDate date,String authorization){
        if(date.isBefore(LocalDate.now()))throw new ApiException(HttpStatus.BAD_REQUEST,"Availability date must not be in the past");
        doctorReferences.requireDoctor(doctorId,authorization);
        var occupied=repository.findByDoctorIdAndAppointmentDateOrderByQueueNumberAsc(doctorId,date).stream().filter(a->a.status!=AppointmentStatus.CANCELLED&&a.status!=AppointmentStatus.NO_SHOW).map(a->a.appointmentTime).collect(java.util.stream.Collectors.toSet());
        var times=new java.util.ArrayList<String>();
        for(int i=0;i<48;i++){LocalTime time=LocalTime.of(i/2,(i%2)*30);if(LocalDateTime.of(date,time).isAfter(LocalDateTime.now())&&!occupied.contains(time))times.add(time.toString());}
        return java.util.Map.of("doctorId",doctorId,"date",date,"availableStartTimes",times,"gridMinutes",30,"workingHoursConfigured",false,"limitation","Unoccupied start times on a 30-minute grid, not a clinician roster. Durations and working hours are not modeled. Booking rechecks conflicts transactionally.");
    }

    private static void requireFuture(LocalDate date, LocalTime time) {
        if (date == null || time == null || !LocalDateTime.of(date, time).isAfter(LocalDateTime.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment date and time must be in the future");
    }

    private static void requireDoctorTransition(AppointmentStatus from, AppointmentStatus to) {
        boolean allowed = switch (from) {
            case SCHEDULED -> to == AppointmentStatus.CONFIRMED || to == AppointmentStatus.NO_SHOW || to == AppointmentStatus.CANCELLED;
            case CONFIRMED -> to == AppointmentStatus.IN_PROGRESS || to == AppointmentStatus.NO_SHOW || to == AppointmentStatus.CANCELLED;
            case IN_PROGRESS -> to == AppointmentStatus.COMPLETED || to == AppointmentStatus.CANCELLED;
            default -> false;
        };
        if (!allowed) throw new ApiException(HttpStatus.CONFLICT, "Invalid appointment status transition");
    }

    private static AppointmentResponse view(Appointment a) {
        return new AppointmentResponse(a.id, a.patientId, a.doctorId, a.appointmentDate,
                a.appointmentTime, a.reason, a.notes, a.status, a.queueNumber, a.createdAt, a.updatedAt);
    }
}
