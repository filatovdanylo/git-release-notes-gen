package me.automatedgitdiffnotesgenerator.client;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GitHubClientFactory {
    @Value("${github.api.token}")
    private String githubToken;

    public GitHub create() throws IOException {
        return new GitHubBuilder().withOAuthToken(githubToken).build();
    }
}
