package me.automatedgitdiffnotesgenerator.dto;

import java.util.List;
import java.util.Set;

public record CommitsAndPRInfo(
        List<String> commits,
        Set<PullRequest> pullRequests
) {
}
