package me.automatedgitdiffnotesgenerator.dto;

import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;

import java.time.OffsetDateTime;

public record NoteResponse(
        String repoOwner,
        String repoName,
        String fromTag,
        String toTag,
        ReleaseNote.Status status,
        String content,
        OffsetDateTime createdAt
) {
}
