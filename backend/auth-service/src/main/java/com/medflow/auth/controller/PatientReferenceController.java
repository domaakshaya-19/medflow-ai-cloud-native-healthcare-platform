package com.medflow.auth.controller;
import com.medflow.auth.domain.Role;
import com.medflow.auth.repository.UserAccountRepository;
import com.medflow.auth.exception.ApiException;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/auth/patient-reference") public class PatientReferenceController {
 private final UserAccountRepository users;public PatientReferenceController(UserAccountRepository users){this.users=users;}
 @GetMapping("/{id}") public Map<String,Object> patient(@PathVariable UUID id,Authentication auth){
  if(auth.getAuthorities().stream().noneMatch(g->Set.of("ROLE_ADMIN","ROLE_BILLING_STAFF").contains(g.getAuthority())))throw new ApiException(FORBIDDEN,"Billing identity lookup is restricted");
  var user=users.findById(id).filter(u->u.isEnabled()&&u.getRole()==Role.PATIENT).orElseThrow(()->new ApiException(NOT_FOUND,"Patient identity not found"));
  return Map.of("id",user.getId(),"fullName",user.getFullName());
 }
}
