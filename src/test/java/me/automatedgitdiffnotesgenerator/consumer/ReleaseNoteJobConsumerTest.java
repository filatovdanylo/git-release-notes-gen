package me.automatedgitdiffnotesgenerator.consumer;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.GitHubTagService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import me.automatedgitdiffnotesgenerator.service.ReleaseNotesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseNoteJobConsumerTest {

    private GitCompareService compareService;
    private NoteGenerationService generationService;
    private ReleaseNoteRepository noteRepository;
    private ReleaseNotesService notesService;
    private GitHubTagService tagService;
    private ReleaseNoteJobConsumer consumer;

    @Captor
    private ArgumentCaptor<ReleaseNote> captor;

    private final ReleaseNoteJob job = new ReleaseNoteJob("octocat", "hello-world", "v1.0.0", "v2.0.0");
    private final ReleaseNoteJob webhookJob = new ReleaseNoteJob("octocat", "hello-world", null, "v2.0.0");
    private final ReleaseNoteJob resolvedJob = new ReleaseNoteJob("octocat", "hello-world", "v1.0.0", "v2.0.0");

    @BeforeEach
    void setUp() {
        compareService = mock(GitCompareService.class);
        generationService = mock(NoteGenerationService.class);
        noteRepository = mock(ReleaseNoteRepository.class);
        notesService = mock(ReleaseNotesService.class);
        tagService = mock(GitHubTagService.class);

        consumer = new ReleaseNoteJobConsumer(
                compareService,
                generationService,
                noteRepository,
                notesService,
                tagService
        );
    }

    private ReleaseNote existingProcessingNote() {
        ReleaseNote note = new ReleaseNote();
        note.setRepoOwner(job.repoOwner());
        note.setRepoName(job.repoName());
        note.setFromTag(job.fromTag());
        note.setToTag(job.toTag());
        note.setStatus(ReleaseNote.Status.PROCESSING);
        return note;
    }

    @Test
    void jobAlreadyClaimed_skipsProcessingEntirely() {
        when(notesService.tryClaim(job)).thenReturn(false);

        consumer.handleJob(job);

        verifyNoInteractions(compareService, generationService, tagService);
        verify(noteRepository, never()).findByRepoOwnerAndRepoNameAndFromTagAndToTag(any(), any(), any(), any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void claimSucceedsButNoteMissing_logsAndReturnsWithoutProcessing() {
        when(notesService.tryClaim(job)).thenReturn(true);
        when(noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(), job.repoName(), job.fromTag(), job.toTag()))
                .thenReturn(Optional.empty());

        consumer.handleJob(job);

        verifyNoInteractions(compareService, generationService, tagService);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void successfulGeneration_marksNoteCompletedWithContent() {
        ReleaseNote note = existingProcessingNote();

        when(notesService.tryClaim(job)).thenReturn(true);
        when(noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(), job.repoName(), job.fromTag(), job.toTag()))
                .thenReturn(Optional.of(note));
        when(compareService.getCommitDiff(any(GenerateNoteRequest.class)))
                .thenReturn("Commits:\n- fix bug (abc1234)\n");
        when(generationService.generate("Commits:\n- fix bug (abc1234)\n"))
                .thenReturn("## Fixes\n- Fixed a bug");

        consumer.handleJob(job);

        verifyNoInteractions(tagService);
        verify(noteRepository).save(captor.capture());

        ReleaseNote saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReleaseNote.Status.COMPLETED);
        assertThat(saved.getContent()).isEqualTo("## Fixes\n- Fixed a bug");
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void gitCompareServiceThrows_marksNoteFailedWithGenericMessage() {
        ReleaseNote note = existingProcessingNote();

        when(notesService.tryClaim(job)).thenReturn(true);
        when(noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(), job.repoName(), job.fromTag(), job.toTag()))
                .thenReturn(Optional.of(note));
        when(compareService.getCommitDiff(any(GenerateNoteRequest.class)))
                .thenThrow(new GitApiException("GitHub API error (502): upstream failure"));

        consumer.handleJob(job);

        verify(noteRepository).save(captor.capture());

        ReleaseNote saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ReleaseNote.Status.FAILED);
        assertThat(saved.getContent()).isNull();

        assertThat(saved.getErrorMessage())
                .isEqualTo("Generation failed. See server logs for details")
                .doesNotContain("upstream failure");
    }

    @Test
    void noteGenerationServiceThrows_marksNoteFailed() {
        ReleaseNote note = existingProcessingNote();

        when(notesService.tryClaim(job)).thenReturn(true);
        when(noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(), job.repoName(), job.fromTag(), job.toTag()))
                .thenReturn(Optional.of(note));
        when(compareService.getCommitDiff(any(GenerateNoteRequest.class)))
                .thenReturn("Commits:\n- fix bug (abc1234)\n");
        when(generationService.generate(any()))
                .thenThrow(new RuntimeException("Ollama connection refused"));

        consumer.handleJob(job);

        verify(noteRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(ReleaseNote.Status.FAILED);
        assertThat(captor.getValue().getErrorMessage())
                .doesNotContain("Ollama connection refused");
    }

    @Test
    void webhookJob_resolvesPreviousTagThenGeneratesSuccessfully() throws Exception {
        ReleaseNote note = existingProcessingNote();

        when(tagService.getPreviousTag("octocat/hello-world", "v2.0.0")).thenReturn("v1.0.0");
        when(notesService.tryClaim(resolvedJob)).thenReturn(true);
        when(noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                "octocat", "hello-world", "v1.0.0", "v2.0.0"))
                .thenReturn(Optional.of(note));
        when(compareService.getCommitDiff(any(GenerateNoteRequest.class)))
                .thenReturn("Commits:\n- fix bug (abc1234)\n");
        when(generationService.generate("Commits:\n- fix bug (abc1234)\n"))
                .thenReturn("## Fixes\n- Fixed a bug");

        consumer.handleJob(webhookJob);

        verify(tagService).getPreviousTag("octocat/hello-world", "v2.0.0");
        verify(notesService).tryClaim(resolvedJob);
        verify(noteRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReleaseNote.Status.COMPLETED);
    }

    @Test
    void webhookJob_firstReleaseHasNoPreviousTag_skipsClaimAndGeneration() throws Exception {
        when(tagService.getPreviousTag("octocat/hello-world", "v2.0.0")).thenReturn(null);

        consumer.handleJob(webhookJob);

        verify(tagService).getPreviousTag("octocat/hello-world", "v2.0.0");
        verifyNoInteractions(notesService, compareService, generationService);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void webhookJob_tagNotFound_skipsClaimAndGeneration() throws Exception {
        when(tagService.getPreviousTag("octocat/hello-world", "v2.0.0"))
                .thenThrow(new TagNotFoundException("Tag 'v2.0.0' not found"));

        consumer.handleJob(webhookJob);

        verifyNoInteractions(notesService, compareService, generationService);
        verify(noteRepository, never()).save(any());
    }

    @Test
    void webhookJob_gitHubUnreachable_rethrowsForRetry() throws Exception {
        when(tagService.getPreviousTag("octocat/hello-world", "v2.0.0"))
                .thenThrow(new IOException("connection reset"));

        assertThatThrownBy(() -> consumer.handleJob(webhookJob))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to fetch previous tag");

        verifyNoInteractions(notesService, compareService, generationService);
        verify(noteRepository, never()).save(any());
    }
}
