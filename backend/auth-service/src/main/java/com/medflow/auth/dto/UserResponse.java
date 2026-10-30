package com.medflow.auth.dto;
import com.medflow.auth.domain.*; import java.util.UUID;
public record UserResponse(UUID id,String fullName,String email,Role role){ public static UserResponse from(UserAccount u){return new UserResponse(u.getId(),u.getFullName(),u.getEmail(),u.getRole());} }
