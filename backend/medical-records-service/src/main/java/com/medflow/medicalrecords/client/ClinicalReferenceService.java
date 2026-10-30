package com.medflow.medicalrecords.client;

import com.medflow.medicalrecords.exception.ApiException;
import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClinicalReferenceService {
    private static final UUID SERVICE_ID = UUID.nameUUIDFromBytes("medflow-medical-records-service".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> VALID_APPOINTMENT_STATES = Set.of("IN_PROGRESS", "COMPLETED");
    private final PatientClient patients;
    private final DoctorClient doctors;
    private final AppointmentClient appointments;
    private final SecretKey key;

    public ClinicalReferenceService(PatientClient patients, DoctorClient doctors, AppointmentClient appointments,
            @Value("${app.jwt.secret}") String secret) {
        this.patients = patients; this.doctors = doctors; this.appointments = appointments;
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public UUID patientProfileForCurrentUser(UUID authUserId, String authorization) {
        try {
            PatientClient.PatientReference patient = patients.byAuthUser(authUserId, authorization);
            if (patient == null || patient.id() == null || !authUserId.equals(patient.userId()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Patient Service response");
            return patient.id();
        } catch (FeignException.NotFound e) { throw new ApiException(HttpStatus.NOT_FOUND, "Patient profile not found"); }
        catch (FeignException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Patient Service unavailable"); }
    }

    public UUID doctorProfileForCurrentUser(UUID authUserId, String authorization) {
        try {
            DoctorClient.DoctorReference doctor = doctors.me(authorization);
            if (doctor == null || doctor.id() == null || !authUserId.equals(doctor.userId()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Doctor Service response");
            return doctor.id();
        } catch (FeignException.NotFound e) { throw new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"); }
        catch (FeignException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Doctor Service unavailable"); }
    }

    public void validateAppointment(UUID appointmentId, UUID requestedPatientProfileId, UUID requestedDoctorProfileId,
            String callerAuthorization) {
        try {
            AppointmentClient.AppointmentReference appointment = appointments.get(appointmentId, callerAuthorization);
            if (appointment == null || !appointmentId.equals(appointment.id()))
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Invalid Appointment Service response");
            PatientClient.PatientReference patient = patients.byId(appointment.patientId(), internalAuthorization());
            if (patient == null || !appointment.patientId().equals(patient.id())
                    || !requestedPatientProfileId.equals(patient.id())
                    || !requestedDoctorProfileId.equals(appointment.doctorId()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Appointment does not match the specified patient and doctor");
            if (!VALID_APPOINTMENT_STATES.contains(appointment.status()))
                throw new ApiException(HttpStatus.CONFLICT, "Appointment is not in a valid state for a medical record");
        } catch (FeignException.NotFound e) { throw new ApiException(HttpStatus.NOT_FOUND, "Appointment or patient profile not found"); }
        catch (FeignException e) { throw new ApiException(HttpStatus.BAD_GATEWAY, "Clinical reference service unavailable"); }
    }

    private String internalAuthorization() {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(SERVICE_ID.toString())
                .claim("email", "medical-records-service@internal.medflow").claim("role", "ADMIN")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();
    }
}
