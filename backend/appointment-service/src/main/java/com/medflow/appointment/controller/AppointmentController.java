package com.medflow.appointment.controller;

import com.medflow.appointment.client.DoctorReferenceService;
import com.medflow.appointment.client.PatientReferenceService;
import com.medflow.appointment.dto.*;
import com.medflow.appointment.entity.Appointment;
import com.medflow.appointment.exception.ApiException;
import com.medflow.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {
    private final AppointmentService appointments;
    private final DoctorReferenceService doctors;
    private final PatientReferenceService patients;

    public AppointmentController(AppointmentService appointments, DoctorReferenceService doctors,
            PatientReferenceService patients) {
        this.appointments = appointments;
        this.doctors = doctors;
        this.patients = patients;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(@Valid @RequestBody AppointmentCreateRequest request, Authentication auth,
            @RequestHeader("Authorization") String authorization) {
        requireRole(auth, "PATIENT");
        UUID patientProfileId = patients.profileIdForCurrentPatient((UUID) auth.getPrincipal(), authorization);
        return appointments.create(patientProfileId, request, authorization);
    }

    @GetMapping("/me")
    public List<AppointmentResponse> me(Authentication auth,
            @RequestHeader("Authorization") String authorization) {
        requireRole(auth, "PATIENT");
        UUID patientProfileId = patients.profileIdForCurrentPatient((UUID) auth.getPrincipal(), authorization);
        return appointments.patientAppointments(patientProfileId);
    }

    @GetMapping("/doctor/me")
    public List<AppointmentResponse> doctorMe(Authentication auth, @RequestHeader("Authorization") String authorization) {
        requireRole(auth, "DOCTOR");
        return appointments.doctorAppointments(doctors.profileIdForCurrentDoctor(authorization));
    }

    @GetMapping("/doctor/me/queue")
    public List<AppointmentResponse> queue(Authentication auth, @RequestHeader("Authorization") String authorization) {
        requireRole(auth, "DOCTOR");
        return appointments.doctorQueue(doctors.profileIdForCurrentDoctor(authorization));
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable UUID id, Authentication auth,
            @RequestHeader("Authorization") String authorization) {
        checkAccess(appointments.appointment(id), auth, authorization);
        return appointments.get(id);
    }

    @PutMapping("/{id}")
    public AppointmentResponse update(@PathVariable UUID id, @Valid @RequestBody AppointmentUpdateRequest request,
            Authentication auth, @RequestHeader("Authorization") String authorization) {
        checkAccess(appointments.appointment(id), auth, authorization);
        return appointments.update(id, request, roleName(auth));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID id, Authentication auth,
            @RequestHeader("Authorization") String authorization) {
        checkAccess(appointments.appointment(id), auth, authorization);
        appointments.cancel(id, roleName(auth));
    }

    @GetMapping
    public List<AppointmentResponse> all(Authentication auth) {
        requireRole(auth, "ADMIN");
        return appointments.all();
    }

    private void checkAccess(Appointment appointment, Authentication auth, String authorization) {
        if (role(auth, "ADMIN")) return;
        if (role(auth, "PATIENT") && appointment.patientId.equals(
                patients.profileIdForCurrentPatient((UUID) auth.getPrincipal(), authorization))) return;
        if (role(auth, "DOCTOR") && appointment.doctorId.equals(doctors.profileIdForCurrentDoctor(authorization))) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private static String roleName(Authentication auth) {
        if (role(auth, "ADMIN")) return "ADMIN";
        if (role(auth, "DOCTOR")) return "DOCTOR";
        if (role(auth, "PATIENT")) return "PATIENT";
        throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private static boolean role(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private static void requireRole(Authentication auth, String role) {
        if (!role(auth, role)) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
