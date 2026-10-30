package com.medflow.ai;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api/ai") public class AiController {
  public record Request(@Size(max=30)String agent,@NotBlank @Size(max=80)String tool,@Size(max=4)Map<@Size(max=30)String,@Size(max=80)String> arguments,@Size(max=2000)String prompt,boolean invokeModel,boolean syntheticDemo,boolean consentToModel){}
  public record Response(String eventId,String agent,String tool,String mode,String modelStatus,JsonNode data,String explanation,String limitation){}
  private final AgentRegistry agents;private final ControlledTools tools;private final GeminiClient gemini;
  private final java.time.Clock clock;
  private final ConcurrentMap<String,Window> windows=new ConcurrentHashMap<>();private final Semaphore concurrency=new Semaphore(4);
  private record Window(long minute,int count){}
  public AiController(AgentRegistry agents,ControlledTools tools,GeminiClient gemini,java.time.Clock clock){this.agents=agents;this.tools=tools;this.gemini=gemini;this.clock=clock;}
  static String role(Authentication auth){return auth.getAuthorities().iterator().next().getAuthority().replaceFirst("^ROLE_","");}
  @GetMapping("/capabilities") public Map<String,Object> capabilities(Authentication auth){String role=role(auth);return Map.of("modelConfigured",gemini.configured(),"modelVerified",gemini.verified(),"agents",agents.all().stream().filter(a->a.roles().contains(role)).map(a->Map.of("name",a.name(),"instruction",a.instruction(),"tools",tools.all().stream().filter(t->a.tools().contains(t.name())&&t.roles().contains(role)).toList())).toList(),"policy","Read-only deterministic tool routing. Model is optional, never authoritative; synthetic demonstration consent required.");}
  @PostMapping("/requests") public Response request(@Valid @RequestBody Request request,Authentication auth,@RequestHeader("Authorization")String authorization){
    String role=role(auth);AgentRegistry.Agent agent=agents.select(request.agent(),request.prompt(),role);
    var args=request.arguments()==null?Map.<String,String>of():request.arguments();var tool=tools.authorize(agent,request.tool(),args,role);
    if(request.invokeModel()&&(!request.syntheticDemo()||!request.consentToModel()))throw new ResponseStatusException(BAD_REQUEST,"Explicit synthetic-demo and model-transmission consent are required");
    if(request.invokeModel()&&!gemini.configured())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Gemini key missing; read-only tools remain available without a model");
    long minute=clock.millis()/60000;
    if(windows.size()>10000){windows.entrySet().removeIf(e->e.getValue().minute()<minute);if(windows.size()>10000)throw new ResponseStatusException(TOO_MANY_REQUESTS,"Request capacity reached");}
    Window window=windows.compute(auth.getName(),(k,v)->new Window(minute,v==null||v.minute()!=minute?1:v.count()+1));
    if(window.count()>30||!concurrency.tryAcquire())throw new ResponseStatusException(TOO_MANY_REQUESTS,"Request limit reached");
    try{
      JsonNode data=tools.execute(tool,args,authorization);String mode=request.invokeModel()?"GEMINI":"CONTROLLED_TOOL";
      String explanation=request.invokeModel()?gemini.explain(agent,tool.name(),data.isArray()?data.size():1):"Authorized read-only data retrieved. No language model was invoked. "+agent.instruction();
      String event=UUID.randomUUID().toString();org.slf4j.LoggerFactory.getLogger(AiController.class).info("ai_event={} agent={} tool={} mode={} outcome=SUCCESS",event,agent.name(),tool.name(),mode);
      return new Response(event,agent.name(),tool.name(),mode,request.invokeModel()?"INVOKED":"NOT_INVOKED",data,explanation,"Not a diagnosis, treatment decision, emergency dispatch, payment action or medically verified output.");
    }finally{concurrency.release();}
  }
}
