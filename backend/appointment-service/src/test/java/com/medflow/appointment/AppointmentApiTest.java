package com.medflow.appointment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medflow.appointment.client.DoctorReferenceService;
import com.medflow.appointment.client.PatientReferenceService;
import com.medflow.appointment.entity.Appointment;
import com.medflow.appointment.entity.AppointmentStatus;
import com.medflow.appointment.exception.ApiException;
import com.medflow.appointment.repository.AppointmentRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentApiTest {
    private static final String SECRET = "test-only-secret-that-is-at-least-thirty-two-bytes-long";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppointmentRepository appointments;
    @MockBean DoctorReferenceService doctors;
    @MockBean PatientReferenceService patients;

    @BeforeEach void clean() {
        appointments.deleteAll();
        reset(doctors, patients);
        when(patients.profileIdForCurrentPatient(any(UUID.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void receptionAndNursingSeeOnlyAggregates()throws Exception{for(String role:new String[]{"NURSE","RECEPTIONIST"}){String header=token(role,UUID.randomUUID());mvc.perform(get("/api/appointments/operations/summary").header("Authorization",header)).andExpect(status().isOk()).andExpect(jsonPath("$.totalAppointments").exists()).andExpect(jsonPath("$.patientId").doesNotExist());mvc.perform(get("/api/appointments").header("Authorization",header)).andExpect(status().isForbidden());}mvc.perform(get("/api/appointments/operations/summary").header("Authorization",token("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());}
    private String token(String role, UUID user) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(user.toString()).claim("email", "synthetic@example.test")
                .claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String request(UUID doctor, LocalDate date, String time, UUID claimedPatient) {
        String extra = claimedPatient == null ? "" : "\"patientId\":\"" + claimedPatient + "\",";
        return "{" + extra + "\"doctorId\":\"" + doctor + "\",\"appointmentDate\":\"" + date
                + "\",\"appointmentTime\":\"" + time + "\",\"reason\":\"Synthetic consultation\"}";
    }

    private String create(String jwt, UUID doctor, LocalDate date, String time) throws Exception {
        return mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(request(doctor, date, time, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    @Test void authenticationAndHealth() throws Exception {
        mvc.perform(get("/api/appointments/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/appointments/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test void patientIdentityOwnershipAndDuplicateBooking() throws Exception {
        UUID patientUser = UUID.randomUUID(), patientProfile = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID(), otherProfile = UUID.randomUUID(), doctor = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(2);
        String jwt = token("PATIENT", patientUser);
        String otherJwt = token("PATIENT", otherUser);
        when(patients.profileIdForCurrentPatient(patientUser, jwt)).thenReturn(patientProfile);
        when(patients.profileIdForCurrentPatient(otherUser, otherJwt)).thenReturn(otherProfile);
        String forged = request(doctor, date, "10:00", otherProfile);
        String body = mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(forged))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.patientId").value(patientProfile.toString()))
                .andExpect(jsonPath("$.queueNumber").value(1)).andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        verify(doctors).requireDoctor(doctor, jwt);
        mvc.perform(get("/api/appointments/me").header("Authorization", jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/appointments/" + id).header("Authorization", jwt)).andExpect(status().isOk());
        mvc.perform(get("/api/appointments/" + id).header("Authorization", otherJwt))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/appointments/" + id).header("Authorization", otherJwt))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(forged)).andExpect(status().isConflict());
        mvc.perform(get("/api/appointments").header("Authorization", jwt)).andExpect(status().isForbidden());
    }

    @Test void patientServiceLookupFailureIsReturnedCleanly() throws Exception {
        UUID patientUser = UUID.randomUUID(), doctor = UUID.randomUUID();
        String jwt = token("PATIENT", patientUser);
        when(patients.profileIdForCurrentPatient(patientUser, jwt))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "Patient Service unavailable"));
        mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(doctor, LocalDate.now().plusDays(2), "10:00", null)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("Patient Service unavailable"));
    }

    @Test void queueNumbersArePerDoctorAndDateAndCancellationDoesNotRenumber() throws Exception {
        UUID doctorA = UUID.randomUUID(), doctorB = UUID.randomUUID();
        LocalDate day = LocalDate.now().plusDays(2);
        String first = create(token("PATIENT", UUID.randomUUID()), doctorA, day, "10:00");
        create(token("PATIENT", UUID.randomUUID()), doctorA, day, "10:30");
        create(token("PATIENT", UUID.randomUUID()), doctorB, day, "10:00");
        mvc.perform(delete("/api/appointments/" + json.readTree(first).get("id").asText())
                .header("Authorization", token("ADMIN", UUID.randomUUID()))).andExpect(status().isNoContent());
        create(token("PATIENT", UUID.randomUUID()), doctorA, day, "11:00");
        org.junit.jupiter.api.Assertions.assertEquals(3, appointments.maxQueueNumber(doctorA, day));
        org.junit.jupiter.api.Assertions.assertEquals(1, appointments.maxQueueNumber(doctorB, day));
        org.junit.jupiter.api.Assertions.assertEquals(0, appointments.maxQueueNumber(doctorA, day.plusDays(1)));
    }

    @Test void doctorCanViewOwnAppointmentsAndChangeStatusOnly() throws Exception {
        UUID doctor = UUID.randomUUID(), otherDoctor = UUID.randomUUID();
        String owner = token("DOCTOR", UUID.randomUUID());
        String other = token("DOCTOR", UUID.randomUUID());
        when(doctors.profileIdForCurrentDoctor(owner)).thenReturn(doctor);
        when(doctors.profileIdForCurrentDoctor(other)).thenReturn(otherDoctor);
        String id = json.readTree(create(token("PATIENT", UUID.randomUUID()), doctor,
                LocalDate.now().plusDays(2), "10:00")).get("id").asText();
        mvc.perform(get("/api/appointments/doctor/me").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/appointments/" + id).header("Authorization", owner)).andExpect(status().isOk());
        mvc.perform(get("/api/appointments/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/" + id).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(put("/api/appointments/" + id).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/appointments/" + id).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Changed\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/appointments/doctor/me/queue").header("Authorization", owner))
                .andExpect(status().isOk());
    }

    @Test void adminCanListAndPatientCannotSetPrivilegedStatus() throws Exception {
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID();
        String patientJwt = token("PATIENT", patient);
        String id = json.readTree(create(patientJwt, doctor, LocalDate.now().plusDays(2), "10:00"))
                .get("id").asText();
        mvc.perform(get("/api/appointments").header("Authorization", token("ADMIN", UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/appointments/" + id).header("Authorization", patientJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/appointments/" + id).header("Authorization", patientJwt))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/appointments/" + id).header("Authorization", patientJwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(put("/api/appointments/" + id).header("Authorization", patientJwt)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void invalidDateAndMissingDoctor() throws Exception {
        UUID doctor = UUID.randomUUID(); String jwt = token("PATIENT", UUID.randomUUID());
        mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(doctor, LocalDate.now().minusDays(1), "10:00", null)))
                .andExpect(status().isBadRequest());
        doThrow(new ApiException(HttpStatus.NOT_FOUND, "Doctor not found")).when(doctors).requireDoctor(doctor, jwt);
        mvc.perform(post("/api/appointments").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request(doctor, LocalDate.now().plusDays(2), "10:00", null)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Doctor not found"));
    }

    @Test void currentDoctorQueueContainsOnlyTodaysActiveAppointments() throws Exception {
        UUID doctor = UUID.randomUUID();
        String doctorJwt = token("DOCTOR", UUID.randomUUID());
        when(doctors.profileIdForCurrentDoctor(doctorJwt)).thenReturn(doctor);
        for (int number = 1; number <= 3; number++) {
            Appointment a = new Appointment();
            a.patientId = UUID.randomUUID(); a.doctorId = doctor;
            a.appointmentDate = LocalDate.now(); a.appointmentTime = LocalTime.of(9 + number, 0);
            a.reason = "Synthetic consultation"; a.queueNumber = number;
            a.status = number == 2 ? AppointmentStatus.CANCELLED : AppointmentStatus.SCHEDULED;
            appointments.saveAndFlush(a);
        }
        mvc.perform(get("/api/appointments/doctor/me/queue").header("Authorization", doctorJwt))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].queueNumber").value(1))
                .andExpect(jsonPath("$[1].queueNumber").value(3));
    }
}
