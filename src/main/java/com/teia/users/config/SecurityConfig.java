package com.teia.users.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teia.users.dto.ErrorResponse;
import com.teia.users.security.InternalRequestFilter;
import com.teia.users.security.JwtAuthFilter;
import com.teia.users.security.TwilioSignatureFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final InternalRequestFilter internalRequestFilter;
    private final TwilioSignatureFilter twilioSignatureFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, InternalRequestFilter internalRequestFilter,
                          TwilioSignatureFilter twilioSignatureFilter, ObjectMapper objectMapper) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.internalRequestFilter = internalRequestFilter;
        this.twilioSignatureFilter = twilioSignatureFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for stateless JWT authentication
            .csrf(csrf -> csrf.disable())

            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Configure authorization rules
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers("/auth/**").permitAll()

                // Twilio webhook endpoints (unauthenticated - Twilio cannot send auth headers)
                // Security is provided by Twilio request validation and credit checking
                .requestMatchers("/twilio/**").permitAll()

                // Internal service-to-service endpoints (authenticated by InternalRequestFilter)
                .requestMatchers("/internal/**").permitAll()

                // Stripe webhook endpoint (unauthenticated - uses Stripe signature verification)
                .requestMatchers("/billing/webhook").permitAll()

                // Swagger/OpenAPI documentation
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/api-docs/**",
                    "/v3/api-docs/**"
                ).permitAll()

                // Health/actuator endpoints (if added later)
                .requestMatchers("/actuator/health").permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )

            // Add JWT filter before UsernamePasswordAuthenticationFilter first so that
            // JwtAuthFilter has a known position in the chain (required by Spring Security 6
            // before it can be used as a reference class in a subsequent addFilterBefore call).
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Add InternalRequestFilter before JwtAuthFilter for /internal/** paths
            .addFilterBefore(internalRequestFilter, JwtAuthFilter.class)

            // Add TwilioSignatureFilter before JwtAuthFilter for /twilio/inbound/** paths
            .addFilterBefore(twilioSignatureFilter, JwtAuthFilter.class)

            // Custom exception handling for 401 and 403 responses
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse error = new ErrorResponse(
                        401,
                        "Unauthorized",
                        "Authentication required",
                        request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), error);
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    ErrorResponse error = new ErrorResponse(
                        403,
                        "Forbidden",
                        "Access denied: insufficient permissions",
                        request.getRequestURI()
                    );
                    objectMapper.writeValue(response.getOutputStream(), error);
                })
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowUrlEncodedDoubleSlash(true);
        return firewall;
    }
}
