package me.automatedgitdiffnotesgenerator.service;

import me.automatedgitdiffnotesgenerator.dto.NoteResponse;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReleaseNotesService {
    private final ReleaseNoteRepository repository;
    public ReleaseNotesService(ReleaseNoteRepository repository) {
        this.repository = repository;
    }

    public List<NoteResponse> getNotesByRepoName(String fullRepoName) {
        return repository.findByRepoName(fullRepoName).stream()
                .map(r -> new NoteResponse(
                        r.getRepoOwner(),
                        r.getRepoName(),
                        r.getFromTag(),
                        r.getToTag(),
                        r.getContent()
                )).toList();
    }
}
