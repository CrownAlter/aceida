package com.adewunmi.acedia.api.controller;

import com.adewunmi.acedia.api.dto.*;
import com.adewunmi.acedia.model.entity.Chapter;
import com.adewunmi.acedia.model.entity.Novel;
import com.adewunmi.acedia.service.NovelProcessor;
import com.adewunmi.acedia.service.NovelService;
import com.adewunmi.acedia.service.SelectorTestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/novels")
@RequiredArgsConstructor
@Slf4j
public class NovelController {

    private final NovelService novelService;
    private final NovelProcessor novelProcessor;
    private final SelectorTestService selectorTestService;

    /**
     * Start scraping a novel from URL
     * POST /api/novels/scrape
     * 
     * Examples:
     * - Scrape all chapters: {"url": "https://..."}
     * - Scrape single chapter: {"url": "https://...", "chapterNumber": 15}
     * - Scrape first 5 chapters: {"url": "https://...", "chapterLimit": 5}
     * - Scrape chapters 10-20: {"url": "https://...", "chapterStart": 10, "chapterEnd": 20}
     * - Scrape from chapter 50 onwards: {"url": "https://...", "chapterStart": 50}
     */
    @PostMapping("/scrape")
    public ResponseEntity<ScrapeResponseDto> scrapeNovel(@Valid @RequestBody ScrapeRequestDto request) {
        log.info("Received scrape request for URL: {}", request.getUrl());
        if (request.getChapterNumber() != null) {
            log.info("Single chapter requested: {}", request.getChapterNumber());
        } else if (request.getChapterLimit() != null) {
            log.info("Chapter limit set to: {}", request.getChapterLimit());
        } else if (request.getChapterStart() != null || request.getChapterEnd() != null) {
            log.info("Chapter range: {} to {}", 
                request.getChapterStart() != null ? request.getChapterStart() : 1,
                request.getChapterEnd() != null ? request.getChapterEnd() : "end");
        }

        try {
            // Start the scraping process
            java.net.URI uri = java.net.URI.create(request.getUrl());
            novelProcessor.processNovel(uri, request.getChapterNumber(), request.getChapterLimit(), request.getChapterStart(), request.getChapterEnd());

            // Get the novel that was just created/updated
            Novel novel = novelService.getByUrl(request.getUrl()).orElse(null);

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .novelId(novel != null ? novel.getId() : null)
                    .title(novel != null ? novel.getTitle() : null)
                    .message("Novel scraped successfully")
                    .status("COMPLETED")
                    .totalChapters(novel != null ? novel.getTotalChapters() : null)
                    .downloadedChapters(
                            novel != null && novel.getChapters() != null ? novel.getChapters().size() : null)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error scraping novel: {}", e.getMessage(), e);

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .message("Failed to scrape novel: " + e.getMessage())
                    .status("FAILED")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * List all scraped novels with pagination
     * GET /api/novels?page=0&size=20&sort=dateCreated,desc
     */
    @GetMapping
    public ResponseEntity<Page<NovelResponseDto>> listNovels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateLastModified,desc") String[] sort) {
        log.info("Listing novels - page: {}, size: {}", page, size);

        try {
            // Parse sort parameters
            Sort.Direction direction = sort.length > 1 && sort[1].equalsIgnoreCase("asc")
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;
            String sortBy = sort.length > 0 ? sort[0] : "dateLastModified";

            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            Page<Novel> novels = novelService.findAll(pageable);

            Page<NovelResponseDto> response = novels.map(this::convertToDto);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error listing novels: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Search novels by title
     * GET /api/novels/search?query=overlord
     */
    @GetMapping("/search")
    public ResponseEntity<List<NovelResponseDto>> searchNovels(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Searching novels with query: {}", query);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Novel> novels = novelService.searchByTitle(query, pageable);

            List<NovelResponseDto> response = novels.getContent().stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error searching novels: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get novel details by ID including chapters
     * GET /api/novels/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<NovelDetailResponseDto> getNovelDetails(@PathVariable UUID id) {
        log.info("Getting novel details for ID: {}", id);

        try {
            Novel novel = novelService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Novel not found with ID: " + id));

            List<ChapterResponseDto> chapters = novel.getChapters().stream()
                    .map(this::convertChapterToDto)
                    .sorted((a, b) -> Float.compare(a.getNumber(), b.getNumber()))
                    .collect(Collectors.toList());

            NovelDetailResponseDto response = NovelDetailResponseDto.builder()
                    .novel(convertToDto(novel))
                    .chapters(chapters)
                    .build();

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Novel not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting novel details: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing novel (fetch new chapters)
     * PUT /api/novels/{id}/update?chapterLimit=5
     */
    @PutMapping("/{id}/update")
    public ResponseEntity<ScrapeResponseDto> updateNovel(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer chapterLimit) {
        log.info("Updating novel with ID: {}", id);
        if (chapterLimit != null) {
            log.info("Chapter limit set to: {}", chapterLimit);
        }

        try {
            Novel novel = novelService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Novel not found with ID: " + id));

            // Start update process with optional chapter limit
            java.net.URI uri = java.net.URI.create(novel.getUrl());
            novelProcessor.processNovel(uri, chapterLimit);

            // Reload the novel to get updated data
            novel = novelService.findById(id)
                    .orElseThrow(() -> new RuntimeException("Novel not found with ID: " + id));

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .novelId(id)
                    .title(novel.getTitle())
                    .message("Novel updated successfully")
                    .status("COMPLETED")
                    .totalChapters(novel.getTotalChapters())
                    .downloadedChapters(novel.getChapters() != null ? novel.getChapters().size() : 0)
                    .build();

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Novel not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating novel: {}", e.getMessage(), e);

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .message("Failed to update novel: " + e.getMessage())
                    .status("FAILED")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Update an existing novel by URL (no need to know the ID!)
     * PUT /api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterLimit=5
     * 
     * Can also use:
     * - Single chapter: PUT /api/novels/update?url=...&chapterNumber=15
     * - Chapter ranges: PUT /api/novels/update?url=...&chapterStart=10&chapterEnd=20
     */
    @PutMapping("/update")
    public ResponseEntity<ScrapeResponseDto> updateNovelByUrl(
            @RequestParam String url,
            @RequestParam(required = false) Integer chapterNumber,
            @RequestParam(required = false) Integer chapterLimit,
            @RequestParam(required = false) Integer chapterStart,
            @RequestParam(required = false) Integer chapterEnd) {
        log.info("Updating novel with URL: {}", url);
        if (chapterNumber != null) {
            log.info("Single chapter requested: {}", chapterNumber);
        } else if (chapterLimit != null) {
            log.info("Chapter limit set to: {}", chapterLimit);
        } else if (chapterStart != null || chapterEnd != null) {
            log.info("Chapter range: {} to {}", 
                chapterStart != null ? chapterStart : 1,
                chapterEnd != null ? chapterEnd : "end");
        }

        try {
            Novel novel = novelService.getByUrl(url)
                    .orElseThrow(() -> new RuntimeException("Novel not found with URL: " + url));

            // Start update process with optional chapter selection
            java.net.URI uri = java.net.URI.create(novel.getUrl());
            novelProcessor.processNovel(uri, chapterNumber, chapterLimit, chapterStart, chapterEnd);

            // Reload the novel to get updated data
            novel = novelService.getByUrl(url)
                    .orElseThrow(() -> new RuntimeException("Novel not found with URL: " + url));

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .novelId(novel.getId())
                    .title(novel.getTitle())
                    .message("Novel updated successfully")
                    .status("COMPLETED")
                    .totalChapters(novel.getTotalChapters())
                    .downloadedChapters(novel.getChapters() != null ? novel.getChapters().size() : 0)
                    .build();

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            log.error("Novel not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating novel: {}", e.getMessage(), e);

            ScrapeResponseDto response = ScrapeResponseDto.builder()
                    .message("Failed to update novel: " + e.getMessage())
                    .status("FAILED")
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Delete a novel by ID
     * DELETE /api/novels/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNovel(@PathVariable UUID id) {
        log.info("Deleting novel with ID: {}", id);

        try {
            novelService.deleteById(id);
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            log.error("Error deleting novel: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Test CSS selectors against a URL to discover the right selectors for new
     * sites
     * POST /api/novels/test-selectors
     * 
     * Example request body:
     * {
     * "url": "https://novelfull.com/example-novel",
     * "useSelenium": false,
     * "selectors": [
     * {
     * "name": "novelTitle",
     * "selector": "div.col-info-desc h3.title",
     * "selectAll": false
     * },
     * {
     * "name": "chapterLinks",
     * "selector": "ul.list-chapter a",
     * "attribute": "href",
     * "selectAll": true
     * }
     * ]
     * }
     */
    @PostMapping("/test-selectors")
    public ResponseEntity<SelectorTestResponseDto> testSelectors(
            @Valid @RequestBody SelectorTestRequestDto request) {
        log.info("Testing selectors for URL: {}", request.getUrl());

        try {
            SelectorTestResponseDto response = selectorTestService.testSelectors(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error testing selectors: {}", e.getMessage(), e);

            SelectorTestResponseDto errorResponse = SelectorTestResponseDto.builder()
                    .url(request.getUrl())
                    .success(false)
                    .errorMessage("Failed to test selectors: " + e.getMessage())
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Helper methods for DTO conversion

    private NovelResponseDto convertToDto(Novel novel) {
        return NovelResponseDto.builder()
                .id(novel.getId())
                .title(novel.getTitle())
                .author(novel.getAuthor())
                .siteName(novel.getSiteName())
                .url(novel.getUrl())
                .genre(novel.getGenre())
                .description(novel.getDescription())
                .status(novel.getStatus())
                .totalChapters(novel.getTotalChapters())
                .downloadedChapters(novel.getChapters() != null ? novel.getChapters().size() : 0)
                .dateCreated(novel.getDateCreated())
                .dateLastModified(novel.getDateLastModified())
                .saveLocation(novel.getSaveLocation())
                .fileType(novel.getFileType())
                .lastChapter(novel.isLastChapter())
                .currentChapter(novel.getCurrentChapter())
                .build();
    }

    private ChapterResponseDto convertChapterToDto(Chapter chapter) {
        return ChapterResponseDto.builder()
                .id(chapter.getId())
                .title(chapter.getTitle())
                .url(chapter.getUrl())
                .number(chapter.getNumber())
                .dateCreated(chapter.getDateCreated())
                .hasContent(chapter.getContent() != null && !chapter.getContent().isEmpty())
                .pageCount(chapter.getPages() != null ? chapter.getPages().size() : 0)
                .build();
    }
}
