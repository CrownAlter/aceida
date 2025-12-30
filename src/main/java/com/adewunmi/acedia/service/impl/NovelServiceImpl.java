package com.adewunmi.acedia.service.impl;

import com.adewunmi.acedia.model.entity.Chapter;
import com.adewunmi.acedia.model.entity.Novel;
import com.adewunmi.acedia.repository.ChapterRepository;
import com.adewunmi.acedia.repository.NovelRepository;
import com.adewunmi.acedia.service.NovelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelServiceImpl implements NovelService {

    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;

    @Override
    @Transactional
    public UUID create(Novel novel) {
        novel.setDateLastModified(LocalDateTime.now());
        novel.setTotalChapters(novel.getChapters().size());
        Novel saved = novelRepository.save(novel);
        return saved.getId();
    }

    @Override
    @Transactional
    public void update(Novel novel) {
        novel.setDateLastModified(LocalDateTime.now());
        novelRepository.save(novel);
    }

    @Override
    @Transactional
    public void updateAndAddChapters(java.util.UUID novelId, List<Chapter> newChapters, com.adewunmi.acedia.model.dto.NovelDataBuffer buffer) {
        // Fetch the managed entity to avoid orphan deletion issues
        Novel managedNovel = novelRepository.findById(novelId)
                .orElseThrow(() -> new RuntimeException("Novel not found: " + novelId));
        
        // Set bidirectional relationship for new chapters
        for (Chapter chapter : newChapters) {
            chapter.setNovel(managedNovel);
        }
        
        // Add new chapters to the managed novel's collection (important for orphanRemoval)
        // This maintains the collection reference and prevents orphan deletion error
        managedNovel.getChapters().addAll(newChapters);
        
        // Update novel metadata from buffer
        managedNovel.setDateLastModified(LocalDateTime.now());
        if (buffer.getLastTableOfContentsPageUrl() != null) {
            managedNovel.setLastTableOfContentsUrl(buffer.getLastTableOfContentsPageUrl());
        }
        if (buffer.getNovelStatus() != null) {
            managedNovel.setStatus(buffer.getNovelStatus());
        }
        managedNovel.setLastChapter(buffer.isNovelCompleted());
        
        // Update chapter counts and current chapter
        managedNovel.setTotalChapters(managedNovel.getChapters().size());
        
        if (!newChapters.isEmpty()) {
            Chapter lastChapter = newChapters.get(newChapters.size() - 1);
            managedNovel.setCurrentChapter(lastChapter.getTitle());
            managedNovel.setCurrentChapterUrl(lastChapter.getUrl());
        }
        
        // Save the novel (cascade will save new chapters)
        novelRepository.save(managedNovel);
        log.info("Added {} new chapters to novel '{}'", newChapters.size(), managedNovel.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Novel> getAll() {
        return novelRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Novel> getByUrl(String url) {
        Optional<Novel> novelOpt = novelRepository.findByUrl(url);
        if (novelOpt.isPresent()) {
            Novel novel = novelOpt.get();
            // Force initialization of the lazy-loaded chapters collection
            // Don't replace the collection - just access it to trigger loading
            novel.getChapters().size(); // This forces Hibernate to load chapters
        }
        return novelOpt;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Novel> getById(UUID id) {
        Optional<Novel> novelOpt = novelRepository.findById(id);
        if (novelOpt.isPresent()) {
            Novel novel = novelOpt.get();
            // Force initialization of the lazy-loaded chapters collection
            // Don't replace the collection - just access it to trigger loading
            novel.getChapters().size(); // This forces Hibernate to load chapters
        }
        return novelOpt;
    }

    @Override
    public boolean existsByUrl(String url) {
        return novelRepository.existsByUrl(url);
    }

    @Override
    public boolean existsById(UUID id) {
        return novelRepository.existsById(id);
    }

    @Override
    @Transactional
    public void deleteAll() {
        chapterRepository.deleteAll();
        novelRepository.deleteAll();
    }

    @Override
    @Transactional
    public void deleteById(UUID id) {
        chapterRepository.deleteByNovel_Id(id);
        novelRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Novel> searchByTitle(String keyword) {
        return novelRepository.findByTitleContainingIgnoreCase(keyword);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Novel> findAll(Pageable pageable) {
        return novelRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Novel> findById(UUID id) {
        return getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Novel> searchByTitle(String keyword, Pageable pageable) {
        return novelRepository.findByTitleContainingIgnoreCase(keyword, pageable);
    }
}
