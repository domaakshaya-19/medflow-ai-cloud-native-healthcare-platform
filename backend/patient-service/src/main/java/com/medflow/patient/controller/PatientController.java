package com.medflow.patient.controller;

import com.medflow.patient.dto.*;
import com.medflow.patient.exception.ApiException;
import com.medflow.patient.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/patients")
public class PatientController {
 private final PatientService patients;
 public PatientController(PatientService patients){this.patients=patients;}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public PatientResponse create(@Valid @RequestBody PatientCreateRequest request,Authentication auth){
  if(!role(auth,"PATIENT")&&!role(auth,"ADMIN")) throw new ApiException(HttpStatus.FORBIDDEN,"Not allowed to create patient profiles");
  UUID subject=(UUID)auth.getPrincipal();
  if(role(auth,"PATIENT")&&request.userId()!=null&&!subject.equals(request.userId())) throw new ApiException(HttpStatus.FORBIDDEN,"Cannot create another user's profile");
  UUID owner=role(auth,"ADMIN")?request.userId():subject;
  if(owner==null) throw new ApiException(HttpStatus.BAD_REQUEST,"userId is required for admin creation");
  return patients.createPatient(owner,request);
 }
 @GetMapping("/me") public PatientResponse me(Authentication auth){requireRole(auth,"PATIENT");return patients.getPatientByUserId((UUID)auth.getPrincipal());}
 @GetMapping("/by-auth-user/{authUserId}") public PatientResponse byAuthUser(@PathVariable UUID authUserId,Authentication auth){if(!role(auth,"ADMIN")&&(!role(auth,"PATIENT")||!authUserId.equals(auth.getPrincipal())))throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");return patients.getPatientByUserId(authUserId);}
 @GetMapping("/{id}") public PatientResponse get(@PathVariable UUID id,Authentication auth){checkAccess(id,auth);return patients.getPatientById(id);}
 @PutMapping("/{id}") public PatientResponse update(@PathVariable UUID id,@Valid @RequestBody PatientUpdateRequest request,Authentication auth){checkAccess(id,auth);return patients.updatePatient(id,request);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){checkAccess(id,auth);patients.deletePatient(id);}
 @GetMapping public List<PatientResponse> all(Authentication auth){requireRole(auth,"ADMIN");return patients.getAllPatients();}
 private void checkAccess(UUID id,Authentication auth){if(role(auth,"ADMIN"))return;if(!role(auth,"PATIENT")||!patients.owner(id).equals(auth.getPrincipal()))throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
 private static boolean role(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
 private static void requireRole(Authentication auth,String role){if(!role(auth,role))throw new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
}
