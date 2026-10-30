package com.medflow.prescription;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.medflow.prescription.client.ClinicalReferenceService;
import com.medflow.prescription.entity.Prescription;
import com.medflow.prescription.entity.PrescriptionMedicine;
import com.medflow.prescription.entity.PrescriptionStatus;
import com.medflow.prescription.exception.ApiException;
import com.medflow.prescription.repository.PrescriptionRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PrescriptionApiTest {
    static final String SECRET = "test-secret-that-is-at-least-thirty-two-bytes-long";
    @Autowired MockMvc mvc;
    @Autowired PrescriptionRepository repository;
    @MockitoBean ClinicalReferenceService references;
    UUID patientUser, doctorUser, patientId, doctorId;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        reset(references);
        patientUser = UUID.randomUUID(); doctorUser = UUID.randomUUID();
        patientId = UUID.randomUUID(); doctorId = UUID.randomUUID();
        when(references.patientProfileForCurrentUser(eq(patientUser), anyString())).thenReturn(patientId);
        when(references.doctorProfileForCurrentUser(eq(doctorUser), anyString())).thenReturn(doctorId);
    }

    @Test void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test void unauthenticatedApiIsRejected() throws Exception {
        mvc.perform(get("/api/prescriptions/me")).andExpect(status().isUnauthorized());
    }

    @Test void doctorCreatesPrescriptionForOwnProfile() throws Exception {
        UUID appointment = UUID.randomUUID(); String auth = token(doctorUser, "DOCTOR");
        mvc.perform(post("/api/prescriptions").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(patientId, doctorId, appointment)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.medicines", hasSize(1)));
        verify(references).validatePrescriptionReferences(appointment, patientId, doctorId, auth);
    }

    @Test void patientCannotCreatePrescription() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", token(patientUser, "PATIENT"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, UUID.randomUUID())))
                .andExpect(status().isForbidden());
        verify(references, never()).validatePrescriptionReferences(any(), any(), any(), anyString());
    }

    @Test void doctorCannotCreateForAnotherDoctorProfile() throws Exception {
        mvc.perform(post("/api/prescriptions").header("Authorization", token(doctorUser, "DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanCreateForAnyValidProfiles() throws Exception {
        UUID patient = UUID.randomUUID(), doctor = UUID.randomUUID(), appointment = UUID.randomUUID();
        mvc.perform(post("/api/prescriptions").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(patient, doctor, appointment)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.patientId").value(patient.toString()));
    }

    @Test void patientViewsOnlyOwnPrescriptions() throws Exception {
        Prescription own = saved(patientId, doctorId); saved(UUID.randomUUID(), doctorId);
        mvc.perform(get("/api/prescriptions/patient/me").header("Authorization", token(patientUser, "PATIENT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(own.id.toString()));
    }

    @Test void patientCannotViewAnotherPatientsPrescription() throws Exception {
        Prescription other = saved(UUID.randomUUID(), doctorId);
        mvc.perform(get("/api/prescriptions/" + other.id).header("Authorization", token(patientUser, "PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test void doctorViewsOnlyOwnPrescriptions() throws Exception {
        saved(patientId, doctorId); saved(patientId, UUID.randomUUID());
        mvc.perform(get("/api/prescriptions/doctor/me").header("Authorization", token(doctorUser, "DOCTOR")))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test void doctorCannotUpdateAnotherDoctorsPrescription() throws Exception {
        Prescription other = saved(patientId, UUID.randomUUID());
        mvc.perform(put("/api/prescriptions/" + other.id).header("Authorization", token(doctorUser, "DOCTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"diagnosis\":\"Updated\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanListUpdateAndCancel() throws Exception {
        Prescription prescription = saved(patientId, doctorId); String auth = token(UUID.randomUUID(), "ADMIN");
        mvc.perform(get("/api/prescriptions").header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(put("/api/prescriptions/" + prescription.id).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(delete("/api/prescriptions/" + prescription.id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test void cancelledPrescriptionCannotBeReactivated() throws Exception {
        Prescription prescription = saved(patientId, doctorId); prescription.status = PrescriptionStatus.CANCELLED; repository.saveAndFlush(prescription);
        mvc.perform(put("/api/prescriptions/" + prescription.id).header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict());
    }

    @Test void duplicateAppointmentIsRejected() throws Exception {
        Prescription existing = saved(patientId, doctorId);
        mvc.perform(post("/api/prescriptions").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, existing.appointmentId)))
                .andExpect(status().isConflict());
    }

    @Test void medicineValidationIsEnforced() throws Exception {
        String invalid = createJson(patientId, doctorId, UUID.randomUUID()).replace("Paracetamol", "");
        mvc.perform(post("/api/prescriptions").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields['medicines[0].medicineName']").exists());
    }

    @Test void invalidPatientReferenceFailureIsReturnedCleanly() throws Exception {
        referenceFailure("Patient profile not found", HttpStatus.NOT_FOUND);
    }

    @Test void invalidDoctorReferenceFailureIsReturnedCleanly() throws Exception {
        referenceFailure("Doctor profile not found", HttpStatus.NOT_FOUND);
    }

    @Test void invalidAppointmentStateFailureIsReturnedCleanly() throws Exception {
        referenceFailure("Appointment is not in a valid state for a prescription", HttpStatus.CONFLICT);
    }

    private void referenceFailure(String message, HttpStatus status) throws Exception {
        doThrow(new ApiException(status, message)).when(references).validatePrescriptionReferences(any(), any(), any(), anyString());
        mvc.perform(post("/api/prescriptions").header("Authorization", token(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(patientId, doctorId, UUID.randomUUID())))
                .andExpect(status().is(status.value())).andExpect(jsonPath("$.message").value(message));
    }

    private Prescription saved(UUID patient, UUID doctor) {
        Prescription p = new Prescription(); p.patientId = patient; p.doctorId = doctor;
        p.appointmentId = UUID.randomUUID(); p.prescriptionDate = LocalDate.now(); p.diagnosis = "Viral fever"; p.status = PrescriptionStatus.ACTIVE;
        PrescriptionMedicine medicine = new PrescriptionMedicine(); medicine.medicineName = "Paracetamol";
        medicine.dosage = "500 mg"; medicine.frequency = "Twice daily"; medicine.duration = "Five days";
        p.replaceMedicines(List.of(medicine)); return repository.saveAndFlush(p);
    }

    private String token(UUID subject, String role) {
        Instant now = Instant.now();
        return "Bearer " + Jwts.builder().subject(subject.toString()).claim("email", role.toLowerCase() + "@example.test")
                .claim("role", role).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String createJson(UUID patient, UUID doctor, UUID appointment) {
        return "{\"patientId\":\"" + patient + "\",\"doctorId\":\"" + doctor + "\",\"appointmentId\":\"" + appointment
                + "\",\"diagnosis\":\"Viral fever\",\"notes\":\"Hydrate\",\"medicines\":[{\"medicineName\":\"Paracetamol\","
                + "\"dosage\":\"500 mg\",\"frequency\":\"Twice daily\",\"duration\":\"Five days\",\"instructions\":\"After food\"}]}";
    }
}
