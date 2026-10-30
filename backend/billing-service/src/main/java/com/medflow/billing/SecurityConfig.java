package com.medflow.billing;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http,JwtAuthenticationFilter jwt)throws Exception{return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->error(r,401,"Authentication required")).accessDeniedHandler((q,r,x)->error(r,403,"Access denied"))).authorizeHttpRequests(a->a.requestMatchers("/actuator/health","/actuator/health/**","/actuator/info").permitAll().anyRequest().authenticated()).addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();}
    private static void error(HttpServletResponse r,int status,String message)throws IOException{r.setStatus(status);r.setContentType("application/json");r.getWriter().write("{\"status\":"+status+",\"message\":\""+message+"\"}");}
}
