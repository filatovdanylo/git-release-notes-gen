package me.automatedgitdiffnotesgenerator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.producer.ReleaseNoteJobProducer;
import me.automatedgitdiffnotesgenerator.service.ReleaseNotesService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/release-notes")
public class ReleaseNotesController {
    private final ReleaseNoteJobProducer jobProducer;
    private final ReleaseNotesService notesService;
    public ReleaseNotesController(
            ReleaseNoteJobProducer jobProducer,
            ReleaseNotesService notesService
    ) {
        this.jobProducer = jobProducer;
        this.notesService = notesService;
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/generate-async")
    @Operation(
            summary = "Generate release notes manually in asynchronous mode",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("#request.repoOwner() == authentication.name or hasRole('ADMIN')")
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
    @Operation(
            summary = "Get all release notes for given {owner} and {repo}",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<?> getNotesByRepo(@PathVariable String owner, @PathVariable String repo) {
        var notes = notesService.getNotesByRepoName(owner, repo);

        return ResponseEntity.ok(notes);
    }

    @GetMapping("/{owner}/{repo}/{from}/{to}")
    @Operation(
            summary = "Get release note for given {owner}/{repo} repository with {from} and {to} tags",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<?> getNoteByRepoAndTags(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String from,
            @PathVariable String to
    ) {
        var note = notesService.getNoteByRepoAndTags(owner, repo, from, to);

        if (note.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(note.get());
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{owner}/{repo}/{from}/{to}/regenerate")
    @Operation(
            summary = "Force note regeneration. Only available if status is PROCESSING or FAILED",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
    public ResponseEntity<?> forceRegenerateNoteAsync(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String from,
            @PathVariable String to
    ) {
        // TODO

        return ResponseEntity.accepted().build();
    }

}
