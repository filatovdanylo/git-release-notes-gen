package me.automatedgitdiffnotesgenerator.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

@Configuration
@EnableMethodSecurity
public class AppConfig {

    private static final String SYSTEM_PROMPT = """
            You are a technical writer generating release notes for a software project.
            Given a list of commit messages, pull requests and changed files between two versions,
            produce clear, well-organized release notes in Markdown format.

            Structure the output with these sections (omit any that are empty):
            ## Features
            ## Fixes
            ## Breaking Changes
            ## Other Changes

            Be concise. Group related changes together. Do not invent information
            that isn't implied by the commits, pull request or file changes provided.
            """;

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newHttpClient();
    }

    @Bean
    public JsonMapper jsonMapper() {
        return new JsonMapper();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
