package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
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

    public String getCommitDiff(GenerateNoteRequest request) throws GitApiException {
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

        HttpResponse<String> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new GitApiException("Network error connecting to GitHub API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitApiException("GitHub API request was interrupted", e);
        }

        if (response.statusCode() != 200) {
            HttpStatus status = HttpStatus.resolve(response.statusCode());
            HttpStatus finalStatus = status != null ? status : HttpStatus.BAD_GATEWAY;
            throw new GitApiException(
                    "GitHub API error (" + response.statusCode() + "): " + response.body(),
                    finalStatus
            );
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