package com.medflow.nutrition;
import com.fasterxml.jackson.databind.*;
import java.net.*;import java.net.http.*;import java.time.*;import java.sql.Timestamp;import java.util.*;
import org.springframework.beans.factory.annotation.Value;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.core.Authentication;import org.springframework.stereotype.Component;import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Component public class Access {
 public final JdbcTemplate db;private final ObjectMapper mapper;private final URI gateway;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
 public Access(JdbcTemplate db,ObjectMapper mapper,@Value("${app.gateway}")String gateway){this.db=db;this.mapper=mapper;this.gateway=URI.create(gateway);}
 public UUID subject(Authentication a){return (UUID)a.getPrincipal();}
 public boolean has(Authentication a,String... roles){return a.getAuthorities().stream().anyMatch(g->Arrays.asList(roles).contains(g.getAuthority().replaceFirst("^ROLE_","")));}
 public void roles(Authentication a,String... roles){if(!has(a,roles))throw new ResponseStatusException(FORBIDDEN,"Role is not authorized");}
 public void owner(Authentication a,Object owner,String... staff){if(!subject(a).toString().equals(String.valueOf(owner))&&!has(a,staff))throw new ResponseStatusException(FORBIDDEN,"Resource belongs to another user");}
 public List<Map<String,Object>> rows(String sql,Object... args){return db.queryForList(sql,args).stream().map(row->{Map<String,Object> out=new LinkedHashMap<>();row.forEach((key,value)->{String[] words=key.toLowerCase(Locale.ROOT).split("_");StringBuilder name=new StringBuilder(words[0]);for(int i=1;i<words.length;i++)name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));Object v=value instanceof UUID?value.toString():value instanceof Timestamp?((Timestamp)value).toInstant().toString():value instanceof java.time.temporal.TemporalAccessor?value.toString():value;out.put(name.toString(),v);});return out;}).toList();}
 public Map<String,Object> one(String sql,Object... args){var rows=rows(sql,args);if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND,"Record not found");return rows.getFirst();}
 public JsonNode remote(String path,String bearer){try{var request=HttpRequest.newBuilder(gateway.resolve(path)).timeout(Duration.ofSeconds(4)).header("Authorization",bearer).GET().build();var response=http.send(request,HttpResponse.BodyHandlers.ofInputStream());try(var in=response.body()){if(response.statusCode()==401||response.statusCode()==403)throw new ResponseStatusException(FORBIDDEN,"Reference access denied");if(response.statusCode()==404)throw new ResponseStatusException(NOT_FOUND,"Reference not found");if(response.statusCode()!=200)throw new ResponseStatusException(BAD_GATEWAY,"Reference service unavailable");byte[] data=in.readNBytes(256001);if(data.length>256000)throw new ResponseStatusException(BAD_GATEWAY,"Reference exceeds limit");return mapper.readTree(data);}}catch(ResponseStatusException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(BAD_GATEWAY,"Reference request failed");}}
}
