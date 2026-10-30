package com.medflow.patient.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record PatientCreateRequest(UUID userId, @NotBlank String firstName, @NotBlank String lastName,
 @NotNull @PastOrPresent LocalDate dateOfBirth, String gender, @NotBlank @Pattern(regexp="^[+0-9 ()-]{7,30}$") String phone,
 @NotBlank @Email String email, String address, @Pattern(regexp="^(A|B|AB|O)[+-]$|^$") String bloodGroup,
 String emergencyContactName, String emergencyContactPhone, String allergies, String medicalHistorySummary) {}
