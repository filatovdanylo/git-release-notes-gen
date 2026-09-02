package me.automatedgitdiffnotesgenerator.controller;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import me.automatedgitdiffnotesgenerator.service.ReleaseNoteJobProducer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/release-notes")
public class ReleaseNotesController {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    private final ReleaseNoteJobProducer jobProducer;
    public ReleaseNotesController(GitCompareService compareService, NoteGenerationService generationService, ReleaseNoteJobProducer jobProducer) {
        this.compareService = compareService;
        this.generationService = generationService;
        this.jobProducer = jobProducer;
    }

    @PostMapping("/generate")
    public String generateNotes(@RequestBody GenerateNoteRequest request) throws Exception {
        var context = compareService.getCommitDiff(request);

        return generationService.generate(context);
    }

    @PostMapping("/generate-async")
    public ResponseEntity<?> generateNotesSaveAsync(@RequestBody GenerateNoteRequest request) throws Exception {
        var releaseJob = new ReleaseNoteJob(
                request.repoOwner(),
                request.repoName(),
                request.fromTag(),
                request.toTag()
        );
        jobProducer.releaseNoteJob(releaseJob);

        return ResponseEntity.ok("Queued");
    }

}
