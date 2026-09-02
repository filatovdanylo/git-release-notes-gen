package me.automatedgitdiffnotesgenerator.job;

public record ReleaseNoteJob(
        String repoOwner,
        String repoName,
        String fromTag,
        String toTag
) {
}
