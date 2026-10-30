package com.medflow.appointment.controller;
import com.medflow.appointment.repository.AppointmentRepository;
import com.medflow.appointment.entity.AppointmentStatus;
import com.medflow.appointment.exception.ApiException;
import java.time.LocalDate;import java.util.*;
import org.springframework.security.core.Authentication;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/appointments/operations") public class OperationsController {
 private final AppointmentRepository appointments;public OperationsController(AppointmentRepository appointments){this.appointments=appointments;}
 @GetMapping("/summary") public Map<String,Object> summary(Authentication a){
  if(a.getAuthorities().stream().noneMatch(g->Set.of("ROLE_ADMIN","ROLE_NURSE","ROLE_RECEPTIONIST").contains(g.getAuthority())))throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,"Operational aggregates are restricted to authorized staff");
  Map<String,Long> counts=new LinkedHashMap<>();for(var status:AppointmentStatus.values())counts.put(status.name(),appointments.countByStatus(status));
  return Map.of("totalAppointments",appointments.count(),"todayAppointments",appointments.countByAppointmentDate(LocalDate.now()),"statusCounts",counts,"privacy","Aggregate counts only; no patient identifiers or clinical content");
 }
}
