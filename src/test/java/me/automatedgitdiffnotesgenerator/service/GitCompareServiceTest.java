package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@RestClientTest(GitCompareService.class)
class GitCompareServiceTest {

    private static final String COMPARE_URL =
            "https://api.github.com/repos/octocat/hello-world/compare/v1.0.0...v2.0.0";

    @Autowired
    private GitCompareService service;

    @Autowired
    private MockRestServiceServer mockServer;

    private final GenerateNoteRequest request =
            new GenerateNoteRequest("octocat", "hello-world", "v1.0.0", "v2.0.0");

    private void withToken() {
        ReflectionTestUtils.setField(service, "token", "test-token");
    }

    @Test
    void successfulComparison_buildsFormattedCommitAndFileContext() {
        withToken();

        String githubJson = """
                {
                  "commits": [
                    { "sha": "abc1234567890", "commit": { "message": "Fix null pointer bug\\n\\nLonger body here" } },
                    { "sha": "def9876543210", "commit": { "message": "Add caching layer" } }
                  ],
                  "files": [
                    { "filename": "src/Main.java", "status": "modified", "additions": 12, "deletions": 3 },
                    { "filename": "README.md", "status": "added", "additions": 5, "deletions": 0 }
                  ]
                }
                """;

        mockServer.expect(requestTo(COMPARE_URL))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andRespond(withSuccess(githubJson, MediaType.APPLICATION_JSON));

        String result = service.getCommitDiff(request);

        assertThat(result)
                .contains("Commits:")
                .contains("- Fix null pointer bug (abc1234)")
                .contains("- Add caching layer (def9876)")
                .contains("Changed files:")
                .contains("- [modified] src/Main.java (+12 / -3)")
                .contains("- [added] README.md (+5 / -0)")
                .doesNotContain("Longer body here");

        mockServer.verify();
    }

    @Test
    void notFoundStatus_throwsGitApiExceptionWithMappedStatus() {
        withToken();

        mockServer.expect(requestTo(COMPARE_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"message\":\"Not Found\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.getCommitDiff(request))
                .isInstanceOf(GitApiException.class)
                .hasMessageContaining("404")
                .extracting(e -> ((GitApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        mockServer.verify();
    }

    @Test
    void serverError_fallsBackAppropriately() {
        withToken();

        mockServer.expect(requestTo(COMPARE_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("upstream unavailable"));

        assertThatThrownBy(() -> service.getCommitDiff(request))
                .isInstanceOf(GitApiException.class)
                .extracting(e -> ((GitApiException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        mockServer.verify();
    }

    @Test
    void transportFailure_wrappedAsGitApiException() {
        withToken();

        mockServer.expect(requestTo(COMPARE_URL))
                .andRespond(req -> { throw new IOException("connection reset by peer"); });

        assertThatThrownBy(() -> service.getCommitDiff(request))
                .isInstanceOf(GitApiException.class)
                .hasMessageContaining("Network error")
                .hasCauseInstanceOf(ResourceAccessException.class);

        mockServer.verify();
    }
}