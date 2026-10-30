package com.medflow.medicalrecords.controller;

import com.medflow.medicalrecords.client.ClinicalReferenceService;
import com.medflow.medicalrecords.dto.*;
import com.medflow.medicalrecords.entity.MedicalRecord;
import com.medflow.medicalrecords.exception.ApiException;
import com.medflow.medicalrecords.service.MedicalRecordService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medical-records")
public class MedicalRecordController {
    private final MedicalRecordService records;
    private final ClinicalReferenceService references;
    public MedicalRecordController(MedicalRecordService records,ClinicalReferenceService references){this.records=records;this.references=references;}

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public MedicalRecordResponse create(@Valid @RequestBody MedicalRecordCreateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){if(role(auth,"ADMIN"))return records.create(request,null,true,authorization);requireRole(auth,"DOCTOR");UUID doctorId=references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);return records.create(request,doctorId,false,authorization);}

    @GetMapping("/me") public List<MedicalRecordResponse> me(Authentication auth,@RequestHeader("Authorization")String authorization){requireRole(auth,"PATIENT");UUID patientId=references.patientProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);return records.patientRecords(patientId);}

    @GetMapping("/patient/{patientId}") public List<MedicalRecordResponse> patient(@PathVariable UUID patientId,Authentication auth,@RequestHeader("Authorization")String authorization){if(role(auth,"ADMIN"))return records.patientRecords(patientId);if(role(auth,"PATIENT")){UUID own=references.patientProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);if(!own.equals(patientId))throw forbidden();return records.patientRecords(patientId);}requireRole(auth,"DOCTOR");UUID doctorId=references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);return records.patientRecordsForDoctor(patientId,doctorId);}

    @GetMapping("/doctor/me") public List<MedicalRecordResponse> doctorMe(Authentication auth,@RequestHeader("Authorization")String authorization){if(role(auth,"ADMIN"))return records.all();requireRole(auth,"DOCTOR");return records.doctorRecords(references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),authorization));}

    @GetMapping("/appointment/{appointmentId}") public MedicalRecordResponse appointment(@PathVariable UUID appointmentId,Authentication auth,@RequestHeader("Authorization")String authorization){MedicalRecordResponse response=records.appointmentRecord(appointmentId);checkAccess(records.record(response.id()),auth,authorization);return response;}

    @GetMapping("/{id}") public MedicalRecordResponse get(@PathVariable UUID id,Authentication auth,@RequestHeader("Authorization")String authorization){MedicalRecord record=records.record(id);checkAccess(record,auth,authorization);return records.get(id);}

    @PutMapping("/{id}") public MedicalRecordResponse update(@PathVariable UUID id,@Valid @RequestBody MedicalRecordUpdateRequest request,Authentication auth,@RequestHeader("Authorization")String authorization){MedicalRecord record=records.record(id);if(!role(auth,"ADMIN")){requireRole(auth,"DOCTOR");UUID doctorId=references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);if(!record.doctorId.equals(doctorId))throw forbidden();}return records.update(id,request);}

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID id,Authentication auth){requireRole(auth,"ADMIN");records.delete(id);}

    private void checkAccess(MedicalRecord record,Authentication auth,String authorization){if(role(auth,"ADMIN"))return;if(role(auth,"PATIENT")){UUID patientId=references.patientProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);if(record.patientId.equals(patientId))return;throw forbidden();}if(role(auth,"DOCTOR")){UUID doctorId=references.doctorProfileForCurrentUser((UUID)auth.getPrincipal(),authorization);if(record.doctorId.equals(doctorId))return;}throw forbidden();}
    private static ApiException forbidden(){return new ApiException(HttpStatus.FORBIDDEN,"Access denied");}
    private static boolean role(Authentication auth,String role){return auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+role));}
    private static void requireRole(Authentication auth,String role){if(!role(auth,role))throw forbidden();}
}
