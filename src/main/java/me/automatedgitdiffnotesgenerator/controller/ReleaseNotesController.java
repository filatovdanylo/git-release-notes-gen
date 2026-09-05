package me.automatedgitdiffnotesgenerator.controller;

import jakarta.validation.Valid;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import me.automatedgitdiffnotesgenerator.producer.ReleaseNoteJobProducer;
import me.automatedgitdiffnotesgenerator.service.ReleaseNotesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/release-notes")
public class ReleaseNotesController {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    private final ReleaseNoteJobProducer jobProducer;
    private final ReleaseNotesService notesService;
    public ReleaseNotesController(
            GitCompareService compareService,
            NoteGenerationService generationService,
            ReleaseNoteJobProducer jobProducer,
            ReleaseNotesService notesService
    ) {
        this.compareService = compareService;
        this.generationService = generationService;
        this.jobProducer = jobProducer;
        this.notesService = notesService;
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/generate")
    public ResponseEntity<?> generateNotes(@RequestBody @Valid GenerateNoteRequest request) {
        var context = compareService.getCommitDiff(request);

        return ResponseEntity.ok(generationService.generate(context));
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/generate-async")
    public ResponseEntity<?> generateNotesSaveAsync(@RequestBody @Valid GenerateNoteRequest request) {
        var releaseJob = new ReleaseNoteJob(
                request.repoOwner(),
                request.repoName(),
                request.fromTag(),
                request.toTag()
        );
        jobProducer.releaseNoteJob(releaseJob);

        return ResponseEntity.accepted().body("Queued for generation");
    }

    @GetMapping("/{owner}/{repo}")
    public ResponseEntity<?> getNotesByRepo(@PathVariable String owner, @PathVariable String repo) {
        var notes = notesService.getNotesByRepoName(owner, repo);

        return ResponseEntity.ok(notes);
    }

}
