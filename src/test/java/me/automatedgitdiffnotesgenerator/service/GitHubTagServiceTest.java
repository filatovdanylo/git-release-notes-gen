package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.client.GitHubClientFactory;
import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GitHubTagServiceTest {
    @InjectMocks
    private GitHubTagService gitHubTagService;

    @Mock
    private GitHubClientFactory clientFactory;

    @Mock
    private GitHub mockGitHub;

    @Mock
    private GHRepository mockRepository;

    @Mock
    private PagedIterable<GHRelease> mockPagedIterable;

    @BeforeEach
    void setUp() throws IOException {
        when(clientFactory.create()).thenReturn(mockGitHub);
        lenient().when(mockGitHub.getRepository(anyString())).thenReturn(mockRepository);
        lenient().when(mockRepository.listReleases()).thenReturn(mockPagedIterable);
    }

    private GHRelease release(String tagName, boolean draft) {
        GHRelease release = mock(GHRelease.class);
        lenient().when(release.getTagName()).thenReturn(tagName);
        lenient().when(release.isDraft()).thenReturn(draft);
        return release;
    }

    private void givenReleases(GHRelease... releases) {
        PagedIterator<GHRelease> mockIterator = mock(PagedIterator.class);

        var listIterator = List.of(releases).iterator();

        when(mockIterator.hasNext()).thenAnswer(inv -> listIterator.hasNext());
        when(mockIterator.next()).thenAnswer(inv -> listIterator.next());

        when(mockPagedIterable.iterator()).thenReturn(mockIterator);
    }

    @Test
    void previousReleaseExistsForGivenRepository_returnsPreviousTag() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";

        givenReleases(release("2.0", false), release("1.0", false));

        String previousTag = gitHubTagService.getPreviousTag(fullRepoName, toTag);

        assertEquals("1.0", previousTag);
    }

    @Test
    void previousReleaseSkipsDraftInBetween_returnsNextNonDraftTag() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "3.0";

        givenReleases(
                release("3.0", false),
                release("2.5-draft", true),
                release("2.0", false)
        );

        String previousTag = gitHubTagService.getPreviousTag(fullRepoName, toTag);

        assertEquals("2.0", previousTag);
    }

    @Test
    void targetTagDoesNotExistForGivenRepository_throwsTagNotFoundException() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";

        givenReleases(release("1.0", false));

        assertThrows(TagNotFoundException.class, () -> gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void repositoryHasOnlyOneRelease_returnsNull() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";

        givenReleases(release("2.0", false));

        assertNull(gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void targetTagIsTheOldestReleaseInRepository_returnsNull() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "1.0";

        givenReleases(release("3.0", false), release("2.0", false), release("1.0", false));

        assertNull(gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void gitHubClientFactoryThrowsIOException_propagatesUnchanged() throws IOException {
        when(clientFactory.create()).thenThrow(new IOException("bad credentials"));

        assertThrows(IOException.class,
                () -> gitHubTagService.getPreviousTag("user/test-repo", "2.0"));
    }
}
