package com.medflow.notification;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.time.*;import java.util.*;
import org.springframework.security.core.Authentication;import org.springframework.transaction.annotation.Transactional;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/notifications") public class NotificationController {
 private final Access access;public NotificationController(Access access){this.access=access;}
 public record Preferences(boolean enabled,boolean reminders){}
 public record Event(@NotNull UUID appointmentId){}
 @GetMapping("/preferences") public Map<String,Object> preferences(Authentication a){var p=access.rows("SELECT enabled,reminders FROM notification_preferences WHERE owner_user_id=?",access.subject(a));return p.isEmpty()?new LinkedHashMap<>(Map.of("enabled",true,"reminders",true)):p.getFirst();}
 @PutMapping("/preferences") @Transactional public Map<String,Object> preferences(@RequestBody Preferences r,Authentication a){UUID owner=access.subject(a);if(access.db.update("UPDATE notification_preferences SET enabled=?,reminders=? WHERE owner_user_id=?",r.enabled(),r.reminders(),owner)==0)access.db.update("INSERT INTO notification_preferences(owner_user_id,enabled,reminders) VALUES(?,?,?)",owner,r.enabled(),r.reminders());return preferences(a);}
 @GetMapping public List<Map<String,Object>> mine(Authentication a){deliverDue();return access.rows("SELECT * FROM notifications WHERE owner_user_id=? ORDER BY created_at DESC LIMIT 100",access.subject(a));}
 @PutMapping("/{id}/read") public Map<String,Object> read(@PathVariable UUID id,Authentication a){var row=access.one("SELECT * FROM notifications WHERE id=?",id);access.owner(a,row.get("ownerUserId"));access.db.update("UPDATE notifications SET is_read=TRUE WHERE id=?",id);return access.one("SELECT * FROM notifications WHERE id=?",id);}
 @PostMapping("/events/appointment") @Transactional public Map<String,Object> event(@Valid @RequestBody Event r,Authentication a,@RequestHeader("Authorization")String bearer){
  var appointment=access.remote("/api/appointments/"+r.appointmentId(),bearer);var prefs=preferences(a);UUID owner=access.subject(a);
  String message="Appointment "+appointment.path("appointmentDate").asText()+" at "+appointment.path("appointmentTime").asText()+" is "+appointment.path("status").asText()+". Review Appointments for current details.";
  if(access.rows("SELECT id FROM notifications WHERE owner_user_id=? AND appointment_id=? AND kind='APPOINTMENT'",owner,r.appointmentId()).isEmpty())access.db.update("INSERT INTO notifications(id,owner_user_id,appointment_id,kind,message,delivery_status,due_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),owner,r.appointmentId(),"APPOINTMENT",message,Boolean.TRUE.equals(prefs.get("enabled"))?"DELIVERED":"SUPPRESSED",OffsetDateTime.now());
  var when=LocalDateTime.parse(appointment.path("appointmentDate").asText()+"T"+appointment.path("appointmentTime").asText()).atZone(ZoneId.systemDefault()).minusHours(24).toOffsetDateTime();
  if(Boolean.TRUE.equals(prefs.get("reminders"))&&access.rows("SELECT id FROM notifications WHERE owner_user_id=? AND appointment_id=? AND kind='REMINDER'",owner,r.appointmentId()).isEmpty())access.db.update("INSERT INTO notifications(id,owner_user_id,appointment_id,kind,message,delivery_status,due_at) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID(),owner,r.appointmentId(),"REMINDER","Reminder: "+message,"PENDING",when);
  return Map.of("accepted",true,"delivery","IN_APP_ONLY","appointmentId",r.appointmentId());
 }
 @Scheduled(fixedDelay=60000) public void deliverDue(){access.db.update("UPDATE notifications SET delivery_status='DELIVERED' WHERE delivery_status='PENDING' AND due_at<=CURRENT_TIMESTAMP AND NOT EXISTS(SELECT 1 FROM notification_preferences p WHERE p.owner_user_id=notifications.owner_user_id AND (p.enabled=FALSE OR p.reminders=FALSE))");}
}
