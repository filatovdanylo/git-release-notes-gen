package me.automatedgitdiffnotesgenerator.repository;

import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, Long> {
    List<ReleaseNote> findByRepoName(String repoName);
    boolean existsByRepoOwnerAndRepoNameAndFromTagAndToTag(
            String repoOwner, String repoName, String fromTag, String toTag);
}
