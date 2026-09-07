package me.automatedgitdiffnotesgenerator.controller;

import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import me.automatedgitdiffnotesgenerator.producer.ReleaseNoteJobProducer;
import me.automatedgitdiffnotesgenerator.service.GitHubTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubWebhookControllerTest {

    private static final String WEBHOOK_SECRET = "test-secret";

    private MockMvc mockMvc;
    private GitHubTagService tagService;
    private ReleaseNoteJobProducer jobProducer;

    @BeforeEach
    void setUp() {
        tagService = mock(GitHubTagService.class);
        jobProducer = mock(ReleaseNoteJobProducer.class);

        GitHubWebhookController controller = new GitHubWebhookController(
                JsonMapper.builder().build(),
                tagService,
                jobProducer
        );
        ReflectionTestUtils.setField(controller, "webhookSecret", WEBHOOK_SECRET);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // Compute a valid HMAC signature in the similar way GitHub would have done that
    private String sign(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return "sha256=" + HexFormat.of().formatHex(hash);
    }

    private String publishedReleasePayload(String owner, String repo, String tag) {
        return """
                {
                  "action": "published",
                  "repository": { "name": "%s", "owner": { "login": "%s" } },
                  "release": { "tag_name": "%s" }
                }
                """.formatted(repo, owner, tag);
    }

    @Test
    void missingSignatureHeader_returns401() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void malformedSignaturePrefix_returns401() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", "not-a-valid-format")
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void signatureComputedWithWrongSecret_returns401() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");
        String badSignature = "sha256=" + "0".repeat(64);

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", badSignature)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void nonReleaseEvent_isIgnoredWith200() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "push")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isOk());

        verifyNoInteractions(tagService, jobProducer);
    }

    @Test
    void nonPublishedAction_isIgnoredWith200() throws Exception {
        String payload = """
                {
                  "action": "deleted",
                  "repository": { "name": "hello-world", "owner": { "login": "octocat" } },
                  "release": { "tag_name": "v2.0.0" }
                }
                """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isOk());

        verifyNoInteractions(tagService, jobProducer);
    }

    @Test
    void gitHubApiUnreachableWhenResolvingTag_returns502() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");
        when(tagService.getPreviousTag(eq("octocat/hello-world"), eq("v2.0.0")))
                .thenThrow(new IOException("connection reset"));

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isBadGateway());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void tagNotFound_returns404() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");
        when(tagService.getPreviousTag(eq("octocat/hello-world"), eq("v2.0.0")))
                .thenThrow(new TagNotFoundException("Tag 'v2.0.0' not found"));

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isNotFound());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void validPublishedRelease_queuesJobAndReturns202() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");
        when(tagService.getPreviousTag(eq("octocat/hello-world"), eq("v2.0.0")))
                .thenReturn("v1.0.0");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isAccepted());

        verify(jobProducer, times(1)).releaseNoteJob(any());
    }
}