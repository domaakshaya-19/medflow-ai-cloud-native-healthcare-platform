"""Generate isolated H2/MockMvc tests for the new services; reference HTTP calls are explicit test doubles."""
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
tests={
'pharmacy':r'''
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
''',
'nutrition':r'''
 @Test void foodsRequireAuthentication()throws Exception{mvc.perform(get("/api/nutrition/foods")).andExpect(status().isUnauthorized());}
 @Test void foodsExposeSourceAndPer100gBasis()throws Exception{mvc.perform(get("/api/nutrition/foods").header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isOk()).andExpect(jsonPath("$.foods[0].source").exists()).andExpect(jsonPath("$.basis").exists());}
 @Test void profileCannotBeAssignedToAnotherUser()throws Exception{mvc.perform(put("/api/nutrition/profile").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"goal\":\"VARIETY\",\"ownerUserId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isBadRequest());}
 @Test void profilesAreIsolated()throws Exception{UUID first=UUID.randomUUID(),second=UUID.randomUUID();mvc.perform(put("/api/nutrition/profile").header("Authorization",jwt("PATIENT",first)).contentType("application/json").content("{\"goal\":\"VARIETY\",\"notes\":\"Synthetic wellness goal\"}")).andExpect(status().isOk());mvc.perform(get("/api/nutrition/profile").header("Authorization",jwt("PATIENT",second))).andExpect(status().isNotFound());mvc.perform(get("/api/nutrition/recommendations").header("Authorization",jwt("PATIENT",first))).andExpect(status().isOk()).andExpect(jsonPath("$.method").exists());}
 @Test void nonpatientCannotWriteProfile()throws Exception{mvc.perform(put("/api/nutrition/profile").header("Authorization",jwt("NURSE",UUID.randomUUID())).contentType("application/json").content("{\"goal\":\"VARIETY\"}")).andExpect(status().isForbidden());}
''',
'billing':r'''
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
''',
'emergency':r'''
 @Test void requestsRequireAuthentication()throws Exception{mvc.perform(get("/api/emergency")).andExpect(status().isUnauthorized());}
 @Test void patientsCannotChoosePriorityOrRecipient()throws Exception{mvc.perform(post("/api/emergency").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"location\":\"Synthetic site\",\"summary\":\"Demo request\",\"administrativePriority\":\"ADMIN_URGENT\"}")).andExpect(status().isBadRequest());}
 @Test void historyOwnershipAssignmentAndTransitions()throws Exception{
  UUID patient=UUID.randomUUID(),staff=UUID.randomUUID();String json=mvc.perform(post("/api/emergency").header("Authorization",jwt("PATIENT",patient)).contentType("application/json").content("{\"location\":\"Synthetic site\",\"summary\":\"Demo administrative request\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();String id=mapper.readTree(json).path("id").asText();
  mvc.perform(get("/api/emergency/"+id).header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",staff)).contentType("application/json").content("{\"status\":\"CLOSED\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isConflict());
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",staff)).contentType("application/json").content("{\"status\":\"ASSIGNED\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(2));
  mvc.perform(put("/api/emergency/"+id).header("Authorization",jwt("EMERGENCY_STAFF",UUID.randomUUID())).contentType("application/json").content("{\"status\":\"IN_PROGRESS\",\"administrativePriority\":\"ADMIN_STANDARD\",\"assignToSelf\":true}")).andExpect(status().isForbidden());
 }
''',
'notification':r'''
 @Test void notificationsRequireAuthentication()throws Exception{mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());}
 @Test void preferenceOwnerCannotBeInjected()throws Exception{mvc.perform(put("/api/notifications/preferences").header("Authorization",jwt("PATIENT",UUID.randomUUID())).contentType("application/json").content("{\"enabled\":true,\"reminders\":true,\"ownerUserId\":\""+UUID.randomUUID()+"\"}")).andExpect(status().isBadRequest());}
 @Test void notificationsAreDeduplicatedAndOwned()throws Exception{
  UUID patient=UUID.randomUUID(),appointment=UUID.randomUUID();String h=jwt("PATIENT",patient);doReturn(mapper.readTree("{\"appointmentDate\":\"2099-01-01\",\"appointmentTime\":\"10:00:00\",\"status\":\"SCHEDULED\"}")).when(access).remote(anyString(),anyString());String body="{\"appointmentId\":\""+appointment+"\"}";
  for(int i=0;i<2;i++)mvc.perform(post("/api/notifications/events/appointment").header("Authorization",h).contentType("application/json").content(body)).andExpect(status().isOk());
  String json=mvc.perform(get("/api/notifications").header("Authorization",h)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andReturn().getResponse().getContentAsString();String id=mapper.readTree(json).get(0).path("id").asText();
  mvc.perform(put("/api/notifications/"+id+"/read").header("Authorization",jwt("PATIENT",UUID.randomUUID()))).andExpect(status().isForbidden());
  mvc.perform(put("/api/notifications/"+id+"/read").header("Authorization",h)).andExpect(status().isOk()).andExpect(jsonPath("$.isRead").value(true));
 }
'''}
header='''package com.medflow.DOMAIN;
import com.fasterxml.jackson.databind.ObjectMapper;import io.jsonwebtoken.Jwts;import io.jsonwebtoken.security.Keys;import java.nio.charset.StandardCharsets;import java.util.*;
import org.junit.jupiter.api.Test;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:DOMAIN;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.enabled=false","spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:db/migration/V1__schema.sql","app.jwt.secret=test-only-public-signing-fixture-never-use-in-production-0123456789","app.gateway=http://127.0.0.1:1","eureka.client.enabled=false","spring.cloud.discovery.enabled=false"})
@AutoConfigureMockMvc class BusinessSecurityTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@MockitoSpyBean Access access;
 String jwt(String role,UUID id){return "Bearer "+Jwts.builder().subject(id.toString()).claim("email","synthetic@example.test").claim("role",role).expiration(new Date(System.currentTimeMillis()+60000)).signWith(Keys.hmacShaKeyFor("test-only-public-signing-fixture-never-use-in-production-0123456789".getBytes(StandardCharsets.UTF_8))).compact();}
'''
for name,body in tests.items():
    path=ROOT/'backend'/f'{name}-service'/'src/test/java/com/medflow'/name/'BusinessSecurityTest.java'
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(header.replace('DOMAIN',name)+body+'\n}\n')
print('Created isolated domain authorization and transaction tests for five services.')
