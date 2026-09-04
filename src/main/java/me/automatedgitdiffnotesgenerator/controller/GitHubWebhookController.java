package me.automatedgitdiffnotesgenerator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.service.GitHubTagService;
import me.automatedgitdiffnotesgenerator.producer.ReleaseNoteJobProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;
import java.util.HexFormat;

@Slf4j
@RestController
@RequestMapping("/api/webhooks")
public class GitHubWebhookController {
    private static final String HMAC_SHA256 = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final GitHubTagService tagService;
    private final ReleaseNoteJobProducer jobProducer;

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    public GitHubWebhookController(
            ObjectMapper objectMapper,
            GitHubTagService tagService,
            ReleaseNoteJobProducer jobProducer
    ) {
        this.objectMapper = objectMapper;
        this.tagService = tagService;
        this.jobProducer = jobProducer;
    }

    @Operation(security = @SecurityRequirement(name = "githubWebhookSignature"))
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/github")
    public ResponseEntity<?> createGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody String rawBody
    ) {
        log.info("Received GitHub webhook. Event type: {}", event);

        if (signature == null || !signature.startsWith("sha256=")) {
            log.warn("Webhook rejected: Missing or invalid X-Hub-Signature-256 header format");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid signature format");
        }

        if (!isValidSignature(rawBody, signature)) {
            log.warn("Webhook rejected: Invalid signature verify failure");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"release".equalsIgnoreCase(event)) {
            log.info("Webhook ignored: Event type '{}' is not supported", event);
            return ResponseEntity.ok("Ignored event type " + event);
        }

        try {
            JsonNode root = objectMapper.readTree(rawBody);
            String action = root.path("action").asString();

            if (!"published".equals(action)) {
                log.info("Webhook ignored: Release action '{}' is not 'published'", action);
                return ResponseEntity.ok("Ignored action " + action);
            }

            String repoOwner = root.path("repository").path("owner").path("login").asString();
            String repoName = root.path("repository").path("name").asString();
            String toTag = root.path("release").path("tag_name").asString();

            if (repoOwner.isEmpty() || repoName.isEmpty() || toTag.isEmpty()) {
                log.warn("Webhook validation failed: Missing required fields (owner, name, or tag_name)");
                return ResponseEntity.unprocessableContent().body("Required webhook payload fields are missing");
            }

            String repository = repoOwner + "/" + repoName;
            log.info("Processing published release for repository: {}, tag: {}", repository, toTag);

            String fromTag;
            try {
                fromTag = tagService.getPreviousTag(repoOwner + "/" + repoName);
            } catch (IOException e) {
                log.error("Failed to fetch previous tag from GitHub API for repository: {}", repository, e);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Could not reach GitHub to resolve previous tag");
            }
            if (fromTag == null) {
                log.info("Webhook workflow skipped: Repository {} has only one release (no previous tags found)", repository);
                return ResponseEntity.ok("Repository has only one release");
            }

            var releaseJob = new ReleaseNoteJob(repoOwner, repoName, fromTag, toTag);
            jobProducer.releaseNoteJob(releaseJob);

            log.info("Successfully queued new release job for repository: {}, tags: {} -> {}", repository, fromTag, toTag);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("Queued");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid JSON payload");
        }
    }

    private boolean isValidSignature(String payload, String signatureHeader) {
        try {
            String expectedHex = signatureHeader.substring(7);

            byte[] expectedHashBytes = HexFormat.of().parseHex(expectedHex);

            SecretKeySpec secretKeySpec = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(secretKeySpec);

            byte[] computedHashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            return MessageDigest.isEqual(computedHashBytes, expectedHashBytes);
        } catch (Exception e) {
            log.error("Critical error during GitHub webhook signature validation", e);
            return false;
        }
    }

}
