"""Create the five new service skeletons once. Never touches an existing service."""
from pathlib import Path
import csv
ROOT = Path(__file__).resolve().parent.parent
SERVICES = {'pharmacy':8088,'nutrition':8089,'billing':8090,'emergency':8091,'notification':8092}
for name in SERVICES:
    if (ROOT/'backend'/f'{name}-service').exists():
        raise SystemExit(f'Refusing to overwrite existing {name}-service')
base_pom=(ROOT/'backend/ai-service/pom.xml').read_text()
extra='''<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency><dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency><dependency><groupId>org.postgresql</groupId><artifactId>postgresql</artifactId><scope>runtime</scope></dependency><dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>test</scope></dependency>'''
common='''package com.medflow.DOMAIN;
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
'''
errors=(ROOT/'backend/ai-service/src/main/java/com/medflow/ai/Errors.java').read_text().replace('package com.medflow.ai;','package com.medflow.DOMAIN;').replace('@ExceptionHandler(Exception.class)', '@ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) ResponseEntity<?> conflict(Exception e){return ResponseEntity.status(409).body(Map.of("status",409,"message","Record conflicts with existing data or constraints"));}\n  @ExceptionHandler(Exception.class)')
for name,port in SERVICES.items():
    root=ROOT/'backend'/f'{name}-service'
    java=root/'src/main/java/com/medflow'/name
    res=root/'src/main/resources'
    java.mkdir(parents=True);(res/'db/migration').mkdir(parents=True)
    (root/'pom.xml').write_text(base_pom.replace('<artifactId>ai-service</artifactId>','<artifactId>'+name+'-service</artifactId>').replace('</dependencies>',extra+'</dependencies>'))
    (java/'Application.java').write_text(f'package com.medflow.{name};\n@org.springframework.boot.autoconfigure.SpringBootApplication @org.springframework.scheduling.annotation.EnableScheduling public class Application {{ public static void main(String[] args){{org.springframework.boot.SpringApplication.run(Application.class,args);}} }}')
    for file in ['JwtAuthenticationFilter.java','SecurityConfig.java']:
        (java/file).write_text((ROOT/'backend/ai-service/src/main/java/com/medflow/ai'/file).read_text().replace('package com.medflow.ai;',f'package com.medflow.{name};'))
    (java/'Access.java').write_text(common.replace('DOMAIN',name))
    (java/'Errors.java').write_text(errors.replace('DOMAIN',name))
    (res/'application.yml').write_text('''server:
  port: ${PREFIX_SERVICE_PORT:PORT}
  error:
    include-message: never
spring:
  application:
    name: DOMAIN-service
  datasource:
    url: ${PREFIX_DB_URL:jdbc:postgresql://localhost:5432/medflow_DOMAIN}
    username: ${PREFIX_DB_USER:medflow_DOMAIN}
    password: ${PREFIX_DB_PASSWORD}
  jackson:
    deserialization:
      fail-on-unknown-properties: true
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka}
app:
  jwt:
    secret: ${JWT_SECRET}
  gateway: ${API_GATEWAY_URL:http://localhost:8080}
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
'''.replace('PREFIX',name.upper()).replace('DOMAIN',name).replace(':PORT}',':'+str(port)+'}'))
print('Created five independent service skeletons without touching existing modules.')
