package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.CommitsAndPRInfo;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.dto.PullRequest;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GitCompareService {

    @Value("${github.api.token}")
    private String token;

    private static final int THRESHOLD_COMMITS_NUMBER = 50;
    private static final int SEARCH_PER_PAGE = 100;
    private static final Pattern PR_NUMBER_PATTERN = Pattern.compile("#\\d+");

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

        var info = getCommitAndPRInfo(root);

        JsonNode commitsNode = root.get("commits");
        int commitCount = commitsNode != null && commitsNode.isArray() ? commitsNode.size() : 0;

        if (commitCount > THRESHOLD_COMMITS_NUMBER) {
            buildContextForBigRelease(context, info, root);
        } else {
            buildContextForSmallRelease(context, info, root, request);
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
                        throwGitApiException(res.getStatusCode().value(), res.getBody().readAllBytes());
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            throw new GitApiException("Network error connecting to GitHub API", e);
        }
    }

    private CommitsAndPRInfo getCommitAndPRInfo(JsonNode root) {
        List<String> commits = new ArrayList<>();
        Set<PullRequest> pullRequests = new HashSet<>();

        JsonNode commitsNode = root.get("commits");
        if (commitsNode == null || !commitsNode.isArray()) {
            return new CommitsAndPRInfo(commits, pullRequests);
        }

        for (JsonNode commitNode : commitsNode) {
            String sha = commitNode.get("sha").asString().substring(0, 7);
            String message = commitNode.get("commit").get("message").asString()
                    .lines().findFirst().orElse("(empty message)");
            Matcher matcher = PR_NUMBER_PATTERN.matcher(message);
            if (matcher.find()) {
                pullRequests.add(new PullRequest(matcher.group(), message));
            } else {
                String commit = "- " + message + " (" + sha + ")";
                commits.add(commit);
            }
        }

        return new CommitsAndPRInfo(commits, pullRequests);
    }

    private void buildContextForBigRelease(StringBuilder context, CommitsAndPRInfo info, JsonNode root) {
        context.append("Commits:\n");
        for (String commit : info.commits()) {
            context.append(commit).append("\n");
        }

        context.append("\nPull requests:\n");
        for (PullRequest pullRequest : info.pullRequests()) {
            context.append("- (").append(pullRequest.number()).append(") ").append(pullRequest.message()).append("\n");
        }

        int additions = 0;
        int deletions = 0;
        JsonNode filesNode = root.get("files");
        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileNode : filesNode) {
                additions += fileNode.get("additions").asInt();
                deletions += fileNode.get("deletions").asInt();
            }
        }

        context.append("\nFiles changes:\n");
        context.append("Added lines: ").append(additions).append("\n");
        context.append("Deleted lines: ").append(deletions).append("\n");
    }

    private void buildContextForSmallRelease(
            StringBuilder context,
            CommitsAndPRInfo info,
            JsonNode root,
            GenerateNoteRequest request
    ) {
        context.append("Commits:\n");
        for (String commit : info.commits()) {
            context.append(commit).append("\n");
        }

        JsonNode fromResponse = restClient.get()
                .uri("/repos/{owner}/{repo}/releases/tags/{tag}",
                        request.repoOwner(), request.repoName(), request.fromTag())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                    throwGitApiException(res.getStatusCode().value(), res.getBody().readAllBytes());
                })
                .body(JsonNode.class);

        JsonNode toResponse = restClient.get()
                .uri("/repos/{owner}/{repo}/releases/tags/{tag}",
                        request.repoOwner(), request.repoName(), request.toTag())
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                    throwGitApiException(res.getStatusCode().value(), res.getBody().readAllBytes());
                })
                .body(JsonNode.class);

        String fromDate = fromResponse.get("published_at").asString().substring(0, 10);
        String toDate = toResponse.get("published_at").asString().substring(0, 10);

        String searchQuery = String.format("repo:%s/%s is:pr is:merged merged:%s..%s",
                request.repoOwner(), request.repoName(), fromDate, toDate);

        context.append("\nPull requests:\n");
        for (JsonNode pullRequest : searchMergedPullRequests(searchQuery)) {
            int number = pullRequest.get("number").asInt();
            String title = pullRequest.get("title").asString();
            if (info.pullRequests().contains(new PullRequest("#" + number, null))) {
                context.append("- (#").append(number).append(") ").append(title).append("\n");
            }
        }

        context.append("\nChanged files:\n");
        JsonNode filesNode = root.get("files");
        if (filesNode != null && filesNode.isArray()) {
            for (JsonNode fileNode : filesNode) {
                context.append("- [").append(fileNode.get("status").asString()).append("] ")
                        .append(fileNode.get("filename").asString())
                        .append(" (+").append(fileNode.get("additions").asInt())
                        .append(" / -").append(fileNode.get("deletions").asInt()).append(")\n");
            }
        }
    }

    private List<JsonNode> searchMergedPullRequests(String searchQuery) {
        List<JsonNode> items = new ArrayList<>();
        int page = 1;
        int totalCount = Integer.MAX_VALUE;

        while (items.size() < totalCount) {
            final int currentPage = page;
            JsonNode searchResult = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search/issues")
                            .queryParam("q", searchQuery)
                            .queryParam("per_page", SEARCH_PER_PAGE)
                            .queryParam("page", currentPage)
                            .build())
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> !status.equals(HttpStatus.OK), (req, res) -> {
                        throwGitApiException(res.getStatusCode().value(), res.getBody().readAllBytes());
                    })
                    .body(JsonNode.class);

            if (searchResult == null) {
                break;
            }

            totalCount = searchResult.path("total_count").asInt(0);
            JsonNode pageItems = searchResult.get("items");
            if (pageItems == null || !pageItems.isArray() || pageItems.isEmpty()) {
                break;
            }

            for (JsonNode item : pageItems) {
                items.add(item);
            }

            if (pageItems.size() < SEARCH_PER_PAGE) {
                break;
            }
            page++;
        }

        return items;
    }

    private void throwGitApiException(int statusCode, byte[] errorBodyBytes) {
        HttpStatus resolved = HttpStatus.resolve(statusCode);
        HttpStatus finalStatus = resolved != null ? resolved : HttpStatus.BAD_GATEWAY;
        String errorBody = new String(errorBodyBytes, StandardCharsets.UTF_8);
        throw new GitApiException(
                "GitHub API error (" + statusCode + "): " + errorBody,
                finalStatus
        );
    }
}
