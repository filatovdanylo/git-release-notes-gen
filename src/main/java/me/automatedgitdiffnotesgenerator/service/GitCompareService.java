package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.dto.PRInfo;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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

        String responseBody = getComparisonInfo(request);

        JsonNode root = mapper.readTree(responseBody);
        StringBuilder context = new StringBuilder();

        getCommitAndPRInfo(root, context, request);

        context.append("\nChanged files:\n");
        for (JsonNode fileNode : root.get("files")) {
            context.append("- [").append(fileNode.get("status").asString()).append("] ")
                    .append(fileNode.get("filename").asString())
                    .append(" (+").append(fileNode.get("additions").asInt())
                    .append(" / -").append(fileNode.get("deletions").asInt()).append(")\n");
        }

        return context.toString();
    }

    private String getComparisonInfo(GenerateNoteRequest request) {
        try {
            return restClient.get()
                    .uri("/repos/{owner}/{repo}/compare/{from}...{to}",
                            request.repoOwner(), request.repoName(), request.fromTag(), request.toTag())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                        HttpStatus resolved = HttpStatus.resolve(res.getStatusCode().value());
                        HttpStatus finalStatus = resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
                        String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new GitApiException(
                                "GitHub API error (" + res.getStatusCode().value() + "): " + errorBody,
                                finalStatus
                        );
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new GitApiException("Network error connecting to GitHub API", e);
        }
    }

    private void getCommitAndPRInfo(
            JsonNode root,
            StringBuilder context,
            GenerateNoteRequest request
    ) {
        HashMap<String, PRInfo> prInfo = new HashMap<>();
        int requestThreshold = 100;

        context.append("Commits:\n");
        for (JsonNode commitNode : root.get("commits")) {
            String sha = commitNode.get("sha").asString().substring(0, 7);
            String message = commitNode.get("commit").get("message").asString()
                    .lines().findFirst().orElse("(empty message)");
            context.append("- ").append(message).append(" (").append(sha).append(")\n");

            if (requestThreshold <= 0) {
                continue;
            }
            requestThreshold--;
            var prs = restClient.get()
                    .uri("/repos/{owner}/{repo}/commits/{sha}/pulls",
                            request.repoOwner(), request.repoName(), commitNode.get("sha").asString())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                        HttpStatus resolved = HttpStatus.resolve(res.getStatusCode().value());
                        HttpStatus finalStatus = resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
                        String errorBody = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        throw new GitApiException(
                                "GitHub API error (" + res.getStatusCode().value() + "): " + errorBody,
                                finalStatus
                        );
                    })
                    .body(JsonNode.class);

            if (prs.isEmpty()) continue;
            for (JsonNode pr : prs) {
                String number = pr.get("number").asString();
                String title = pr.get("title").asString();
                String url  = pr.get("html_url").asString();
                String user = pr.get("user").get("login").asString();
                prInfo.putIfAbsent(
                        number,
                        new PRInfo(title, url, user)
                );
            }
            context.append("Pull requests info:\n");
            for (var pr : prInfo.entrySet()) {
                context.append("#").append(pr.getKey()).append(": ").append(pr.getValue()).append("\n");
            }
        }

    }

}