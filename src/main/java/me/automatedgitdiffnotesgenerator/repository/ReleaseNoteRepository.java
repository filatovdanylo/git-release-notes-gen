package me.automatedgitdiffnotesgenerator.repository;

import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, Long> {
    List<ReleaseNote> findByRepoOwnerAndRepoName(String repoOwner, String repoName);
    Optional<ReleaseNote> findByRepoOwnerAndRepoNameAndFromTagAndToTag(
            String repoOwner, String repoName, String fromTag, String toTag);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
    INSERT INTO release_notes (
        repo_owner,
        repo_name,
        from_tag,
        to_tag,
        status,
        created_at,
        updated_at
    )
    VALUES (
        :owner,
        :repo,
        :fromTag,
        :toTag,
        'PROCESSING',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (repo_owner, repo_name, from_tag, to_tag)
    DO UPDATE SET
            status        = 'PROCESSING',
            content       = NULL,
            error_message = NULL,
            updated_at    = CURRENT_TIMESTAMP
        WHERE release_notes.status = 'FAILED'
           OR (
                release_notes.status = 'PROCESSING'
            AND release_notes.updated_at < :staleBefore
           )
    """, nativeQuery = true)
    int tryClaim(
            String owner,
            String repo,
            String fromTag,
            String toTag,
            OffsetDateTime staleBefore
    );
}
