package com.medflow.pharmacy;
import com.fasterxml.jackson.databind.ObjectMapper;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pharmacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql","app.jwt.secret=test-only-public-signing-fixture-never-use-in-production-0123456789","app.gateway=http://127.0.0.1:1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc class BusinessSecurityTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@MockitoSpyBean Access access;
 String jwt(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).claim("email","synthetic@example.test").claim("role",role).expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor("test-only-public-signing-fixture-never-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8))).compact();}

 @Test void catalogRequiresAuthentication()throws Exception{mvc.perform(get("/api/pharmacy/medications")).andExpect(status().isUnauthorized());}
 @Test void patientCannotCreateMedication()throws Exception{mvc.perform(post("/api/pharmacy/medications").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"name\":\"Synthetic item\",\"stock\":10,\"unit\":\"demo\"}")).andExpect(status().isForbidden());}
 @Test void stockMustBeNonnegative()throws Exception{mvc.perform(post("/api/pharmacy/medications").header("Authorization",jwt("PHARMACIST",UUID.randomUUID())).contentType("application/json").content("{\"name\":\"Synthetic item\",\"stock\":-1,\"unit\":\"demo\"}")).andExpect(status().isBadRequest());}
 @Test void dispensingIsAtomicAndCannotRepeat()throws Exception{
  UUID staff=UUID.randomUUID(),rx=UUID.randomUUID(),patient=UUID.randomUUID();String h=jwt("PHARMACIST",staff);
  String json=mvc.perform(post("/api/pharmacy/medications").header("Authorization",h).contentType("application/json").content("{\"name\":\"Synthetic "+rx+"\",\"stock\":10,\"unit\":\"demo\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();String medication=mapper.readTree(json).path("id").asText();
  doReturn(mapper.readTree("{\"id\":\""+rx+"\",\"patientId\":\""+patient+"\",\"status\":\"ACTIVE\",\"medicines\":[{\"medicineName\":\"Synthetic "+rx+"\"}]}")).when(access).remote(anyString(),anyString());
  String body="{\"prescriptionId\":\""+rx+"\",\"medicationId\":\""+medication+"\",\"quantity\":3,\"confirmed\":true}";
  mvc.perform(post("/api/pharmacy/dispensations").header("Authorization",h).contentType("application/json").content(body)).andExpect(status().isCreated());
  mvc.perform(post("/api/pharmacy/dispensations").header("Authorization",h).contentType("application/json").content(body)).andExpect(status().isConflict());
  assertEquals(7,access.db.queryForObject("SELECT stock FROM medications WHERE id=?",Integer.class,UUID.fromString(medication)));
 }

}
