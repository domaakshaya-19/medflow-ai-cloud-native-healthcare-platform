package com.medflow.ai;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Component public class ControlledTools {
  public record Tool(String name,Set<String> roles,String endpoint,Set<String> arguments,String ownership,String permission){}
  private final Map<String,Tool> tools=new LinkedHashMap<>();
  private final ObjectMapper mapper;private final URI gateway;private final HttpClient http;
  public ControlledTools(ObjectMapper mapper,@Value("${app.gateway}")String gateway){
    this.mapper=mapper;this.gateway=URI.create(gateway);http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    if(this.gateway.getHost()==null||this.gateway.getUserInfo()!=null||this.gateway.getQuery()!=null||this.gateway.getFragment()!=null)throw new IllegalArgumentException("Invalid configured gateway");
    add("doctor.directory",Set.of("PATIENT","DOCTOR","ADMIN"),"/api/doctors/directory",Set.of(),"Authenticated directory only");
    add("patient.appointments",Set.of("PATIENT"),"/api/appointments/me",Set.of(),"JWT subject -> own patient profile");
    add("patient.records",Set.of("PATIENT"),"/api/medical-records/me",Set.of(),"JWT subject -> own patient profile");
    add("patient.labResults",Set.of("PATIENT"),"/api/lab/results/me",Set.of(),"JWT subject -> own patient profile");
    add("patient.prescriptions",Set.of("PATIENT"),"/api/prescriptions/patient/me",Set.of(),"JWT subject -> own patient profile");
    add("doctor.queue",Set.of("DOCTOR"),"/api/appointments/doctor/me/queue",Set.of(),"Assigned doctor only");
    add("doctor.records",Set.of("DOCTOR","ADMIN"),"/api/medical-records/doctor/me",Set.of(),"Assigned doctor or administrator");
    add("doctor.labOrders",Set.of("DOCTOR","ADMIN"),"/api/lab/orders",Set.of(),"Assigned doctor or administrator");
    add("doctor.patientRecords",Set.of("DOCTOR","ADMIN"),"/api/medical-records/patient/{patientId}",Set.of("patientId"),"Backend checks assigned clinician and resource ownership");
    add("operations.appointments",Set.of("ADMIN"),"/api/appointments",Set.of(),"Administrator; aggregate output only");
    add("emergency.instructions",Set.of("PATIENT","EMERGENCY_STAFF","ADMIN"),"LOCAL",Set.of(),"No patient data");
    add("emergency.mine",Set.of("PATIENT"),"/api/emergency/me",Set.of(),"JWT subject only");
    add("pharmacy.catalog",Set.of("PATIENT","DOCTOR","PHARMACIST","ADMIN"),"/api/pharmacy/medications",Set.of(),"Catalog information only");
    add("billing.mine",Set.of("PATIENT"),"/api/billing/me",Set.of(),"JWT subject only");
    add("billing.invoices",Set.of("BILLING_STAFF","ADMIN"),"/api/billing/invoices",Set.of(),"Authorized billing staff; aggregate output only");
    add("nutrition.foods",Set.of("PATIENT","DOCTOR","ADMIN"),"/api/nutrition/foods",Set.of(),"Public sourced food data for authenticated users");
    add("nutrition.profile",Set.of("PATIENT"),"/api/nutrition/profile",Set.of(),"JWT subject only");
    add("nutrition.recommendations",Set.of("PATIENT"),"/api/nutrition/recommendations",Set.of(),"JWT subject only; transparent nonclinical rules");
    add("nutrition.progress",Set.of("PATIENT"),"/api/nutrition/progress",Set.of(),"JWT subject only");
    add("pharmacy.prescription",Set.of("PHARMACIST","ADMIN"),"/api/prescriptions/dispensing/{prescriptionId}",Set.of("prescriptionId"),"Authorized pharmacist projection only");
    add("billing.invoice",Set.of("PATIENT","BILLING_STAFF","ADMIN"),"/api/billing/invoices/{invoiceId}",Set.of("invoiceId"),"Backend independently enforces owner or billing role");
    add("emergency.requests",Set.of("EMERGENCY_STAFF","ADMIN"),"/api/emergency",Set.of(),"Authorized emergency staff only; administrative labels, no model triage");
    add("operations.summary",Set.of("NURSE","RECEPTIONIST","ADMIN"),"/api/appointments/operations/summary",Set.of(),"Authorized aggregates; no patient identifiers");
    add("operations.labSummary",Set.of("LAB_STAFF","ADMIN"),"/api/lab/orders",Set.of(),"Laboratory aggregate only");
  }
  private void add(String n,Set<String> roles,String endpoint,Set<String> arguments,String ownership){tools.put(n,new Tool(n,roles,endpoint,arguments,ownership,"READ_ONLY"));}
  public Collection<Tool> all(){return Collections.unmodifiableCollection(tools.values());}
  public Tool authorize(AgentRegistry.Agent agent,String name,Map<String,String> args,String role){
    Tool tool=tools.get(name);
    if(tool==null)throw new ResponseStatusException(BAD_REQUEST,"Unregistered tool");
    if(!agent.tools().contains(name)||!tool.roles().contains(role))throw new ResponseStatusException(FORBIDDEN,"Tool not authorized");
    if(args==null)args=Map.of();
    if(!args.keySet().equals(tool.arguments()))throw new ResponseStatusException(BAD_REQUEST,"Arguments do not match the registered schema");
    for(String argument:tool.arguments())try{UUID.fromString(args.get(argument));}catch(RuntimeException e){throw new ResponseStatusException(BAD_REQUEST,argument+" must be a UUID");}
    return tool;
  }
  public JsonNode execute(Tool tool,Map<String,String> args,String authorization){
    if(tool.endpoint().equals("LOCAL"))return mapper.valueToTree(Map.of("instructions","For a possible medical emergency contact local emergency services immediately. This application is not emergency dispatch or clinical triage."));
    String path=tool.endpoint();if(args!=null)for(String argument:tool.arguments())path=path.replace("{"+argument+"}",UUID.fromString(args.get(argument)).toString());
    try{
      HttpRequest request=HttpRequest.newBuilder(gateway.resolve(path)).timeout(Duration.ofSeconds(5)).header("Authorization",authorization).GET().build();
      var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
      try(var input=response.body()){
        if(response.statusCode()==401||response.statusCode()==403)throw new ResponseStatusException(FORBIDDEN,"Downstream ownership or role authorization denied");
        if(response.statusCode()!=200)throw new ResponseStatusException(BAD_GATEWAY,"Registered backend tool is unavailable");
        byte[] bytes=input.readNBytes(256001);if(bytes.length>256000)throw new ResponseStatusException(BAD_GATEWAY,"Tool response exceeds limit");
        JsonNode result=mapper.readTree(bytes);
        if(tool.name().equals("operations.summary"))return result;
        if(tool.name().startsWith("operations.")||tool.name().equals("billing.invoices"))return mapper.valueToTree(Map.of("count",result.isArray()?result.size():1));
        return filter(result,0);
      }
    }catch(ResponseStatusException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(BAD_GATEWAY,"Tool request failed or timed out");}
  }
  // Tool results are data only. They are NEVER interpreted as instructions, URLs, SQL or tool calls.
  private static final Set<String> FIELDS=Set.of("id","appointmentId","patientId","doctorId","firstName","lastName","specialization","qualification","department","experienceYears","consultationFee","available","appointmentDate","appointmentTime","queueNumber","status","reason","diagnosis","symptoms","treatmentPlan","medications","visitDate","testName","resultSummary","details","referenceRange","abnormal","resultDate","prescriptionDate","medicines","medicineName","dosage","frequency","duration","instructions","name","stock","unit","kcal","protein","carbohydrate","fat","source","goal","progress","total","currency","items","description","amount","paymentStatus","createdAt","message");
  JsonNode filter(JsonNode input,int depth){
    if(depth>5)return NullNode.instance;
    if(input.isArray()){ArrayNode out=mapper.createArrayNode();for(int i=0;i<Math.min(input.size(),100);i++)out.add(filter(input.get(i),depth+1));return out;}
    if(input.isObject()){ObjectNode out=mapper.createObjectNode();input.fields().forEachRemaining(e->{if(FIELDS.contains(e.getKey())||Set.of("foods","basis","unitPrice","quantity").contains(e.getKey()))out.set(e.getKey(),filter(e.getValue(),depth+1));});return out;}
    if(input.isTextual())return TextNode.valueOf(input.asText().substring(0,Math.min(input.asText().length(),2000)));
    return input;
  }
}
