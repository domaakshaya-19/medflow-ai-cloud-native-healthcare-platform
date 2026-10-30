package com.medflow.appointment.controller;
import com.medflow.appointment.service.AppointmentService;
import com.medflow.appointment.exception.ApiException;
import java.time.LocalDate;import java.util.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/appointments") public class AvailabilityController{
 private final AppointmentService appointments;public AvailabilityController(AppointmentService appointments){this.appointments=appointments;}
 @GetMapping("/availability") public Map<String,Object> availability(@RequestParam UUID doctorId,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate date,Authentication auth,@RequestHeader("Authorization")String bearer){
  if(auth.getAuthorities().stream().noneMatch(g->Set.of("ROLE_PATIENT","ROLE_DOCTOR","ROLE_ADMIN").contains(g.getAuthority())))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"Availability lookup is restricted");
  return appointments.availability(doctorId,date,bearer);
 }
}
