package me.automatedgitdiffnotesgenerator.controller;

import me.automatedgitdiffnotesgenerator.dto.GenerateNoteRequest;
import me.automatedgitdiffnotesgenerator.service.GitCompareService;
import me.automatedgitdiffnotesgenerator.service.NoteGenerationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/release-notes")
public class ReleaseNotesController {
    private final GitCompareService compareService;
    private final NoteGenerationService generationService;
    public ReleaseNotesController(GitCompareService compareService, NoteGenerationService generationService) {
        this.compareService = compareService;
        this.generationService = generationService;
    }

    @PostMapping("/generate")
    public String generateNotes(@RequestBody GenerateNoteRequest request) throws Exception {
        var context = compareService.getCommitDiff(request);

        return generationService.generate(context);
    }
}
