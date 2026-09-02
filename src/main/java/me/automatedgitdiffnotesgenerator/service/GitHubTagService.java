package me.automatedgitdiffnotesgenerator.service;

import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GitHubTagService {

    @Value("${github.api.token}")
    private String githubToken;

    public String getPreviousTag(String repoFullName) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
        GHRepository repository = github.getRepository(repoFullName);

        PagedIterable<GHTag> tagsIterable = repository.listTags();
        List<GHTag> tags = tagsIterable.toList();

        if (tags.size() < 2) {
            return null;
        }

        return tags.get(1).getName();
    }
}
