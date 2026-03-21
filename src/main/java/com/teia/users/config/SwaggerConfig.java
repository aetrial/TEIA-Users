package com.teia.users.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI teiaUsersOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TEIA Users API")
                        .description("User and credential management service for the TEIA Voice AI Platform")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Dev-Team")
                                .url("https://example.com/")
                                .email("admin@example.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
