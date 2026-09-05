package me.automatedgitdiffnotesgenerator.service;

import jakarta.transaction.Transactional;
import me.automatedgitdiffnotesgenerator.dto.NoteResponse;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReleaseNotesService {
    private final ReleaseNoteRepository repository;
    public ReleaseNotesService(ReleaseNoteRepository repository) {
        this.repository = repository;
    }

    public List<NoteResponse> getNotesByRepoName(String owner, String repoName) {
        return repository.findByRepoOwnerAndRepoName(owner, repoName).stream()
                .map(r -> new NoteResponse(
                        r.getRepoOwner(),
                        r.getRepoName(),
                        r.getFromTag(),
                        r.getToTag(),
                        r.getContent()
                )).toList();
    }

    @Transactional
    public boolean tryClaim(ReleaseNoteJob job) {
        return repository.tryClaim(
                job.repoOwner(),
                job.repoName(),
                job.fromTag(),
                job.toTag()
        ) == 1;
    }
}
