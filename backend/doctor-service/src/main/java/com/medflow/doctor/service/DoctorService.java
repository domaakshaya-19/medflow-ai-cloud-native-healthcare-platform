package com.medflow.doctor.service;

import com.medflow.doctor.dto.*;
import com.medflow.doctor.entity.Doctor;
import com.medflow.doctor.exception.ApiException;
import com.medflow.doctor.repository.DoctorRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoctorService {
    private final DoctorRepository repository;
    public DoctorService(DoctorRepository repository) { this.repository = repository; }

    @Transactional
    public DoctorResponse create(UUID userId, DoctorCreateRequest request) {
        if (repository.existsByUserId(userId)) throw new ApiException(HttpStatus.CONFLICT, "Doctor profile already exists");
        Doctor doctor = new Doctor();
        doctor.userId = userId;
        copy(doctor, request.firstName(), request.lastName(), request.specialization(), request.qualification(),
                request.licenseNumber(), request.phone(), request.email(), request.department(),
                request.experienceYears(), request.consultationFee(), request.available());
        return view(repository.saveAndFlush(doctor));
    }

    @Transactional(readOnly = true)
    public DoctorResponse get(UUID id) { return view(find(id)); }

    @Transactional(readOnly = true)
    public DoctorResponse getByUserId(UUID userId) {
        return view(repository.findByUserId(userId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found")));
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> all() { return repository.findAll().stream().map(DoctorService::view).toList(); }

    @Transactional(readOnly = true)
    public List<DoctorDirectoryResponse> directory(String specialization) {
        List<Doctor> doctors = specialization == null || specialization.isBlank()
                ? repository.findAllByOrderByLastNameAscFirstNameAsc()
                : repository.findBySpecializationContainingIgnoreCaseOrderByLastNameAscFirstNameAsc(specialization.trim());
        return doctors.stream().map(DoctorService::directoryView).toList();
    }

    @Transactional(readOnly = true)
    public DoctorDirectoryResponse directoryEntry(UUID id) { return directoryView(find(id)); }

    @Transactional(readOnly = true)
    public UUID owner(UUID id) { return find(id).userId; }

    @Transactional
    public DoctorResponse update(UUID id, DoctorUpdateRequest request) {
        Doctor doctor = find(id);
        copy(doctor, request.firstName(), request.lastName(), request.specialization(), request.qualification(),
                request.licenseNumber(), request.phone(), request.email(), request.department(),
                request.experienceYears(), request.consultationFee(), request.available());
        return view(repository.saveAndFlush(doctor));
    }

    @Transactional
    public void delete(UUID id) { repository.delete(find(id)); }

    private Doctor find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Doctor profile not found"));
    }

    private static void copy(Doctor d, String first, String last, String specialization, String qualification,
            String license, String phone, String email, String department, int years,
            java.math.BigDecimal fee, boolean available) {
        d.firstName = first.trim(); d.lastName = last.trim(); d.specialization = specialization.trim();
        d.qualification = qualification.trim(); d.licenseNumber = license.trim(); d.phone = phone;
        d.email = email.trim(); d.department = department; d.experienceYears = years;
        d.consultationFee = fee; d.available = available;
    }

    private static DoctorResponse view(Doctor d) {
        return new DoctorResponse(d.id, d.userId, d.firstName, d.lastName, d.specialization,
                d.qualification, d.licenseNumber, d.phone, d.email, d.department,
                d.experienceYears, d.consultationFee, d.available, d.createdAt, d.updatedAt);
    }

    private static DoctorDirectoryResponse directoryView(Doctor d) {
        return new DoctorDirectoryResponse(d.id, d.firstName, d.lastName, d.specialization,
                d.qualification, d.licenseNumber, d.department, d.experienceYears,
                d.consultationFee, d.available);
    }
}
