package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GitCompareService {

    @Value("${github.api.token}")
    private String token;

    private final HttpClient httpClient;
    private final JsonMapper mapper;
    public GitCompareService(HttpClient httpClient, JsonMapper mapper) {
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public String getCommitDiff(GenerateNoteRequest request) throws Exception {
        String url = String.format(
                "https://api.github.com/repos/%s/%s/compare/%s...%s",
                request.repoOwner(), request.repoName(),
                request.fromTag(), request.toTag()
        );

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("GitHub API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
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