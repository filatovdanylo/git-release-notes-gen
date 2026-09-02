package me.automatedgitdiffnotesgenerator.job;

import java.io.Serializable;

public record ReleaseNoteJob(
        String repoOwner,
        String repoName,
        String fromTag,
        String toTag
) implements Serializable {
}
