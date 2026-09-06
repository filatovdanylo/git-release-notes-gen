package me.automatedgitdiffnotesgenerator.consumer;

import lombok.extern.slf4j.Slf4j;
import me.automatedgitdiffnotesgenerator.config.RabbitConfig;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import me.automatedgitdiffnotesgenerator.service.ReleaseNotesService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
public class ReleaseNoteJobConsumer {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    private final ReleaseNoteRepository noteRepository;
    private final ReleaseNotesService notesService;

    public ReleaseNoteJobConsumer(
            GitCompareService compareService,
            NoteGenerationService generationService,
            ReleaseNoteRepository noteRepository,
            ReleaseNotesService notesService
    ) {
        this.compareService = compareService;
        this.generationService = generationService;
        this.noteRepository = noteRepository;
        this.notesService = notesService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_NAME)
    public void handleJob(ReleaseNoteJob job) {
        log.info("Received a ReleaseNoteJob from the queue: {}", job);

        if (!notesService.tryClaim(job)) {
            log.info("Job already claimed: {}/{} {}...{}",
                    job.repoOwner(),
                    job.repoName(),
                    job.fromTag(),
                    job.toTag());
            return;
        }

        ReleaseNote note = noteRepository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(), job.repoName(), job.fromTag(), job.toTag()
        ).orElse(null);

        if (note == null) {
            log.error(
                    "CRITICAL: job was successfully claimed, but ReleaseNote could not be loaded. Job: {}/{} {}...{}",
                    job.repoOwner(),
                    job.repoName(),
                    job.fromTag(),
                    job.toTag()
            );
            return;
        }

        try {
            GenerateNoteRequest noteRequest = new GenerateNoteRequest(
                    job.repoOwner(),
                    job.repoName(),
                    job.fromTag(),
                    job.toTag()
            );
            String context = compareService.getCommitDiff(noteRequest);
            String generatedNotes = generationService.generate(context);

            note.setContent(generatedNotes);
            note.setStatus(ReleaseNote.Status.COMPLETED);
        } catch (Exception e) {
            log.error("Failed to generate release notes for {}/{} {}...{}",
                    job.repoOwner(), job.repoName(), job.fromTag(), job.toTag(), e);
            note.setStatus(ReleaseNote.Status.FAILED);
            note.setErrorMessage("Generation failed. See server logs for details");
        } finally {
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        }
    }

}
