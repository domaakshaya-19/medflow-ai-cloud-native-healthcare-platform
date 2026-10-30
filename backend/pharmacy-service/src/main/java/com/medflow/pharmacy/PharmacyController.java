package com.medflow.pharmacy;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;
import org.springframework.security.core.Authentication;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/pharmacy") public class PharmacyController {
 private final Access access;public PharmacyController(Access access){this.access=access;}
 public record Medication(@NotBlank @Size(max=250)String name,@Min(0) @Max(1000000)int stock,@NotBlank @Size(max=40)String unit){}
 public record Stock(@Min(1) @Max(100000)int quantity){}
 public record Dispense(@NotNull UUID prescriptionId,@NotNull UUID medicationId,@Min(1) @Max(1000)int quantity,boolean confirmed){}
 @GetMapping("/medications") public List<Map<String,Object>> medications(){return access.rows("SELECT * FROM medications ORDER BY name LIMIT 100");}
 @PostMapping("/medications") @ResponseStatus(CREATED) public Map<String,Object> create(@Valid @RequestBody Medication r,Authentication a){access.roles(a,"PHARMACIST","ADMIN");UUID id=UUID.randomUUID();access.db.update("INSERT INTO medications(id,name,stock,unit) VALUES(?,?,?,?)",id,r.name().trim(),r.stock(),r.unit().trim());return access.one("SELECT * FROM medications WHERE id=?",id);}
 @PutMapping("/medications/{id}/stock") public Map<String,Object> stock(@PathVariable UUID id,@Valid @RequestBody Stock r,Authentication a){access.roles(a,"PHARMACIST","ADMIN");if(access.db.update("UPDATE medications SET stock=stock+? WHERE id=? AND stock+?<=1000000",r.quantity(),id,r.quantity())!=1)throw new ResponseStatusException(CONFLICT,"Medication missing or stock limit exceeded");return access.one("SELECT * FROM medications WHERE id=?",id);}
 @PostMapping("/dispensations") @ResponseStatus(CREATED) @Transactional public Map<String,Object> dispense(@Valid @RequestBody Dispense r,Authentication a,@RequestHeader("Authorization")String bearer){
  access.roles(a,"PHARMACIST","ADMIN");if(!r.confirmed())throw new ResponseStatusException(BAD_REQUEST,"Explicit dispensing confirmation required");
  var rx=access.remote("/api/prescriptions/dispensing/"+r.prescriptionId(),bearer);if(!rx.path("status").asText().equals("ACTIVE"))throw new ResponseStatusException(CONFLICT,"Prescription is not active");
  var medication=access.one("SELECT * FROM medications WHERE id=?",r.medicationId());boolean listed=false;for(var m:rx.path("medicines"))if(m.path("medicineName").asText().equalsIgnoreCase(String.valueOf(medication.get("name"))))listed=true;
  if(!listed)throw new ResponseStatusException(BAD_REQUEST,"Medication is not listed on the prescription");
  if(access.db.update("UPDATE medications SET stock=stock-? WHERE id=? AND stock>=?",r.quantity(),r.medicationId(),r.quantity())!=1)throw new ResponseStatusException(CONFLICT,"Insufficient stock");
  UUID id=UUID.randomUUID();access.db.update("INSERT INTO dispensations(id,prescription_id,patient_id,medication_id,quantity,dispensed_by) VALUES(?,?,?,?,?,?)",id,r.prescriptionId(),UUID.fromString(rx.path("patientId").asText()),r.medicationId(),r.quantity(),access.subject(a));return access.one("SELECT * FROM dispensations WHERE id=?",id);
 }
 @GetMapping("/dispensations") public List<Map<String,Object>> all(Authentication a){access.roles(a,"PHARMACIST","ADMIN");return access.rows("SELECT * FROM dispensations ORDER BY created_at DESC LIMIT 100");}
 @GetMapping("/dispensations/me") public List<Map<String,Object>> mine(Authentication a,@RequestHeader("Authorization")String bearer){access.roles(a,"PATIENT");UUID patient=UUID.fromString(access.remote("/api/patients/me",bearer).path("id").asText());return access.rows("SELECT * FROM dispensations WHERE patient_id=? ORDER BY created_at DESC LIMIT 100",patient);}
}
