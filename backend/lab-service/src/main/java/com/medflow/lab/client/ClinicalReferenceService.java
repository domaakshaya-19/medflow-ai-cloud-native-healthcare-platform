package com.medflow.lab.client;
import com.medflow.lab.exception.ApiException;
import feign.FeignException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
@Service
public class ClinicalReferenceService {
    private static final UUID SERVICE_ID=UUID.nameUUIDFromBytes("medflow-lab-service".getBytes(StandardCharsets.UTF_8));
    private static final Set<String> VALID_STATES=Set.of("CONFIRMED","IN_PROGRESS","COMPLETED");
    private final PatientClient patients;private final DoctorClient doctors;private final AppointmentClient appointments;private final SecretKey key;
    public ClinicalReferenceService(PatientClient patients,DoctorClient doctors,AppointmentClient appointments,@Value("${app.jwt.secret}")String secret){this.patients=patients;this.doctors=doctors;this.appointments=appointments;if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    public UUID patientProfileForCurrentUser(UUID userId,String authorization){try{var p=patients.byAuthUser(userId,authorization);if(p==null||p.id()==null||!userId.equals(p.userId()))throw new ApiException(HttpStatus.BAD_GATEWAY,"Invalid Patient Service response");return p.id();}catch(FeignException.NotFound e){throw new ApiException(HttpStatus.NOT_FOUND,"Patient profile not found");}catch(FeignException e){throw new ApiException(HttpStatus.BAD_GATEWAY,"Patient Service unavailable");}}
    public UUID doctorProfileForCurrentUser(UUID userId,String authorization){try{var d=doctors.me(authorization);if(d==null||d.id()==null||!userId.equals(d.userId()))throw new ApiException(HttpStatus.BAD_GATEWAY,"Invalid Doctor Service response");return d.id();}catch(FeignException.NotFound e){throw new ApiException(HttpStatus.NOT_FOUND,"Doctor profile not found");}catch(FeignException e){throw new ApiException(HttpStatus.BAD_GATEWAY,"Doctor Service unavailable");}}
    public void validateOrderReferences(UUID appointmentId,UUID patientId,UUID doctorId,String authorization){try{var p=patients.byId(patientId,internalAuthorization());if(p==null||!patientId.equals(p.id()))throw new ApiException(HttpStatus.BAD_GATEWAY,"Invalid Patient Service response");var a=appointments.get(appointmentId,authorization);if(a==null||!appointmentId.equals(a.id()))throw new ApiException(HttpStatus.BAD_GATEWAY,"Invalid Appointment Service response");if(!patientId.equals(a.patientId())||!doctorId.equals(a.doctorId()))throw new ApiException(HttpStatus.BAD_REQUEST,"Appointment does not match the specified patient and doctor");if(!VALID_STATES.contains(a.status()))throw new ApiException(HttpStatus.CONFLICT,"Appointment is not in a valid state for a lab order");}catch(FeignException.NotFound e){throw new ApiException(HttpStatus.NOT_FOUND,"Clinical reference not found");}catch(FeignException e){throw new ApiException(HttpStatus.BAD_GATEWAY,"Clinical reference service unavailable");}}
    private String internalAuthorization(){Instant now=Instant.now();return "Bearer "+Jwts.builder().subject(SERVICE_ID.toString()).claim("email","lab-service@internal.medflow").claim("role","ADMIN").issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60))).signWith(key).compact();}
}
