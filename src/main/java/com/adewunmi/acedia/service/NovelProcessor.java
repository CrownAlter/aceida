package com.adewunmi.acedia.service;

import com.adewunmi.acedia.config.ScraperProperties;
import com.adewunmi.acedia.config.SiteConfiguration;
import com.adewunmi.acedia.model.dto.ChapterDataBuffer;
import com.adewunmi.acedia.model.dto.NovelDataBuffer;
import com.adewunmi.acedia.model.entity.Chapter;
import com.adewunmi.acedia.model.entity.Configuration;
import com.adewunmi.acedia.model.entity.Novel;
import com.adewunmi.acedia.model.entity.NovelFileType;
import com.adewunmi.acedia.repository.ConfigurationRepository;
import com.adewunmi.acedia.scraper.ScraperStrategy;
import com.adewunmi.acedia.scraper.factory.NovelScraperFactory;
import com.adewunmi.acedia.util.CommonHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NovelProcessor {

    private final NovelService novelService;
    private final ChapterService chapterService;
    private final NovelScraperFactory scraperFactory;
    private final ScraperProperties scraperProperties;
    private final ConfigurationRepository configurationRepository;

    public void processNovel(URI tableOfContentsUri) throws Exception {
        processNovel(tableOfContentsUri, null, null, null, null);
    }

    public void processNovel(URI tableOfContentsUri, Integer chapterLimit) throws Exception {
        processNovel(tableOfContentsUri, null, chapterLimit, null, null);
    }

    public void processNovel(URI tableOfContentsUri, Integer chapterLimit, Integer chapterStart, Integer chapterEnd) throws Exception {
        processNovel(tableOfContentsUri, null, chapterLimit, chapterStart, chapterEnd);
    }

    public void processNovel(URI tableOfContentsUri, Integer chapterNumber, Integer chapterLimit, Integer chapterStart, Integer chapterEnd) throws Exception {
        // Validate inputs
        if (tableOfContentsUri == null) {
            throw new IllegalArgumentException("Table of contents URI cannot be null");
        }
        
        if (chapterNumber != null && chapterNumber <= 0) {
            throw new IllegalArgumentException("Chapter number must be a positive number, got: " + chapterNumber);
        }
        
        if (chapterLimit != null && chapterLimit <= 0) {
            throw new IllegalArgumentException("Chapter limit must be a positive number, got: " + chapterLimit);
        }
        
        if (chapterStart != null && chapterStart <= 0) {
            throw new IllegalArgumentException("Chapter start must be a positive number, got: " + chapterStart);
        }
        
        if (chapterEnd != null && chapterEnd <= 0) {
            throw new IllegalArgumentException("Chapter end must be a positive number, got: " + chapterEnd);
        }
        
        if (chapterStart != null && chapterEnd != null && chapterStart > chapterEnd) {
            throw new IllegalArgumentException("Chapter start (" + chapterStart + ") cannot be greater than chapter end (" + chapterEnd + ")");
        }
        
        SiteConfiguration siteConfig = getSiteConfiguration(tableOfContentsUri);
        if (siteConfig == null) {
            throw new IllegalArgumentException("No configuration found for site: " + tableOfContentsUri.getHost() + 
                ". Supported sites: " + getSupportedSites());
        }

        Optional<Novel> existingNovel = novelService.getByUrl(tableOfContentsUri.toString());

        ScraperStrategy strategy = scraperFactory.createScraper(tableOfContentsUri, siteConfig);
        Configuration config = configurationRepository.findById(1).orElseGet(() -> {
            Configuration defaultConfig = new Configuration();
            defaultConfig.setConcurrencyLimit(2); // Default concurrency
            return defaultConfig;
        });

        // Ensure valid concurrency limit
        int concurrencyLimit = config.getConcurrencyLimit();
        if (concurrencyLimit <= 0) {
            log.warn("Invalid concurrency limit in config ({}), using default of 2", concurrencyLimit);
            concurrencyLimit = 2;
        }

        strategy.setVariables(siteConfig, tableOfContentsUri, concurrencyLimit);

        if (existingNovel.isEmpty()) {
            log.info("Novel not in database, adding new novel");
            addNewNovel(tableOfContentsUri, strategy, config, chapterNumber, chapterLimit, chapterStart, chapterEnd);
        } else {
            log.info("Novel found in database, updating");
            updateExistingNovel(existingNovel.get(), strategy, config, chapterNumber, chapterLimit, chapterStart, chapterEnd);
        }
    }
    
    private String getSupportedSites() {
        return scraperProperties.getSiteConfigurations().stream()
                .map(SiteConfiguration::getUrlPattern)
                .collect(Collectors.joining(", "));
    }

    private void addNewNovel(URI tableOfContentsUri, ScraperStrategy strategy, Configuration config, Integer chapterNumber, Integer chapterLimit, Integer chapterStart, Integer chapterEnd) throws Exception {
        try (NovelDataBuffer novelDataBuffer = strategy.scrapeNovelData()) {
            // Pass chapter limit to strategy for pagination optimization
            novelDataBuffer.setChapterLimit(chapterLimit);

            // Validate scraped data
            if (novelDataBuffer.getTitle() == null || novelDataBuffer.getTitle().isEmpty()) {
                throw new IllegalStateException("Failed to scrape novel title from: " + tableOfContentsUri);
            }
            
            if (novelDataBuffer.getChapterUrls() == null || novelDataBuffer.getChapterUrls().isEmpty()) {
                throw new IllegalStateException("No chapters found for novel: " + novelDataBuffer.getTitle());
            }

            Novel newNovel = createNovelFromBuffer(novelDataBuffer, tableOfContentsUri);
            log.info("Finished populating novel data for: {}", newNovel.getTitle());

            // Apply chapter filters (single, limit, range, etc.)
            List<String> chapterUrlsToScrape = novelDataBuffer.getChapterUrls();
            int totalAvailableChapters = chapterUrlsToScrape.size();
            
            // Priority: chapterNumber > chapterLimit > chapterStart/End range > all
            if (chapterNumber != null && chapterNumber > 0) {
                // Download single specific chapter
                if (chapterNumber > totalAvailableChapters) {
                    throw new IllegalArgumentException("Chapter number (" + chapterNumber + ") exceeds total chapters (" + totalAvailableChapters + ")");
                }
                chapterUrlsToScrape = chapterUrlsToScrape.subList(chapterNumber - 1, chapterNumber); // Single chapter
                log.info("Single chapter requested: scraping chapter {} out of {}", chapterNumber, totalAvailableChapters);
            } else if (chapterLimit != null && chapterLimit > 0) {
                // Use chapter limit (chapters 1 to N)
                if (chapterLimit < totalAvailableChapters) {
                    chapterUrlsToScrape = chapterUrlsToScrape.subList(0, chapterLimit);
                    log.info("Chapter limit applied: scraping {} out of {} chapters", chapterLimit, totalAvailableChapters);
                } else {
                    log.info("Chapter limit ({}) is >= total chapters ({}), scraping all", chapterLimit, totalAvailableChapters);
                }
            } else if (chapterStart != null || chapterEnd != null) {
                // Use chapter range
                int startIdx = chapterStart != null ? chapterStart - 1 : 0; // Convert to 0-based index
                int endIdx = chapterEnd != null ? chapterEnd : totalAvailableChapters; // Inclusive
                
                // Validate range bounds
                if (startIdx < 0) startIdx = 0;
                if (endIdx > totalAvailableChapters) endIdx = totalAvailableChapters;
                if (startIdx >= totalAvailableChapters) {
                    throw new IllegalArgumentException("Chapter start (" + chapterStart + ") exceeds total chapters (" + totalAvailableChapters + ")");
                }
                
                chapterUrlsToScrape = chapterUrlsToScrape.subList(startIdx, endIdx);
                log.info("Chapter range applied: scraping chapters {} to {} ({} chapters)", 
                    chapterStart != null ? chapterStart : 1, 
                    chapterEnd != null ? chapterEnd : totalAvailableChapters,
                    chapterUrlsToScrape.size());
            }
            
            log.info("Found {} chapters to scrape", chapterUrlsToScrape.size());
            
            if (chapterUrlsToScrape.isEmpty()) {
                throw new IllegalStateException("No chapter URLs to scrape after applying filters");
            }
            
            List<ChapterDataBuffer> chapterDataBuffers = strategy
                    .getChaptersDataAsync(chapterUrlsToScrape);

            log.info("Creating chapter entities and establishing relationships...");
            List<Chapter> chapters = createChaptersFromBuffers(chapterDataBuffers, null);
            
            // Validate we got chapter data
            if (chapters.isEmpty()) {
                throw new IllegalStateException("Failed to scrape any chapter content");
            }

            // Establish bidirectional relationship
            for (Chapter chapter : chapters) {
                chapter.setNovel(newNovel);
            }
            newNovel.setChapters(chapters);
            
            // Update current chapter to reflect what was actually scraped
            if (!chapters.isEmpty()) {
                Chapter lastScrapedChapter = chapters.get(chapters.size() - 1);
                newNovel.setCurrentChapter(lastScrapedChapter.getTitle());
                newNovel.setCurrentChapterUrl(lastScrapedChapter.getUrl());
                log.info("Set current chapter to: {} ({})", lastScrapedChapter.getTitle(), lastScrapedChapter.getUrl());
            }
            newNovel.setTotalChapters(chapters.size());

            SiteConfiguration siteConfig = getSiteConfiguration(tableOfContentsUri);
            String outputDir = CommonHelper.getOutputDirectoryForTitle(newNovel.getTitle(),
                    config.determineSaveLocation(siteConfig.isHasImagesForChapterContent()));

            log.info("Saving novel with {} chapters to database...", chapters.size());
            newNovel.setId(novelService.create(newNovel));
            log.info("Novel saved successfully with ID: {}", newNovel.getId());

            // TODO: Generate file (EPUB/PDF/CBZ) based on content type
            newNovel.setFileType(NovelFileType.EPUB);
            novelService.update(newNovel);

            log.info("Successfully added novel '{}' with {} chapters", newNovel.getTitle(), chapters.size());
        }
    }

    private void updateExistingNovel(Novel novel, ScraperStrategy strategy, Configuration config, Integer chapterNumber, Integer chapterLimit, Integer chapterStart, Integer chapterEnd) throws Exception {
        try (NovelDataBuffer novelDataBuffer = strategy.scrapeNovelData()) {

            log.info("Checking if novel '{}' is up to date. Current chapter URL in DB: {}", 
                novel.getTitle(), novel.getCurrentChapterUrl());
            log.info("Latest chapter URL from scrape: {}", novelDataBuffer.getCurrentChapterUrl());
            log.info("Chapters in database: {}, Chapters found on site: {}", 
                novel.getChapters().size(), novelDataBuffer.getChapterUrls().size());
            
            // Handle different chapter selection modes
            // Priority: chapterNumber > chapterLimit > chapterStart/End range > auto-update new chapters
            
            if (chapterNumber != null && chapterNumber > 0) {
                // MODE 0: Download single specific chapter
                List<String> allChapterUrls = novelDataBuffer.getChapterUrls();
                int totalAvailableChapters = allChapterUrls.size();
                
                if (chapterNumber > totalAvailableChapters) {
                    throw new IllegalArgumentException("Chapter number (" + chapterNumber + ") exceeds total chapters (" + totalAvailableChapters + ")");
                }
                
                String targetChapterUrl = allChapterUrls.get(chapterNumber - 1); // 0-based index
                log.info("Single chapter requested: chapter {}", chapterNumber);
                
                // Check if we already have this chapter
                List<String> existingChapterUrls = novel.getChapters().stream()
                        .map(Chapter::getUrl)
                        .collect(Collectors.toList());
                
                if (existingChapterUrls.contains(targetChapterUrl)) {
                    log.info("Chapter {} is already downloaded", chapterNumber);
                    return;
                }
                
                log.info("Downloading chapter {}", chapterNumber);
                List<ChapterDataBuffer> newChapterBuffers = strategy.getChaptersDataAsync(List.of(targetChapterUrl));
                List<Chapter> newChapters = createChaptersFromBuffers(newChapterBuffers, novel.getId());

                if (newChapters.isEmpty()) {
                    log.warn("Failed to download chapter {}", chapterNumber);
                    return;
                }

                log.info("Saving chapter {} to database...", chapterNumber);
                novelService.updateAndAddChapters(novel.getId(), newChapters, novelDataBuffer);

                log.info("Successfully added chapter {} to novel '{}'", chapterNumber, novel.getTitle());
                return;
                
            } else if (chapterLimit != null && chapterLimit > 0) {
                // MODE 1: Update to specific chapter limit (chapters 1 to N)
                log.info("Chapter limit specified ({}), updating to chapter {}", chapterLimit, chapterLimit);
                
                // Get all chapters from the site up to the limit
                List<String> targetChapterUrls = novelDataBuffer.getChapterUrls();
                if (chapterLimit < targetChapterUrls.size()) {
                    targetChapterUrls = targetChapterUrls.subList(0, chapterLimit);
                }
                
                // Determine which chapters we need to download (those we don't have yet)
                List<String> existingChapterUrls = novel.getChapters().stream()
                        .map(Chapter::getUrl)
                        .collect(Collectors.toList());
                
                List<String> chaptersToDownload = targetChapterUrls.stream()
                        .filter(url -> !existingChapterUrls.contains(url))
                        .collect(Collectors.toList());
                
                if (chaptersToDownload.isEmpty()) {
                    log.info("All chapters up to chapter {} are already downloaded", chapterLimit);
                    return;
                }
                
                log.info("Need to download {} chapters to reach chapter limit of {}", 
                        chaptersToDownload.size(), chapterLimit);
                List<ChapterDataBuffer> newChapterBuffers = strategy.getChaptersDataAsync(chaptersToDownload);
                List<Chapter> newChapters = createChaptersFromBuffers(newChapterBuffers, novel.getId());

                log.info("Saving {} new chapters to database...", newChapters.size());
                novelService.updateAndAddChapters(novel.getId(), newChapters, novelDataBuffer);

                log.info("Successfully updated novel '{}' with {} new chapters (now at chapter {})", 
                        novel.getTitle(), newChapters.size(), chapterLimit);
                return;
                
            } else if (chapterStart != null || chapterEnd != null) {
                // MODE 2: Update specific chapter range
                List<String> allChapterUrls = novelDataBuffer.getChapterUrls();
                int totalAvailableChapters = allChapterUrls.size();
                
                int startIdx = chapterStart != null ? chapterStart - 1 : 0; // Convert to 0-based
                int endIdx = chapterEnd != null ? chapterEnd : totalAvailableChapters; // Inclusive
                
                // Validate range bounds
                if (startIdx < 0) startIdx = 0;
                if (endIdx > totalAvailableChapters) endIdx = totalAvailableChapters;
                if (startIdx >= totalAvailableChapters) {
                    throw new IllegalArgumentException("Chapter start (" + chapterStart + ") exceeds total chapters (" + totalAvailableChapters + ")");
                }
                
                List<String> targetChapterUrls = allChapterUrls.subList(startIdx, endIdx);
                log.info("Chapter range specified: {} to {} ({} chapters)", 
                    chapterStart != null ? chapterStart : 1,
                    chapterEnd != null ? chapterEnd : totalAvailableChapters,
                    targetChapterUrls.size());
                
                // Determine which chapters we need to download (those we don't have yet)
                List<String> existingChapterUrls = novel.getChapters().stream()
                        .map(Chapter::getUrl)
                        .collect(Collectors.toList());
                
                List<String> chaptersToDownload = targetChapterUrls.stream()
                        .filter(url -> !existingChapterUrls.contains(url))
                        .collect(Collectors.toList());
                
                if (chaptersToDownload.isEmpty()) {
                    log.info("All chapters in range {} to {} are already downloaded", 
                        chapterStart != null ? chapterStart : 1,
                        chapterEnd != null ? chapterEnd : totalAvailableChapters);
                    return;
                }
                
                log.info("Need to download {} chapters in the specified range", chaptersToDownload.size());
                List<ChapterDataBuffer> newChapterBuffers = strategy.getChaptersDataAsync(chaptersToDownload);
                List<Chapter> newChapters = createChaptersFromBuffers(newChapterBuffers, novel.getId());

                log.info("Saving {} new chapters to database...", newChapters.size());
                novelService.updateAndAddChapters(novel.getId(), newChapters, novelDataBuffer);

                log.info("Successfully updated novel '{}' with {} new chapters from range {} to {}", 
                        novel.getTitle(), newChapters.size(),
                        chapterStart != null ? chapterStart : 1,
                        chapterEnd != null ? chapterEnd : totalAvailableChapters);
                return;
            }
            
            // No chapter limit specified - check if already up to date
            if (isNovelUpToDate(novel, novelDataBuffer)) {
                log.info("Novel {} is up to date", novel.getTitle());
                return;
            }

            List<Chapter> sortedChapters = novel.getChapters().stream()
                    .sorted((c1, c2) -> c1.getDateCreated().compareTo(c2.getDateCreated()))
                    .collect(Collectors.toList());

            List<String> newChapterUrls = determineNewChapters(novel.getCurrentChapterUrl(),
                    sortedChapters, novelDataBuffer.getChapterUrls());

            if (!newChapterUrls.isEmpty()) {
                log.info("Found {} new chapters to scrape", newChapterUrls.size());
                List<ChapterDataBuffer> newChapterBuffers = strategy.getChaptersDataAsync(newChapterUrls);
                List<Chapter> newChapters = createChaptersFromBuffers(newChapterBuffers, novel.getId());

                log.info("Saving {} new chapters to database...", newChapters.size());
                novelService.updateAndAddChapters(novel.getId(), newChapters, novelDataBuffer);

                log.info("Successfully updated novel '{}' with {} new chapters", novel.getTitle(), newChapters.size());
            }
        }
    }

    private Novel createNovelFromBuffer(NovelDataBuffer buffer, URI tableOfContentsUri) {
        Novel novel = new Novel();
        novel.setTitle(buffer.getTitle() != null ? buffer.getTitle() : "");
        novel.setAuthor(buffer.getAuthor());
        novel.setUrl(tableOfContentsUri.toString());
        novel.setGenre(String.join(", ", buffer.getGenres()));
        novel.setDescription(String.join(" ", buffer.getDescription()));
        novel.setDateCreated(LocalDateTime.now());
        novel.setDateLastModified(LocalDateTime.now());
        novel.setStatus(buffer.getNovelStatus());
        novel.setLastTableOfContentsUrl(buffer.getLastTableOfContentsPageUrl());
        novel.setLastChapter(buffer.isNovelCompleted());
        novel.setCurrentChapter(buffer.getMostRecentChapterTitle() != null ? buffer.getMostRecentChapterTitle() : "");
        novel.setSiteName(tableOfContentsUri.getHost() != null ? tableOfContentsUri.getHost() : "");
        novel.setFirstChapter(buffer.getFirstChapter() != null ? buffer.getFirstChapter() : "");
        novel.setCurrentChapterUrl(buffer.getCurrentChapterUrl() != null ? buffer.getCurrentChapterUrl() : "");
        return novel;
    }

    private List<Chapter> createChaptersFromBuffers(List<ChapterDataBuffer> buffers, java.util.UUID novelId) {
        List<Chapter> chapters = new ArrayList<>();
        int skippedCount = 0;

        log.info("Converting {} chapter buffers to entities...", buffers.size());
        int bufferIndex = 0;
        for (ChapterDataBuffer buffer : buffers) {
            bufferIndex++;
            
            // Log what we're receiving from the buffer
            String bufferTitle = buffer.getTitle();
            String bufferContent = buffer.getContent();
            
            log.info("Buffer #{}: title='{}', content length={}", 
                bufferIndex, 
                bufferTitle != null ? bufferTitle : "NULL", 
                bufferContent != null ? bufferContent.length() : 0);
            
            // Validate chapter content - skip chapters with no content or title
            boolean hasTitle = bufferTitle != null && !bufferTitle.trim().isEmpty();
            boolean hasContent = bufferContent != null && bufferContent.trim().length() > 0;
            
            if (!hasTitle && !hasContent) {
                log.warn("Skipping chapter #{} - no title and no content. URL: {}", 
                    bufferIndex, buffer.getUrl());
                skippedCount++;
                continue;
            }
            
            if (!hasContent) {
                log.warn("Skipping chapter #{} - no content found. Title: '{}', URL: {}", 
                    bufferIndex, bufferTitle, buffer.getUrl());
                skippedCount++;
                continue;
            }
            
            // Only create chapter entity if we have valid content
            Chapter chapter = new Chapter();
            chapter.setUrl(buffer.getUrl() != null ? buffer.getUrl() : "");
            chapter.setContent(bufferContent.trim());
            chapter.setTitle(bufferTitle != null ? bufferTitle.trim() : "");
            chapter.setNumber(buffer.getSequenceNumber());
            chapter.setDateCreated(LocalDateTime.now());
            chapter.setDateLastModified(buffer.getDateLastModified());
            
            // Verify what was set
            log.info("Chapter entity #{}: title='{}', content length={}", 
                bufferIndex, 
                chapter.getTitle(), 
                chapter.getContent() != null ? chapter.getContent().length() : 0);
            
            chapters.add(chapter);
        }

        if (skippedCount > 0) {
            log.warn("Skipped {} chapters due to missing content", skippedCount);
        }
        
        log.info("Successfully converted {} valid chapters from {} buffers", chapters.size(), buffers.size());
        return chapters;
    }

    private boolean isNovelUpToDate(Novel novel, NovelDataBuffer buffer) {
        // Check if the current chapter URL matches (most reliable)
        if (novel.getCurrentChapterUrl() != null && buffer.getCurrentChapterUrl() != null &&
                novel.getCurrentChapterUrl().equals(buffer.getCurrentChapterUrl())) {
            log.info("Novel is up to date - current chapter URL matches");
            return true;
        }
        
        // Check if the current chapter title matches (fallback)
        if (novel.getCurrentChapter() != null && buffer.getMostRecentChapterTitle() != null &&
                novel.getCurrentChapter().equals(buffer.getMostRecentChapterTitle())) {
            log.info("Novel is up to date - current chapter title matches");
            return true;
        }
        
        // Check if chapter count matches
        if (novel.getChapters() != null && buffer.getChapterUrls() != null) {
            int dbChapterCount = novel.getChapters().size();
            int siteChapterCount = buffer.getChapterUrls().size();
            if (dbChapterCount >= siteChapterCount) {
                log.info("Novel is up to date - chapter count: DB={}, Site={}", dbChapterCount, siteChapterCount);
                return true;
            }
        }
        
        log.info("Novel needs update - new chapters detected");
        return false;
    }

    private List<String> determineNewChapters(String currentChapterUrl,
            List<Chapter> savedChapters,
            List<String> allChapterUrls) {
        
        // Try to find the last saved chapter in the full list
        int lastIndex = -1;
        
        // First try: Match by current chapter URL from novel
        if (currentChapterUrl != null && !currentChapterUrl.isEmpty()) {
            lastIndex = allChapterUrls.indexOf(currentChapterUrl);
            log.debug("Searching for current chapter URL in site list: found at index {}", lastIndex);
        }

        // Second try: Match by last saved chapter URL
        if (lastIndex == -1 && !savedChapters.isEmpty()) {
            String lastSavedChapterUrl = savedChapters.get(savedChapters.size() - 1).getUrl();
            lastIndex = allChapterUrls.indexOf(lastSavedChapterUrl);
            log.debug("Searching for last saved chapter URL in site list: found at index {}", lastIndex);
        }

        // Third try: Match by chapter count (assume chapters are in order)
        if (lastIndex == -1 && !savedChapters.isEmpty()) {
            lastIndex = savedChapters.size() - 1;
            log.debug("Using chapter count as fallback: index {}", lastIndex);
        }

        // Get all chapters after the last index
        if (lastIndex != -1 && lastIndex < allChapterUrls.size() - 1) {
            List<String> newChapters = allChapterUrls.subList(lastIndex + 1, allChapterUrls.size());
            log.info("Found {} new chapters (starting from index {})", newChapters.size(), lastIndex + 1);
            return newChapters;
        }

        log.info("No new chapters found");
        return new ArrayList<>();
    }

    private void updateNovelWithNewChapters(Novel novel, NovelDataBuffer buffer, List<Chapter> newChapters) {
        if (buffer.getLastTableOfContentsPageUrl() != null) {
            novel.setLastTableOfContentsUrl(buffer.getLastTableOfContentsPageUrl());
        }
        if (buffer.getNovelStatus() != null) {
            novel.setStatus(buffer.getNovelStatus());
        }
        novel.setLastChapter(buffer.isNovelCompleted());
        novel.setDateLastModified(LocalDateTime.now());
        novel.setTotalChapters(novel.getChapters().size() + newChapters.size());

        if (!newChapters.isEmpty()) {
            Chapter lastChapter = newChapters.get(newChapters.size() - 1);
            novel.setCurrentChapter(lastChapter.getTitle());
            novel.setCurrentChapterUrl(lastChapter.getUrl());
        }

        if (buffer.getNovelUrl() != null) {
            novel.setUrl(buffer.getNovelUrl());
        }
        if (!buffer.getGenres().isEmpty()) {
            novel.setGenre(String.join(", ", buffer.getGenres()));
        }
    }

    private SiteConfiguration getSiteConfiguration(URI tableOfContentsUri) {
        String host = tableOfContentsUri.getHost();
        return scraperProperties.getSiteConfigurations().stream()
                .filter(config -> host.contains(config.getUrlPattern()))
                .findFirst()
                .orElse(null);
    }
}
