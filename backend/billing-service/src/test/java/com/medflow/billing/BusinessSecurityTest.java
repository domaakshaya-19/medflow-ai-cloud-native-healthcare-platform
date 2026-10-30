package com.medflow.billing;
import com.fasterxml.jackson.databind.ObjectMapper;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:billing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql","app.jwt.secret=test-only-public-signing-fixture-never-use-in-production-0123456789","app.gateway=http://127.0.0.1:1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc class BusinessSecurityTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@MockitoSpyBean Access access;
 String jwt(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).claim("email","synthetic@example.test").claim("role",role).expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor("test-only-public-signing-fixture-never-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8))).compact();}

 @Test void invoicesRequireAuthentication()throws Exception{mvc.perform(get("/api/billing/invoices")).andExpect(status().isUnauthorized());}
 @Test void patientCannotSetInvoiceCharges()throws Exception{mvc.perform(post("/api/billing/invoices").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content(invoice(UUID.randomUUID()))).andExpect(status().isForbidden());}
 String invoice(UUID patient){return "{\"patientUserId\":\""+patient+"\",\"currency\":\"INR\",\"items\":[{\"description\":\"Synthetic visit\",\"quantity\":2,\"unitPrice\":100.25}]}";}
 @Test void totalsOwnershipAndSyntheticPaymentAreEnforced()throws Exception{
  UUID patient=UUID.randomUUID();doReturn(mapper.readTree("{\"id\":\""+patient+"\"}")).when(access).remote(anyString(),anyString());
  String json=mvc.perform(post("/api/billing/invoices").header("Authorization",jwt("BILLING_STAFF",UUID.randomUUID())).contentType("application/json").content(invoice(patient))).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(200.5)).andReturn().getResponse().getContentAsString();String id=mapper.readTree(json).path("id").asText();
  mvc.perform(get("/api/billing/invoices/"+id).header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
  mvc.perform(post("/api/billing/invoices/"+id+"/demo-payment").header("Authorization",jwt("PATIENT",patient)).contentType("application/json").content("{\"confirmed\":false}")).andExpect(status().isBadRequest());
  mvc.perform(post("/api/billing/invoices/"+id+"/demo-payment").header("Authorization",jwt("PATIENT",patient)).contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("PAID_DEMO"));
  mvc.perform(post("/api/billing/invoices/"+id+"/demo-payment").header("Authorization",jwt("PATIENT",patient)).contentType("application/json").content("{\"confirmed\":true}")).andExpect(status().isConflict());
 }

}
