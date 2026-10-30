package com.medflow.appointment;

import com.medflow.appointment.client.DoctorClient;
import com.medflow.appointment.client.PatientReferenceService;
import com.medflow.appointment.repository.AppointmentRepository;
import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DoctorLookupAuthorizationTest {
    @Autowired MockMvc mvc;
    @Autowired AppointmentRepository appointments;
    @MockBean DoctorClient doctors;
    @MockBean PatientReferenceService patients;
    private final UUID doctor = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    @BeforeEach void clean() {
        appointments.deleteAll();
        reset(doctors, patients);
        when(patients.profileIdForCurrentPatient(any(), anyString())).thenReturn(patient);
    }

    private String token(String role) {
        return "Bearer " + Jwts.builder().subject(UUID.randomUUID().toString())
                .claim("email", "synthetic@example.test").claim("role", role)
                .signWith(Keys.hmacShaKeyFor("test-only-secret-that-is-at-least-thirty-two-bytes-long"
                        .getBytes(StandardCharsets.UTF_8))).compact();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder booking() {
        return post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"doctorId\":\"" + doctor + "\",\"appointmentDate\":\""
                        + LocalDate.now().plusDays(2) + "\",\"appointmentTime\":\"10:00\",\"reason\":\"Consultation\"}");
    }

    @Test void authenticatedPatientTokenReachesDoctorClientWithoutReplacement() throws Exception {
        String authorization = token("PATIENT");
        when(doctors.directoryEntry(doctor, authorization)).thenReturn(new DoctorClient.DoctorDirectoryReference(doctor));
        mvc.perform(booking().header("Authorization", authorization)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patient.toString()));
        verify(doctors).directoryEntry(doctor, authorization);
        verifyNoMoreInteractions(doctors);
        assertEquals(1, appointments.count());
    }

    @Test void missingInvalidAndInsufficientRoleTokensNeverReachDownstream() throws Exception {
        mvc.perform(booking()).andExpect(status().isUnauthorized());
        mvc.perform(booking().header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        for (String role : new String[]{"DOCTOR", "ADMIN"}) {
            mvc.perform(booking().header("Authorization", token(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(doctors, patients);
        assertEquals(0, appointments.count());
    }

    @Test void downstreamDenialsFailClosedWithoutPrivilegedRetryOrBooking() throws Exception {
        for (Class<? extends FeignException> failure : java.util.List.of(
                FeignException.Unauthorized.class, FeignException.Forbidden.class)) {
            reset(doctors);
            String authorization = token("PATIENT");
            when(doctors.directoryEntry(doctor, authorization)).thenThrow(mock(failure));
            mvc.perform(booking().header("Authorization", authorization)).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.message").value("Doctor Service unavailable"));
            verify(doctors).directoryEntry(doctor, authorization);
            verifyNoMoreInteractions(doctors);
            assertEquals(0, appointments.count());
        }
    }
}
