package com.medflow.doctor.controller;

import com.medflow.doctor.dto.*;
import com.medflow.doctor.exception.ApiException;
import com.medflow.doctor.service.DoctorService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {
    private final DoctorService doctors;
    public DoctorController(DoctorService doctors) { this.doctors = doctors; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorResponse create(@Valid @RequestBody DoctorCreateRequest request, Authentication auth) {
        UUID subject = (UUID) auth.getPrincipal();
        if (role(auth, "DOCTOR")) {
            if (request.userId() != null && !subject.equals(request.userId()))
                throw new ApiException(HttpStatus.FORBIDDEN, "Cannot create another doctor's profile");
            return doctors.create(subject, request);
        }
        requireRole(auth, "ADMIN");
        if (request.userId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "userId is required for admin creation");
        return doctors.create(request.userId(), request);
    }

    @GetMapping("/me")
    public DoctorResponse me(Authentication auth) {
        requireRole(auth, "DOCTOR");
        return doctors.getByUserId((UUID) auth.getPrincipal());
    }

    @GetMapping("/directory")
    public List<DoctorDirectoryResponse> directory(
            @RequestParam(required = false) String specialization, Authentication auth) {
        requireAnyRole(auth, "PATIENT", "DOCTOR", "ADMIN");
        return doctors.directory(specialization);
    }

    @GetMapping("/directory/{id}")
    public DoctorDirectoryResponse directoryEntry(@PathVariable UUID id, Authentication auth) {
        requireAnyRole(auth, "PATIENT", "DOCTOR", "ADMIN");
        return doctors.directoryEntry(id);
    }

    @GetMapping("/{id}")
    public DoctorResponse get(@PathVariable UUID id, Authentication auth) {
        checkAccess(id, auth);
        return doctors.get(id);
    }

    @PutMapping("/{id}")
    public DoctorResponse update(@PathVariable UUID id, @Valid @RequestBody DoctorUpdateRequest request, Authentication auth) {
        checkAccess(id, auth);
        return doctors.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        checkAccess(id, auth);
        doctors.delete(id);
    }

    @GetMapping
    public List<DoctorResponse> all(Authentication auth) {
        requireRole(auth, "ADMIN");
        return doctors.all();
    }

    private void checkAccess(UUID id, Authentication auth) {
        if (role(auth, "ADMIN")) return;
        if (!role(auth, "DOCTOR") || !doctors.owner(id).equals(auth.getPrincipal()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private static boolean role(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    private static void requireRole(Authentication auth, String role) {
        if (!role(auth, role)) throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }

    private static void requireAnyRole(Authentication auth, String... roles) {
        for (String candidate : roles) if (role(auth, candidate)) return;
        throw new ApiException(HttpStatus.FORBIDDEN, "Access denied");
    }
}
