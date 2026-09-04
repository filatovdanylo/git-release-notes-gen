package me.automatedgitdiffnotesgenerator.consumer;

import lombok.extern.slf4j.Slf4j;
import me.automatedgitdiffnotesgenerator.config.RabbitConfig;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
public class ReleaseNoteJobConsumer {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    private final ReleaseNoteRepository noteRepository;

    public ReleaseNoteJobConsumer(GitCompareService compareService, NoteGenerationService generationService, ReleaseNoteRepository noteRepository) {
        this.compareService = compareService;
        this.generationService = generationService;
        this.noteRepository = noteRepository;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_NAME)
    public void handleJob(ReleaseNoteJob job) {
        boolean alreadyExists = noteRepository.existsByRepoOwnerAndRepoNameAndFromTagAndToTag(
                job.repoOwner(),
                job.repoName(),
                job.fromTag(),
                job.toTag()
        );

        if (alreadyExists) {
            log.info("Release notes already generated for {}/{} {}...{}, skipping request",
                    job.repoOwner(), job.repoName(), job.fromTag(), job.toTag());
            return;
        }

        ReleaseNote note = new ReleaseNote();
        note.setRepoName(job.repoName());
        note.setRepoOwner(job.repoOwner());
        note.setFromTag(job.fromTag());
        note.setToTag(job.toTag());
        note.setStatus(ReleaseNote.Status.PROCESSING);
        note.setCreatedAt(OffsetDateTime.now());
        note.setUpdatedAt(OffsetDateTime.now());
        noteRepository.save(note);

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
            note.setStatus(ReleaseNote.Status.FAILED);
            note.setErrorMessage(e.getMessage());
        } finally {
            note.setUpdatedAt(OffsetDateTime.now());
            noteRepository.save(note);
        }
    }

}
