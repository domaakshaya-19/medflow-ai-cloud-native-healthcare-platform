package com.medflow.notification;
import com.fasterxml.jackson.databind.ObjectMapper;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:notification;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql","app.jwt.secret=test-only-public-signing-fixture-never-use-in-production-0123456789","app.gateway=http://127.0.0.1:1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc class BusinessSecurityTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@MockitoSpyBean Access access;
 String jwt(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).claim("email","synthetic@example.test").claim("role",role).expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor("test-only-public-signing-fixture-never-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8))).compact();}

 @Test void notificationsRequireAuthentication()throws Exception{mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());}
 @Test void preferenceOwnerCannotBeInjected()throws Exception{mvc.perform(put("/api/notifications/preferences").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"enabled\":true,\"reminders\":true,\"ownerUserId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isBadRequest());}
 @Test void notificationsAreDeduplicatedAndOwned()throws Exception{
  UUID patient=UUID.randomUUID(),appointment=UUID.randomUUID();String h=jwt("PATIENT",patient);doReturn(mapper.readTree("{\"appointmentDate\":\"2099-01-01\",\"appointmentTime\":\"10:00:00\",\"status\":\"SCHEDULED\"}")).when(access).remote(anyString(),anyString());String body="{\"appointmentId\":\""+appointment+"\"}";
  for(int i=0;i<2;i++)mvc.perform(post("/api/notifications/events/appointment").header("Authorization",h).contentType("application/json").content(body)).andExpect(status().isOk());
  String json=mvc.perform(get("/api/notifications").header("Authorization",h)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString();String id=mapper.readTree(json).get(0).path("id").asText();
  mvc.perform(put("/api/notifications/"+id+"/read").header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
  mvc.perform(put("/api/notifications/"+id+"/read").header("Authorization",h)).andExpect(status().isOk()).andExpect(jsonPath("$.isRead").value(true));
 }

}
