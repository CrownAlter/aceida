package com.adewunmi.acedia.repository;

import com.adewunmi.acedia.model.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChapterRepository extends JpaRepository<Chapter, UUID> {

    List<Chapter> findByNovel_IdOrderByNumberAsc(UUID novelId);

    List<Chapter> findByNovel_Id(UUID novelId);

    @Query("SELECT c FROM Chapter c WHERE c.novel.id = ?1 ORDER BY c.dateCreated DESC")
    Optional<Chapter> findLastSavedChapterByNovelId(UUID novelId);

    void deleteByNovel_Id(UUID novelId);
}
