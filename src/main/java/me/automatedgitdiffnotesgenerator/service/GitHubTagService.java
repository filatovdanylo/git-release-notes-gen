package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.exception.TagNotFoundException;
import org.kohsuke.github.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class GitHubTagService {

    @Value("${github.api.token}")
    private String githubToken;

    public String getPreviousTag(String repoFullName, String toTag) throws IOException {
        GitHub github = new GitHubBuilder().withOAuthToken(githubToken).build();
        GHRepository repository = github.getRepository(repoFullName);

        List<GHTag> tags = repository.listTags().toList();

        int index = -1;
        for (int i = 0; i < tags.size(); i++) {
            if (tags.get(i).getName().equals(toTag)) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new TagNotFoundException("Tag '" + toTag + "' not found in repository " + repoFullName);
        }

        if (index + 1 >= tags.size()) {
            return null;
        }

        return tags.get(index + 1).getName();
    }
}
