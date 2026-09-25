package me.automatedgitdiffnotesgenerator.service;

import jakarta.transaction.Transactional;
import me.automatedgitdiffnotesgenerator.dto.NoteResponse;
import me.automatedgitdiffnotesgenerator.job.ReleaseNoteJob;
import me.automatedgitdiffnotesgenerator.repository.ReleaseNoteRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ReleaseNotesService {
    private final ReleaseNoteRepository repository;

    @Value("${app.release-notes.claim-stale-after}")
    private Duration staleAfter;

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
                        r.getStatus(),
                        r.getContent(),
                        r.getCreatedAt()
                )).toList();
    }

    public Optional<NoteResponse> getNoteByRepoAndTags(String owner, String repo, String from, String to) {
        var note = repository.findByRepoOwnerAndRepoNameAndFromTagAndToTag(owner, repo, from, to);

        if (note.isEmpty()) {
            return Optional.empty();
        }

        var databaseNote = note.get();

        return Optional.of(new NoteResponse(
                databaseNote.getRepoOwner(),
                databaseNote.getRepoName(),
                databaseNote.getFromTag(),
                databaseNote.getToTag(),
                databaseNote.getStatus(),
                databaseNote.getContent(),
                databaseNote.getCreatedAt()
        ));
    }

    @Transactional
    public boolean tryClaim(ReleaseNoteJob job) {
        OffsetDateTime staleBefore = OffsetDateTime.now().minus(staleAfter);

        return repository.tryClaim(
                job.repoOwner(),
                job.repoName(),
                job.fromTag(),
                job.toTag(),
                staleBefore
        ) == 1;
    }
}
