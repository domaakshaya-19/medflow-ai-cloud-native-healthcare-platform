package com.medflow.ai;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Component public class AgentRegistry {
  public record Agent(String name,Set<String> roles,Set<String> tools,String instruction){}
  private final Map<String,Agent> agents=new LinkedHashMap<>();
  public AgentRegistry(){
    add("PATIENT",Set.of("PATIENT","ADMIN"),Set.of("doctor.directory","patient.appointments","patient.records","patient.labResults","patient.prescriptions"),"Explain hospital navigation and appointment status. Give general education only; do not diagnose or choose treatment.");
    add("DOCTOR",Set.of("DOCTOR","ADMIN"),Set.of("doctor.queue","doctor.records","doctor.labOrders","doctor.patientRecords"),"Organize authorized clinical information as a documentation aid. A qualified clinician makes every clinical decision.");
    add("EMERGENCY",Set.of("PATIENT","EMERGENCY_STAFF","ADMIN"),Set.of("emergency.instructions","emergency.mine","emergency.requests"),"For a possible medical emergency advise contacting local emergency services immediately. Do not triage or assign clinical severity.");
    add("PHARMACY",Set.of("PATIENT","DOCTOR","PHARMACIST","ADMIN"),Set.of("pharmacy.catalog","patient.prescriptions","pharmacy.prescription"),"Explain authorized prescription status and catalog stock. Never create, change, or dispense a prescription autonomously.");
    add("BILLING",Set.of("PATIENT","BILLING_STAFF","ADMIN"),Set.of("billing.mine","billing.invoices","billing.invoice"),"Explain authorized invoice items and synthetic payment status. Never execute payments or request card details.");
    add("OPERATIONS",Set.of("DOCTOR","NURSE","RECEPTIONIST","LAB_STAFF","ADMIN"),Set.of("doctor.queue","operations.appointments","operations.summary","operations.labSummary"),"Summarize operational counts limited to the caller's permissions. Do not infer unrestricted clinical access from a staff role.");
    add("NUTRITION",Set.of("PATIENT","DOCTOR","ADMIN"),Set.of("nutrition.foods","nutrition.profile","nutrition.recommendations","nutrition.progress"),"Explain sourced food information and general wellness goals. Do not provide disease-specific diets or individualized treatment plans.");
  }
  private void add(String name,Set<String> roles,Set<String> tools,String instruction){agents.put(name,new Agent(name,roles,tools,instruction));}
  public Collection<Agent> all(){return Collections.unmodifiableCollection(agents.values());}
  public Agent select(String requested,String prompt,String role){
    String name=requested;
    if(name==null||name.isBlank()||name.equals("AUTO")){
      String p=prompt==null?"":prompt.toLowerCase(Locale.ROOT);
      List<String> matches=new ArrayList<>();
      if(p.matches(".*\\b(emergency|urgent)\\b.*"))matches.add("EMERGENCY");
      if(p.matches(".*\\b(bill|invoice|payment)\\b.*"))matches.add("BILLING");
      if(p.matches(".*\\b(food|nutrition|wellness)\\b.*"))matches.add("NUTRITION");
      if(p.matches(".*\\b(pharmacy|prescription|medicine)\\b.*"))matches.add("PHARMACY");
      if(matches.size()>1)throw new ResponseStatusException(BAD_REQUEST,"Ambiguous intent; select one agent explicitly");
      name=matches.isEmpty()?switch(role){case "PATIENT"->"PATIENT";case "DOCTOR"->"DOCTOR";case "PHARMACIST"->"PHARMACY";case "BILLING_STAFF"->"BILLING";case "EMERGENCY_STAFF"->"EMERGENCY";default->"OPERATIONS";}:matches.getFirst();
    }
    Agent agent=agents.get(name);
    if(agent==null)throw new ResponseStatusException(BAD_REQUEST,"Unknown agent");
    if(!agent.roles().contains(role))throw new ResponseStatusException(FORBIDDEN,"Agent not authorized for this role");
    return agent;
  }
}
