package com.teia.users.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    /**
     * Security scheme name for internal HMAC authentication.
     * Used by /internal/** endpoints.
     */
    public static final String INTERNAL_HMAC_SCHEME = "InternalHmac";

    @Bean
    public OpenAPI teiaUsersOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TEIA Users API")
                        .description("User and credential management service for the TEIA Voice AI Platform.\n\n" +
                                "## Authentication\n\n" +
                                "### JWT Bearer Token (default)\n" +
                                "Most endpoints require a JWT access token in the Authorization header.\n\n" +
                                "### Internal HMAC (service-to-service)\n" +
                                "Endpoints under `/internal/**` use HMAC-SHA256 authentication for service-to-service calls from TEIA Core.\n\n" +
                                "**Required Headers:**\n" +
                                "- `X-Internal-Key`: HMAC-SHA256 signature (hex-encoded)\n" +
                                "- `X-Timestamp`: Unix timestamp in milliseconds\n" +
                                "- `X-Client-ID`: Client identifier (TEIA Core instance UUID)\n\n" +
                                "**Signature:** `HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET)`")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Dev-Team")
                                .url("https://example.com/")
                                .email("admin@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")))
                .components(new Components()
                        // JWT Bearer authentication for standard endpoints
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT access token from /auth/login"))
                        // Internal HMAC authentication for /internal/** endpoints
                        .addSecuritySchemes(INTERNAL_HMAC_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Key")
                                .description("HMAC-SHA256 signature for internal service-to-service authentication. " +
                                        "Computed as: HMAC-SHA256(requestBody + timestamp, INTERNAL_SECRET). " +
                                        "Also requires X-Timestamp and X-Client-ID headers.")))
                // Default security requirement (JWT) - can be overridden per-endpoint
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
