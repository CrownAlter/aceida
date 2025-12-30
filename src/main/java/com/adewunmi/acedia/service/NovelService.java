package com.adewunmi.acedia.service;

import com.adewunmi.acedia.model.entity.Chapter;
import com.adewunmi.acedia.model.entity.Novel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NovelService {

    UUID create(Novel novel);

    void update(Novel novel);

    void updateAndAddChapters(java.util.UUID novelId, List<Chapter> newChapters, com.adewunmi.acedia.model.dto.NovelDataBuffer buffer);

    List<Novel> getAll();

    Page<Novel> findAll(Pageable pageable);

    Optional<Novel> getByUrl(String url);

    Optional<Novel> getById(UUID id);

    Optional<Novel> findById(UUID id);

    boolean existsByUrl(String url);

    boolean existsById(UUID id);

    void deleteAll();

    void deleteById(UUID id);

    List<Novel> searchByTitle(String keyword);

    Page<Novel> searchByTitle(String keyword, Pageable pageable);
}
