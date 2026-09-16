package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.client.GitHubClientFactory;
import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import org.kohsuke.github.*;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class GitHubTagService {

    private final GitHubClientFactory clientFactory;
    public GitHubTagService(GitHubClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public String getPreviousTag(String repoFullName, String toTag) throws IOException {
        GitHub github = clientFactory.create();
        GHRepository repository = github.getRepository(repoFullName);

        boolean foundCurrent = false;
        for (var release : repository.listReleases()) {
            if (foundCurrent && !release.isDraft()) {
                return release.getTagName();
            }

            if (release.getTagName().equals(toTag)) {
                foundCurrent = true;
            }
        }

        if (!foundCurrent) {
            throw new TagNotFoundException("Tag '" + toTag + "' not found in repository " + repoFullName);
        }

        return null;
    }
}
