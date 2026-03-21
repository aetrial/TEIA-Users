package com.teia.users.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.teia.users.models.enums.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT authentication filter that validates Bearer tokens on every request.
 * Extracts user claims and sets the SecurityContext for authenticated requests.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        // If no Authorization header or not Bearer token, continue without authentication
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            DecodedJWT decodedJwt = jwtService.validateToken(token);

            if (decodedJwt != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Long userId = jwtService.extractUserId(decodedJwt);
                String email = jwtService.extractEmail(decodedJwt);
                Role role = jwtService.extractRole(decodedJwt);

                if (userId != null && email != null && role != null) {
                    // Create authentication token with role as authority
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.name());

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            new JwtUserPrincipal(userId, email, role),
                            null,
                            Collections.singletonList(authority)
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    logger.debug("Authenticated user: {} with role: {}", email, role);
                }
            }
        } catch (Exception e) {
            logger.debug("JWT authentication failed: {}", e.getMessage());
            // Don't set authentication - let Spring Security handle the 401
        }

        filterChain.doFilter(request, response);
    }
}
