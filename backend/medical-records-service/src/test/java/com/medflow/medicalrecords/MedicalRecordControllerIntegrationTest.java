package com.medflow.medicalrecords;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.medflow.medicalrecords.client.ClinicalReferenceService;
import com.medflow.medicalrecords.entity.MedicalRecord;
import com.medflow.medicalrecords.exception.ApiException;
import com.medflow.medicalrecords.repository.MedicalRecordRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MedicalRecordControllerIntegrationTest {
    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";
    private final UUID patientUserId = UUID.randomUUID();
    private final UUID doctorUserId = UUID.randomUUID();
    private final UUID patientId = UUID.randomUUID();
    private final UUID doctorId = UUID.randomUUID();

    @Autowired MockMvc mvc;
    @Autowired MedicalRecordRepository repository;
    @MockitoBean ClinicalReferenceService references;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        reset(references);
    }

    @Test
    void unauthenticatedRequestIsRejectedAndHealthIsPublic() throws Exception {
        mvc.perform(get("/api/medical-records/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void doctorCanCreateAValidatedRecord() throws Exception {
        when(references.doctorProfileForCurrentUser(eq(doctorUserId), anyString())).thenReturn(doctorId);
        mvc.perform(post("/api/medical-records").header("Authorization", token(doctorUserId, "DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, UUID.randomUUID())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.doctorId").value(doctorId.toString()))
                .andExpect(jsonPath("$.diagnosis").value("Seasonal influenza"));
        verify(references).validateAppointment(any(), eq(patientId), eq(doctorId), anyString());
    }

    @Test
    void patientCannotCreateARecord() throws Exception {
        mvc.perform(post("/api/medical-records").header("Authorization", token(patientUserId, "PATIENT"))
                .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorCannotCreateForAnotherDoctor() throws Exception {
        when(references.doctorProfileForCurrentUser(eq(doctorUserId), anyString())).thenReturn(doctorId);
        mvc.perform(post("/api/medical-records").header("Authorization", token(doctorUserId, "DOCTOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson(patientId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(references, never()).validateAppointment(any(), any(), any(), anyString());
    }

    @Test
    void patientCanReadOnlyTheirOwnRecord() throws Exception {
        MedicalRecord own = save(patientId, doctorId, UUID.randomUUID());
        when(references.patientProfileForCurrentUser(eq(patientUserId), anyString())).thenReturn(patientId);
        mvc.perform(get("/api/medical-records/" + own.id).header("Authorization", token(patientUserId, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(own.id.toString()));
        when(references.patientProfileForCurrentUser(eq(patientUserId), anyString())).thenReturn(UUID.randomUUID());
        mvc.perform(get("/api/medical-records/" + own.id).header("Authorization", token(patientUserId, "PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientMeReturnsOwnRecords() throws Exception {
        save(patientId, doctorId, UUID.randomUUID());
        save(UUID.randomUUID(), doctorId, UUID.randomUUID());
        when(references.patientProfileForCurrentUser(eq(patientUserId), anyString())).thenReturn(patientId);
        mvc.perform(get("/api/medical-records/me").header("Authorization", token(patientUserId, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].patientId").value(patientId.toString()));
    }

    @Test
    void doctorCanListAndUpdateOnlyOwnRecords() throws Exception {
        MedicalRecord own = save(patientId, doctorId, UUID.randomUUID());
        MedicalRecord other = save(patientId, UUID.randomUUID(), UUID.randomUUID());
        when(references.doctorProfileForCurrentUser(eq(doctorUserId), anyString())).thenReturn(doctorId);
        String authorization = token(doctorUserId, "DOCTOR");
        mvc.perform(get("/api/medical-records/doctor/me").header("Authorization", authorization))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/medical-records/" + own.id).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"Recovered\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnosis").value("Recovered"));
        mvc.perform(put("/api/medical-records/" + other.id).header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"Forbidden edit\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void doctorPatientViewContainsOnlyRecordsCreatedByThatDoctor() throws Exception {
        save(patientId, doctorId, UUID.randomUUID());
        save(patientId, UUID.randomUUID(), UUID.randomUUID());
        when(references.doctorProfileForCurrentUser(eq(doctorUserId), anyString())).thenReturn(doctorId);
        mvc.perform(get("/api/medical-records/patient/" + patientId)
                .header("Authorization", token(doctorUserId, "DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doctorId").value(doctorId.toString()));
    }

    @Test
    void adminCanCreateListUpdateAndDeleteAnyRecord() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        String admin = token(UUID.randomUUID(), "ADMIN");
        String response = mvc.perform(post("/api/medical-records").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, appointmentId)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1"));
        mvc.perform(get("/api/medical-records/doctor/me").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(delete("/api/medical-records/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/medical-records/" + id).header("Authorization", admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateAppointmentIsRejected() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        save(patientId, doctorId, appointmentId);
        mvc.perform(post("/api/medical-records").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, appointmentId)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void invalidRequestIsRejectedBeforeClinicalValidation() throws Exception {
        mvc.perform(post("/api/medical-records").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.patientId").exists())
                .andExpect(jsonPath("$.fields.diagnosis").exists());
        verifyNoInteractions(references);
    }

    @Test
    void appointmentValidationFailureIsReturnedToCaller() throws Exception {
        doThrow(new ApiException(HttpStatus.CONFLICT, "Appointment is not in a valid state for a medical record"))
                .when(references).validateAppointment(any(), any(), any(), anyString());
        mvc.perform(post("/api/medical-records").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, UUID.randomUUID())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message", containsString("valid state")));
    }

    @Test
    void appointmentLookupHonorsRecordOwnership() throws Exception {
        UUID appointmentId = UUID.randomUUID();
        save(patientId, doctorId, appointmentId);
        when(references.patientProfileForCurrentUser(eq(patientUserId), anyString())).thenReturn(patientId);
        mvc.perform(get("/api/medical-records/appointment/" + appointmentId)
                .header("Authorization", token(patientUserId, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appointmentId").value(appointmentId.toString()));
    }

    private MedicalRecord save(UUID patient, UUID doctor, UUID appointment) {
        MedicalRecord record = new MedicalRecord();
        record.patientId = patient; record.doctorId = doctor; record.appointmentId = appointment;
        record.diagnosis = "Initial diagnosis"; record.visitDate = LocalDate.now();
        return repository.saveAndFlush(record);
    }

    private String token(UUID subject, String role) {
        Instant now = Instant.now();
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return "Bearer " + Jwts.builder().subject(subject.toString()).claim("email", role.toLowerCase() + "@example.test")
                .claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
                .signWith(key).compact();
    }

    private String createJson(UUID patient, UUID doctor, UUID appointment) {
        return """
                {"patientId":"%s","doctorId":"%s","appointmentId":"%s","diagnosis":"Seasonal influenza",
                "symptoms":"Fever and cough","treatmentPlan":"Rest and fluids","medications":"Paracetamol",
                "notes":"Follow up if symptoms persist","visitDate":"%s"}
                """.formatted(patient, doctor, appointment, LocalDate.now());
    }
}
