package com.medflow.auth.dto;
import jakarta.validation.constraints.*;
public record RegisterRequest(@NotBlank @Size(min=2,max=120) String fullName, @NotBlank @Email @Size(max=254) String email, @NotBlank @Size(min=10,max=72) @Pattern(regexp="^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$", message="must include upper, lower, number, and special characters") String password) {}
