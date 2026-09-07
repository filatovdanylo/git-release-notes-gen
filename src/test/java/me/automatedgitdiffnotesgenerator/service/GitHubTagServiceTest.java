package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.client.GitHubClientFactory;
import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTag;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
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
    private PagedIterable<GHTag> mockPagedIterable;

    @BeforeEach
    void setUp() throws IOException {
        when(clientFactory.create()).thenReturn(mockGitHub);
        lenient().when(mockGitHub.getRepository(anyString())).thenReturn(mockRepository);
        lenient().when(mockRepository.listTags()).thenReturn(mockPagedIterable);
    }

    @Test
    void previousTagExistsForGivenRepository_returnsPreviousTag() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";
        GHTag tag2 = mock(GHTag.class);
        GHTag tag1 = mock(GHTag.class);

        when(tag2.getName()).thenReturn("2.0");
        when(tag1.getName()).thenReturn("1.0");

        when(mockPagedIterable.toList()).thenReturn(List.of(tag2, tag1));

        String previousTag = gitHubTagService.getPreviousTag(fullRepoName, toTag);

        assertEquals("1.0", previousTag);
    }

    @Test
    void targetTagDoesNotExistForGivenRepository_throwsTagNotFoundException() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";
        GHTag otherTag = mock(GHTag.class);

        when(otherTag.getName()).thenReturn("1.0");

        when(mockPagedIterable.toList()).thenReturn(List.of(otherTag));

        assertThrows(TagNotFoundException.class, () -> gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void repositoryHasOnlyOneTag_returnsNull() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "2.0";
        GHTag theOnlyTag = mock(GHTag.class);

        when(theOnlyTag.getName()).thenReturn("2.0");

        when(mockPagedIterable.toList()).thenReturn(List.of(theOnlyTag));

        assertNull(gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void targetTagIsTheFirstTagInRepository_returnsNull() throws IOException {
        String fullRepoName = "user/test-repo";
        String toTag = "1.0";
        GHTag tag1 = mock(GHTag.class);
        GHTag tag2 = mock(GHTag.class);
        GHTag tag3 = mock(GHTag.class);

        when(tag1.getName()).thenReturn("1.0");
        when(tag2.getName()).thenReturn("2.0");
        when(tag3.getName()).thenReturn("3.0");

        when(mockPagedIterable.toList()).thenReturn(List.of(tag3, tag2, tag1));

        assertNull(gitHubTagService.getPreviousTag(fullRepoName, toTag));
    }

    @Test
    void gitHubClientFactoryThrowsIOException_propagatesUnchanged() throws IOException {
        when(clientFactory.create()).thenThrow(new IOException("bad credentials"));

        assertThrows(IOException.class,
                () -> gitHubTagService.getPreviousTag("user/test-repo", "2.0"));
    }
}
