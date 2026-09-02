package me.automatedgitdiffnotesgenerator.dto;

public record NoteResponse(
        String repoOwner,
        String repoName,
        String fromTag,
        String toTag,
        String content
) {
}
