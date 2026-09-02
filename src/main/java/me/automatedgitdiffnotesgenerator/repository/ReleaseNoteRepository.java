package me.automatedgitdiffnotesgenerator.repository;

import me.automatedgitdiffnotesgenerator.entity.ReleaseNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReleaseNoteRepository extends JpaRepository<ReleaseNote, Long> {
}
