package com.moldy.moldymarket.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI moldyMarketOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Moldy Market API")
                        .description("API documentation for Moldy Market marketplace")
                        .version("1.0.0")
                        .contact(new Contact().name("Moldy Market").email("moldymarket@gmail.com"))
                )
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local server")
                ))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập Bearer Token để xác thực các API yêu cầu đăng nhập")
                        )
                );
    }
}
