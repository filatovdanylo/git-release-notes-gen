package me.automatedgitdiffnotesgenerator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("dev")
@RestController
@RequestMapping("/api/release-notes/dev")
public class SyncReleaseNotesController {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    public SyncReleaseNotesController(
            GitCompareService compareService,
            NoteGenerationService generationService
    ) {
        this.compareService = compareService;
        this.generationService = generationService;
    }

    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/generate")
    @Operation(
            summary = "Generate release notes manually in synchronous mode",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public ResponseEntity<?> generateNotes(@RequestBody @Valid GenerateNoteRequest request) {
        var context = compareService.getCommitDiff(request);

        return ResponseEntity.ok(generationService.generate(context));
    }
}
