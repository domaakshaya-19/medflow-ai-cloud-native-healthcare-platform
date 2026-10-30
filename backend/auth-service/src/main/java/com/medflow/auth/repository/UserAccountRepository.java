package com.medflow.auth.repository;
import com.medflow.auth.domain.UserAccount; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> { Optional<UserAccount> findByEmail(String email); boolean existsByEmail(String email); }
