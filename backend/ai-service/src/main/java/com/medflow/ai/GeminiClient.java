package com.medflow.ai;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Component public class GeminiClient {
  private final String key,model;private final ObjectMapper mapper;
  private final HttpClient http;private volatile boolean verified;
  @org.springframework.beans.factory.annotation.Autowired
  public GeminiClient(ObjectMapper mapper,@Value("${app.gemini-key:}")String key,@Value("${app.gemini-model:gemini-3.8-flash}")String model){this(mapper,key,model,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());}
  GeminiClient(ObjectMapper mapper,String key,String model,HttpClient http){this.mapper=mapper;this.key=key;this.model=model;this.http=http;}
  public boolean verified(){return verified;}
  public boolean configured(){return key!=null&&!key.isBlank()&&!Set.of("ADD_LATER","CHANGE_ME").contains(key);}
  public String explain(AgentRegistry.Agent agent,String tool,int count){
    if(!configured())throw new ResponseStatusException(SERVICE_UNAVAILABLE,"Gemini is not configured; no model response was generated");
    try{
      // Only canonical tool name and aggregate count leave this laptop. No prompt, identifier or clinical free text is transmitted.
      Map<String,Object> body=Map.of("model",model,"store",false,"system_instruction",agent.instruction()+" Do not claim medical verification. No tools are available; never request or execute an action.","input","Synthetic academic demonstration. Agent: "+agent.name()+". Authorized read-only tool: "+tool+". Returned item count: "+count+". Explain this workflow without inventing any patient information.","generation_config",Map.of("max_output_tokens",400));
      HttpRequest request=HttpRequest.newBuilder(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions")).timeout(Duration.ofSeconds(20)).header("Content-Type","application/json").header("x-goog-api-key",key).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
      var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());
      try(var input=response.body()){
        if(response.statusCode()==429)throw new ResponseStatusException(TOO_MANY_REQUESTS,"Model provider rate limit reached; retry later");
        if(response.statusCode()!=200)throw new ResponseStatusException(BAD_GATEWAY,"Gemini provider returned HTTP "+response.statusCode()+"; check key, model availability and API access. Provider response details are not exposed.");
        byte[] bytes=input.readNBytes(64001);if(bytes.length>64000)throw new ResponseStatusException(BAD_GATEWAY,"Model response exceeds limit");
        JsonNode node=mapper.readTree(bytes);if(!node.path("status").asText().equals("completed"))throw new ResponseStatusException(BAD_GATEWAY,"Model did not complete; tool requests are never executed");
        StringBuilder text=new StringBuilder();for(JsonNode step:node.path("steps"))if(step.path("type").asText().equals("model_output"))for(JsonNode content:step.path("content"))if(content.path("type").asText().equals("text"))text.append(content.path("text").asText()).append('\n');
        if(text.isEmpty())throw new ResponseStatusException(BAD_GATEWAY,"Model returned no text");
        verified=true;return text.substring(0,Math.min(text.length(),4000));
      }
    }catch(ResponseStatusException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(BAD_GATEWAY,"Model provider unavailable or timed out");}
  }
}
