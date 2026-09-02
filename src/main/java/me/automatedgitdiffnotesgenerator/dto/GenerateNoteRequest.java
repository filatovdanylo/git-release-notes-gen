package me.automatedgitdiffnotesgenerator.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateNoteRequest(
        @NotBlank String repoOwner,
        @NotBlank String repoName,
        @NotBlank String fromTag,
        @NotBlank String toTag
) {
}
