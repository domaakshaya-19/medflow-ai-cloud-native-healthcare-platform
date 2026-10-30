package com.medflow.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="user_accounts", uniqueConstraints=@UniqueConstraint(name="uk_user_email", columnNames="email"))
public class UserAccount {
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(nullable=false, length=120) private String fullName;
 @Column(nullable=false, length=254) private String email;
 @Column(nullable=false, length=100) private String passwordHash;
 @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private Role role;
 @Column(nullable=false) private boolean enabled = true;
 @Column(nullable=false, updatable=false) private Instant createdAt;
 @Column(nullable=false) private Instant updatedAt;
 @PrePersist void create(){ createdAt=updatedAt=Instant.now(); }
 @PreUpdate void update(){ updatedAt=Instant.now(); }
 public UUID getId(){return id;} public String getFullName(){return fullName;} public void setFullName(String v){fullName=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPasswordHash(){return passwordHash;} public void setPasswordHash(String v){passwordHash=v;} public Role getRole(){return role;} public void setRole(Role v){role=v;} public boolean isEnabled(){return enabled;} public void setEnabled(boolean v){enabled=v;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
