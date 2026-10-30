package com.medflow.prescription.controller;

import com.medflow.prescription.client.ClinicalReferenceService;
import com.medflow.prescription.dto.PrescriptionCreateRequest;
import com.medflow.prescription.dto.PrescriptionResponse;
import com.medflow.prescription.dto.PrescriptionUpdateRequest;
import com.medflow.prescription.entity.Prescription;
import com.medflow.prescription.exception.ApiException;
import com.medflow.prescription.service.PrescriptionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {
    private final PrescriptionService prescriptions;
    private final ClinicalReferenceService references;

    public PrescriptionController(PrescriptionService prescriptions, ClinicalReferenceService references) {
        this.prescriptions = prescriptions;
        this.references = references;
    }

    @PostMapping
    ResponseEntity<PrescriptionResponse> create(@Valid @RequestBody PrescriptionCreateRequest request,
                                                Authentication authentication,
                                                @RequestHeader("Authorization") String authorization) {
        requireAny(authentication, "DOCTOR", "ADMIN");
        UUID doctorId = hasRole(authentication, "DOCTOR")
                ? references.doctorProfileForCurrentUser(subject(authentication), authorization) : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(prescriptions.create(request, doctorId, hasRole(authentication, "ADMIN"), authorization));
    }

    @GetMapping("/me")
    // A pharmacist gets only a dedicated dispensing projection, never unrestricted clinical history.
    // Existing /me and resource ownership checks below remain unchanged.
    List<PrescriptionResponse> me(Authentication authentication,
                                  @RequestHeader("Authorization") String authorization) {
        if (hasRole(authentication, "PATIENT"))
            return prescriptions.patient(references.patientProfileForCurrentUser(subject(authentication), authorization));
        if (hasRole(authentication, "DOCTOR"))
            return prescriptions.doctor(references.doctorProfileForCurrentUser(subject(authentication), authorization));
        if (hasRole(authentication, "ADMIN")) return prescriptions.all();
        throw forbidden();
    }

    @GetMapping("/dispensing/{id}")
    java.util.Map<String,Object> dispensing(@PathVariable UUID id,Authentication authentication){
        requireAny(authentication,"PHARMACIST","ADMIN");
        var p=prescriptions.get(id);
        return java.util.Map.of("id",p.id(),"patientId",p.patientId(),"status",p.status(),"medicines",p.medicines());
    }

    @GetMapping("/patient/me")
    List<PrescriptionResponse> patientMe(Authentication authentication,
                                         @RequestHeader("Authorization") String authorization) {
        requireAny(authentication, "PATIENT");
        return prescriptions.patient(references.patientProfileForCurrentUser(subject(authentication), authorization));
    }

    @GetMapping("/doctor/me")
    List<PrescriptionResponse> doctorMe(Authentication authentication,
                                        @RequestHeader("Authorization") String authorization) {
        requireAny(authentication, "DOCTOR");
        return prescriptions.doctor(references.doctorProfileForCurrentUser(subject(authentication), authorization));
    }

    @GetMapping("/appointment/{appointmentId}")
    PrescriptionResponse appointment(@PathVariable UUID appointmentId, Authentication authentication,
                                     @RequestHeader("Authorization") String authorization) {
        Prescription prescription = prescriptions.getByAppointment(appointmentId);
        authorizeRead(prescription, authentication, authorization);
        return prescriptions.get(prescription.id);
    }

    @GetMapping("/{id}")
    PrescriptionResponse get(@PathVariable UUID id, Authentication authentication,
                             @RequestHeader("Authorization") String authorization) {
        Prescription prescription = prescriptions.getEntity(id);
        authorizeRead(prescription, authentication, authorization);
        return prescriptions.get(id);
    }

    @GetMapping
    List<PrescriptionResponse> all(Authentication authentication) {
        requireAny(authentication, "ADMIN");
        return prescriptions.all();
    }

    @PutMapping("/{id}")
    PrescriptionResponse update(@PathVariable UUID id, @Valid @RequestBody PrescriptionUpdateRequest request,
                                Authentication authentication,
                                @RequestHeader("Authorization") String authorization) {
        authorizeManage(prescriptions.getEntity(id), authentication, authorization);
        return prescriptions.update(id, request);
    }

    @DeleteMapping("/{id}")
    PrescriptionResponse cancel(@PathVariable UUID id, Authentication authentication,
                                @RequestHeader("Authorization") String authorization) {
        authorizeManage(prescriptions.getEntity(id), authentication, authorization);
        return prescriptions.cancel(id);
    }

    private void authorizeRead(Prescription prescription, Authentication authentication, String authorization) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "PATIENT") && prescription.patientId.equals(
                references.patientProfileForCurrentUser(subject(authentication), authorization))) return;
        if (hasRole(authentication, "DOCTOR") && prescription.doctorId.equals(
                references.doctorProfileForCurrentUser(subject(authentication), authorization))) return;
        throw forbidden();
    }

    private void authorizeManage(Prescription prescription, Authentication authentication, String authorization) {
        if (hasRole(authentication, "ADMIN")) return;
        if (hasRole(authentication, "DOCTOR") && prescription.doctorId.equals(
                references.doctorProfileForCurrentUser(subject(authentication), authorization))) return;
        throw forbidden();
    }

    private static UUID subject(Authentication authentication) { return (UUID) authentication.getPrincipal(); }
    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    private static void requireAny(Authentication authentication, String... roles) {
        for (String role : roles) if (hasRole(authentication, role)) return;
        throw forbidden();
    }
    private static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN, "Access denied"); }
}
