package me.automatedgitdiffnotesgenerator.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Automated Git Diff Release Notes Generator")
                        .version("1.0")
                        .description("""
                                Generates AI-powered release notes from GitHub commit diffs.
                                Trigger generation manually via /api/release-notes/generate
                                (and async version /api/release-notes/generate-async),
                                or automatically via a GitHub release webhook.
                                """)
                        .contact(new Contact()
                                .name("Danylo Filatov")
                                .url("https://github.com/filatovdanylo")
                        )
                )
                .components(new Components()
                .addSecuritySchemes("githubWebhookSignature",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Hub-Signature-256")
                                .description("HMAC-SHA256 signature GitHub sends to verify webhook authenticity"))
                );
    }
}
