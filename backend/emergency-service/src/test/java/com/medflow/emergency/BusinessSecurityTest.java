package com.medflow.emergency;
import com.fasterxml.jackson.databind.ObjectMapper;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:emergency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql","app.jwt.secret=test-only-public-signing-fixture-never-use-in-production-0123456789","app.gateway=http://127.0.0.1:1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc class BusinessSecurityTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@MockitoSpyBean Access access;
 String jwt(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).claim("email","synthetic@example.test").claim("role",role).expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor("test-only-public-signing-fixture-never-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8))).compact();}

 @Test void requestsRequireAuthentication()throws Exception{mvc.perform(get("/api/emergency")).andExpect(status().isUnauthorized());}
 @Test void patientsCannotChoosePriorityOrRecipient()throws Exception{mvc.perform(post("/api/emergency").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"location\":\"Synthetic site\",\"summary\":\"Demo request\",\"administrativePriority\":\"ADMIN_URGENT\"}")).andExpect(status().isBadRequest());}
 @Test void historyOwnershipAssignmentAndTransitions()throws Exception{
  UUID patient=UUID.randomUUID(),staff=UUID.randomUUID();String json=mvc.perform(post("/api/emergency").header("Authorization",jwt("PATIENT",patient)).contentType("application/json").content("{\"location\":\"Synthetic site\",\"summary\":\"Demo administrative request\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();String id=mapper.readTree(json).path("id").asText();
  mvc.perform(get("/api/emergency/"+id).header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",staff)).contentType("application/json").content("{\"status\":\"CLOSED\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isConflict());
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",staff)).contentType("application/json").content("{\"status\":\"ASSIGNED\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(2));
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",UUID.randomUUID())).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isForbidden());
 }

}
