package me.automatedgitdiffnotesgenerator.controller;

import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.producer.ReleaseNoteJobProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GitHubWebhookControllerTest {

    private static final String WEBHOOK_SECRET = "test-secret";

    private MockMvc mockMvc;
    private ReleaseNoteJobProducer jobProducer;

    @BeforeEach
    void setUp() {
        jobProducer = mock(ReleaseNoteJobProducer.class);

        GitHubWebhookController controller = new GitHubWebhookController(
                JsonMapper.builder().build(),
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

        verifyNoInteractions(jobProducer);
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

        verifyNoInteractions(jobProducer);
    }

    @Test
    void missingRequiredFields_returns422() throws Exception {
        String payload = """
                {
                  "action": "published",
                  "repository": { "name": "", "owner": { "login": "octocat" } },
                  "release": { "tag_name": "v2.0.0" }
                }
                """;

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isUnprocessableContent());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void invalidJsonPayload_returns400() throws Exception {
        String payload = "{ not-json";

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobProducer);
    }

    @Test
    void validPublishedRelease_queuesJobWithUnresolvedFromTagAndReturns202() throws Exception {
        String payload = publishedReleasePayload("octocat", "hello-world", "v2.0.0");

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "release")
                        .header("X-Hub-Signature-256", sign(payload))
                        .content(payload))
                .andExpect(status().isAccepted());

        ArgumentCaptor<ReleaseNoteJob> jobCaptor = ArgumentCaptor.forClass(ReleaseNoteJob.class);
        verify(jobProducer, times(1)).releaseNoteJob(jobCaptor.capture());

        assertThat(jobCaptor.getValue())
                .isEqualTo(new ReleaseNoteJob("octocat", "hello-world", null, "v2.0.0"));
    }
}
