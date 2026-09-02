package me.automatedgitdiffnotesgenerator.controller;

import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.service.GitHubTagService;
import me.automatedgitdiffnotesgenerator.service.ReleaseNoteJobProducer;
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

    @PostMapping("/github")
    public ResponseEntity<String> createGitHubWebhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader(value = "X-GitHub-Signature256", required = false) String signature,
            @RequestBody String rawBody
    ) throws IOException {
        if (signature == null || !signature.startsWith("sha256=")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!isValidSignature(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if (!"release".equals(event)) {
            return ResponseEntity.ok("Ignored event type " + event);
        }

        JsonNode root = objectMapper.readTree(rawBody);
        String action = root.get("action").asString();

        if (!"published".equals(action)) {
            return ResponseEntity.ok("Ignored action " + action);
        }

        String repoOwner = root.get("repository").get("owner").get("login").asString();
        String repoName = root.get("repository").get("name").asString();
        String toTag = root.get("release").get("tag_name").asString();

        String fromTag = tagService.getPreviousTag(repoOwner + "/" + repoName);

        if (fromTag == null) {
            return ResponseEntity.ok("Repository has only one release");
        }

        var releaseJob = new ReleaseNoteJob(repoOwner, repoName, toTag, fromTag);
        jobProducer.releaseNoteJob(releaseJob);

        return ResponseEntity.status(HttpStatus.OK).body("Queued");
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
            return false;
        }
    }

}
