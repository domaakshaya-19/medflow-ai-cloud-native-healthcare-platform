package com.medflow.patient;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PatientApiTest{
 @Autowired MockMvc mvc;
 private static final String SECRET="test-only-secret-that-is-at-least-thirty-two-bytes-long";
 private String token(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).issuer("medflow-auth").claim("role",role).signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();}
 private String valid(String email,String dob){return "{\"firstName\":\"Synthetic\",\"lastName\":\"Patient\",\"dateOfBirth\":\""+dob+"\",\"phone\":\"1234567890\",\"email\":\""+email+"\"}";}
 @Test void rejectsInvalidExpiredAndWrongRoleAuthentication() throws Exception{
  mvc.perform(get("/api/patients/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
  String expired="Bearer "+Jwts.builder().subject(UUID.randomUUID().toString()).claim("role","PATIENT")
   .expiration(new Date(System.currentTimeMillis()-60000))
   .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
  mvc.perform(get("/api/patients/me").header("Authorization",expired)).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/patients/me").header("Authorization",token("DOCTOR",UUID.randomUUID()))).andExpect(status().isForbidden());
 }
 @Test void createReadUpdateDuplicateAndOwnership() throws Exception{
  UUID user=UUID.randomUUID();String auth=token("PATIENT",user);String body=valid("patient@example.test","2000-01-01");
  String location=mvc.perform(post("/api/patients").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.userId").value(user.toString())).andReturn().getResponse().getContentAsString();
  String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(location).get("id").asText();
  mvc.perform(get("/api/patients/me").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
  mvc.perform(get("/api/patients/"+id).header("Authorization",auth)).andExpect(status().isOk());
  mvc.perform(put("/api/patients/"+id).header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(valid("updated@example.test","2000-01-01"))).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("updated@example.test"));
  mvc.perform(post("/api/patients").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
  mvc.perform(get("/api/patients/"+id).header("Authorization",token("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
 }
 @Test void rejectsInvalidInputAndUnauthenticatedRequests() throws Exception{
  String auth=token("PATIENT",UUID.randomUUID());
  mvc.perform(get("/api/patients/me").header("Authorization",auth)).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Patient profile not found"));
  mvc.perform(post("/api/patients").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(valid("invalid","2000-01-01"))).andExpect(status().isBadRequest());
  mvc.perform(post("/api/patients").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content(valid("ok@example.test","2999-01-01"))).andExpect(status().isBadRequest());
  mvc.perform(get("/api/patients/me")).andExpect(status().isUnauthorized());
 }
 @Test void malformedRequestsReturnBadRequest() throws Exception{
  String auth=token("PATIENT",UUID.randomUUID());
  mvc.perform(post("/api/patients").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{invalid"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
  mvc.perform(get("/api/patients/not-a-uuid").header("Authorization",auth))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid request"));
 }
 @Test void byAuthUserAndDeleteEnforceOwnership() throws Exception{
  UUID user=UUID.randomUUID();String own=token("PATIENT",user),other=token("PATIENT",UUID.randomUUID());
  String created=mvc.perform(post("/api/patients").header("Authorization",own).contentType(MediaType.APPLICATION_JSON).content(valid("lookup@example.test","2000-01-01"))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
  String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(created).get("id").asText();
  mvc.perform(get("/api/patients/by-auth-user/"+user).header("Authorization",own)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
  mvc.perform(get("/api/patients/by-auth-user/"+user).header("Authorization",other)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/patients/"+id).header("Authorization",other)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/patients/"+id).header("Authorization",own)).andExpect(status().isNoContent());
  mvc.perform(get("/api/patients/"+id).header("Authorization",own)).andExpect(status().isNotFound());
 }
}
