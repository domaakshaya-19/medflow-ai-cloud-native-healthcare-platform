package com.medflow.patient.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain security(
            HttpSecurity http,
            JwtFilter jwt
    ) throws Exception {

        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .exceptionHandling(exception ->
                        exception
                                .authenticationEntryPoint(
                                        (req, res, ex) ->
                                                writeError(
                                                        res,
                                                        401,
                                                        "Authentication required"
                                                )
                                )
                                .accessDeniedHandler(
                                        (req, res, ex) ->
                                                writeError(
                                                        res,
                                                        403,
                                                        "Access denied"
                                                )
                                )
                )

                .authorizeHttpRequests(auth ->
                        auth
                                .requestMatchers(
                                        "/actuator/health",
                                        "/actuator/info"
                                )
                                .permitAll()

                                .anyRequest()
                                .authenticated()
                )

                .addFilterBefore(
                        jwt,
                        UsernamePasswordAuthenticationFilter.class
                )

                .build();
    }

    @Bean
    JwtFilter jwtFilter(
            @Value("${app.jwt.secret}") String secret
    ) {
        return new JwtFilter(secret);
    }

    private static void writeError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json");

        response.getWriter().write(
                "{\"status\":" + status +
                        ",\"message\":\"" + message + "\"}"
        );
    }

    static class JwtFilter extends OncePerRequestFilter {

        private final SecretKey key;

        JwtFilter(String secret) {

            if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalStateException(
                        "JWT_SECRET must be at least 32 bytes"
                );
            }

            this.key = Keys.hmacShaKeyFor(
                    secret.getBytes(StandardCharsets.UTF_8)
            );
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest req,
                HttpServletResponse res,
                FilterChain chain
        ) throws ServletException, IOException {

            String header = req.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {

                try {

                    Claims claims = Jwts
                            .parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(
                                    header.substring(7)
                            )
                            .getPayload();

                    String role = claims.get(
                            "role",
                            String.class
                    );

                    if (!Set.of(
                            "PATIENT",
                            "DOCTOR",
                            "NURSE",
                            "RECEPTIONIST",
                            "LAB_STAFF",
                            "PHARMACIST",
                            "BILLING_STAFF",
                            "EMERGENCY_STAFF",
                            "ADMIN"
                    ).contains(role)) {

                        throw new IllegalArgumentException(
                                "Unknown role"
                        );
                    }

                    UUID subject = UUID.fromString(claims.getSubject());

                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    subject,
                                    null,
                                    List.of(
                                            new SimpleGrantedAuthority(
                                                    "ROLE_" + role
                                            )
                                    )
                            );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authentication);

                } catch (RuntimeException ignored) {

                    SecurityContextHolder
                            .clearContext();
                }
            }

            chain.doFilter(req, res);
        }
    }
}
