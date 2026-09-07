package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class GitCompareService {

    @Value("${github.api.token}")
    private String token;

    private final RestClient restClient;
    private final JsonMapper mapper;

    public GitCompareService(RestClient.Builder restClientBuilder, JsonMapper mapper) {
        this.restClient = restClientBuilder
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
        this.mapper = mapper;
    }

    public String getCommitDiff(GenerateNoteRequest request) {

        String responseBody;
        try {
            responseBody = restClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{from}...{to}",
                            request.repoOwner(), request.repoName(), request.fromTag(), request.toTag())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                        HttpStatus resolved = HttpStatus.resolve(res.getStatusCode().value());
                        HttpStatus finalStatus = resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
                        String errorBody = new String(res.getBody().readAllBytes());
                        throw new GitApiException(
                                "GitHub API error (" + res.getStatusCode().value() + "): " + errorBody,
                                finalStatus
                        );
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new GitApiException("Network error connecting to GitHub API", e);
        }

        JsonNode root = mapper.readTree(responseBody);
        StringBuilder context = new StringBuilder();

        context.append("Commits:\n");
        for (JsonNode commitNode : root.get("commits")) {
            String sha = commitNode.get("sha").asString().substring(0, 7);
            String message = commitNode.get("commit").get("message").asString()
                    .lines().findFirst().orElse("(empty message)");
            context.append("- ").append(message).append(" (").append(sha).append(")\n");
        }

        context.append("\nChanged files:\n");
        for (JsonNode fileNode : root.get("files")) {
            context.append("- [").append(fileNode.get("status").asString()).append("] ")
                    .append(fileNode.get("filename").asString())
                    .append(" (+").append(fileNode.get("additions").asInt())
                    .append(" / -").append(fileNode.get("deletions").asInt()).append(")\n");
        }

        return context.toString();
    }
}