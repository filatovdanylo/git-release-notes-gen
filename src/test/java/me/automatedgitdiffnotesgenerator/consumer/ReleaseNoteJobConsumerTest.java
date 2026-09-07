package me.automatedgitdiffnotesgenerator.consumer;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import me.automatedgitdiffnotesgenerator.exception.GitApiException;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import me.automatedgitdiffnotesgenerator.service.ReleaseNotesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReleaseNoteJobConsumerTest {

    private GitCompareService compareService;
    private NoteGenerationService generationService;
    private ReleaseNoteRepository noteRepository;
    private ReleaseNotesService notesService;
    private ReleaseNoteJobConsumer consumer;

    @Captor
    private ArgumentCaptor<ReleaseNote> captor;

    private final ReleaseNoteJob job = new ReleaseNoteJob("octocat", "hello-world", "v1.0.0", "v2.0.0");

    @BeforeEach
    void setUp() {
        compareService = mock(GitCompareService.class);
        generationService = mock(NoteGenerationService.class);
        noteRepository = mock(ReleaseNoteRepository.class);
        notesService = mock(ReleaseNotesService.class);

        consumer = new ReleaseNoteJobConsumer(compareService, generationService, noteRepository, notesService);
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

        verifyNoInteractions(compareService, generationService);
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

        verifyNoInteractions(compareService, generationService);
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
}