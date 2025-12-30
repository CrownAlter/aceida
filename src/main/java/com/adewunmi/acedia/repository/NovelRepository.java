package com.adewunmi.acedia.repository;

import com.adewunmi.acedia.model.entity.Novel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NovelRepository extends JpaRepository<Novel, UUID> {

    Optional<Novel> findByUrl(String url);

    List<Novel> findByTitleContainingIgnoreCase(String title);

    Page<Novel> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    List<Novel> findByLastChapterFalse();

    boolean existsByUrl(String url);
}
