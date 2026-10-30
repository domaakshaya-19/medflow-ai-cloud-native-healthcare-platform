package com.medflow.ai;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> ROLES=Set.of("PATIENT","DOCTOR","NURSE","RECEPTIONIST","LAB_STAFF","PHARMACIST","BILLING_STAFF","EMERGENCY_STAFF","ADMIN");
    private final SecretKey key;
    public JwtAuthenticationFilter(@Value("${app.jwt.secret}")String secret){if(secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{String header=req.getHeader("Authorization");if(header!=null&&header.startsWith("Bearer "))try{Claims claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(header.substring(7)).getPayload();UUID subject=UUID.fromString(claims.getSubject());String role=claims.get("role",String.class),email=claims.get("email",String.class);if(claims.getExpiration()==null||!ROLES.contains(role)||email==null||email.isBlank())throw new IllegalArgumentException();SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(subject,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}catch(RuntimeException ignored){SecurityContextHolder.clearContext();}chain.doFilter(req,res);}
}
