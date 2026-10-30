package com.medflow.doctor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medflow.doctor.repository.DoctorRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DoctorApiTest {
    private static final String SECRET = "test-only-secret-that-is-at-least-thirty-two-bytes-long";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DoctorRepository doctors;

    @BeforeEach void clean() { doctors.deleteAll(); }

    private String token(String role, UUID id) {
        return "Bearer " + Jwts.builder().subject(id.toString()).claim("email", "synthetic@example.test")
                .claim("role", role).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String request(UUID userId, int experienceYears, String fee) {
        String id = userId == null ? "" : "\"userId\":\"" + userId + "\",";
        return "{" + id + "\"firstName\":\"Synthetic\",\"lastName\":\"Doctor\","
                + "\"specialization\":\"Cardiology\",\"qualification\":\"MD\","
                + "\"licenseNumber\":\"TEST-001\",\"phone\":\"1234567890\","
                + "\"email\":\"doctor@example.test\",\"department\":\"Cardiology\","
                + "\"experienceYears\":" + experienceYears + ",\"consultationFee\":" + fee
                + ",\"available\":true}";
    }

    @Test void doctorCanCreateReadUpdateAndDeleteOwnProfile() throws Exception {
        UUID user = UUID.randomUUID(); String jwt = token("DOCTOR", user);
        String body = mvc.perform(post("/api/doctors").header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(user.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(get("/api/doctors/me").header("Authorization", jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/doctors/" + id).header("Authorization", jwt)).andExpect(status().isOk());
        mvc.perform(put("/api/doctors/" + id).header("Authorization", jwt)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 6, "350.00")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.experienceYears").value(6));
        mvc.perform(delete("/api/doctors/" + id).header("Authorization", jwt)).andExpect(status().isNoContent());
        mvc.perform(get("/api/doctors/" + id).header("Authorization", jwt)).andExpect(status().isNotFound());
    }

    @Test void ownershipAndRoleChecksAreEnforced() throws Exception {
        UUID user = UUID.randomUUID(); String owner = token("DOCTOR", user);
        String body = mvc.perform(post("/api/doctors").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        String other = token("DOCTOR", UUID.randomUUID());
        mvc.perform(get("/api/doctors/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(put("/api/doctors/" + id).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/doctors/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(post("/api/doctors").header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content(request(user, 5, "300.00")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/doctors").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/doctors").header("Authorization", owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/doctors/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/doctors").header("Authorization", token("PATIENT", UUID.randomUUID()))
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isForbidden());
    }

    @Test void adminCanManageProfilesAndInvalidRequestsAreRejected() throws Exception {
        String admin = token("ADMIN", UUID.randomUUID()); UUID doctor = UUID.randomUUID();
        String body = mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(doctor, 5, "300.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mvc.perform(get("/api/doctors").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));
        mvc.perform(delete("/api/doctors/" + id).header("Authorization", admin)).andExpect(status().isNoContent());
        mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(null, 5, "300.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID(), -1, "300.00")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID(), 1, "-1.00")))
                .andExpect(status().isBadRequest());
    }

    @Test void invalidJwtAndHealthEndpoint() throws Exception {
        mvc.perform(get("/api/doctors/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test void patientCanDiscoverDoctorsWithoutPrivateAccountFields() throws Exception {
        String admin = token("ADMIN", UUID.randomUUID());
        mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID(), 5, "300.00")))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/doctors/directory").header("Authorization", token("PATIENT", UUID.randomUUID())))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].firstName").value("Synthetic"))
                .andExpect(jsonPath("$[0].specialization").value("Cardiology"))
                .andExpect(jsonPath("$[0].consultationFee").value(300.0))
                .andExpect(jsonPath("$[0].userId").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].createdAt").doesNotExist());
    }

    @Test void directorySupportsCaseInsensitiveSpecializationFilteringAndDetails() throws Exception {
        String admin = token("ADMIN", UUID.randomUUID());
        String body = mvc.perform(post("/api/doctors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(request(UUID.randomUUID(), 5, "300.00")))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        String patient = token("PATIENT", UUID.randomUUID());
        mvc.perform(get("/api/doctors/directory").param("specialization", "cardio").header("Authorization", patient))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/doctors/directory").param("specialization", "neurology").header("Authorization", patient))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/doctors/directory/" + id).header("Authorization", patient))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
    }

    @Test void directoryRequiresAuthenticationAndAnAuthorizedRole() throws Exception {
        mvc.perform(get("/api/doctors/directory")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/doctors/directory").header("Authorization", token("NURSE", UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }
}
