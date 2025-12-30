package com.adewunmi.acedia.scraper.strategy.impl;

import org.springframework.stereotype.Component;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import com.adewunmi.acedia.model.dto.NovelDataBuffer;
import com.adewunmi.acedia.scraper.ScraperStrategy;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.List;

@Slf4j
@Component("novelBinStrategy")
public class NovelBinStrategy extends ScraperStrategy {

    @Override
    public NovelDataBuffer scrapeNovelData() throws Exception {
        // For AWS environments, use Selenium from the start to avoid 403 errors
        // AWS IPs are often blocked by anti-bot systems
        Document document;
        if (siteConfig.isSeleniumSite()) {
            log.info("Using Selenium to load novel page (prevents 403 on AWS)");
            document = loadHtmlWithSelenium(siteTableOfContents);
        } else {
            document = loadHtml(siteTableOfContents);
        }
        
        NovelDataBuffer novelData = fetchNovelDataFromTableOfContents(document);
        novelData.setNovelUrl(siteTableOfContents.toString());

        // Get novel slug from URL (e.g., "shadow-slave" from "https://novelbin.me/novel-book/shadow-slave")
        String novelSlug = extractNovelSlug(siteTableOfContents);
        
        // Try to extract numeric novelId from the table-of-contents page for the AJAX endpoint
        String novelId = extractNumericNovelId(document);
        if (novelId == null || novelId.isBlank()) {
            log.warn("Could not find numeric novelId on page; falling back to slug '{}'. AJAX may return empty.", novelSlug);
            novelId = novelSlug; // fallback (may not work on site)
        } else {
            log.info("Extracted numeric novelId: {}", novelId);
        }

        // Load chapter list from AJAX endpoint which contains ALL chapters
        URI ajaxChapterListUri = URI.create("https://novelbin.me/ajax/chapter-archive?novelId=" + novelId);
        log.info("Loading complete chapter list from AJAX endpoint: {}", ajaxChapterListUri);
        
        // Use Selenium for AJAX endpoint too to avoid 403
        Document chapterListDocument;
        if (siteConfig.isSeleniumSite()) {
            log.info("Using Selenium to load AJAX chapter list (prevents 403 on AWS)");
            chapterListDocument = loadHtmlWithSelenium(ajaxChapterListUri);
        } else {
            chapterListDocument = loadHtml(ajaxChapterListUri);
        }
        
        // Extract chapter URLs from the AJAX response
        List<String> chapterUrls = getChapterUrlsInRange(chapterListDocument, baseUri, null, null);
        
        // If AJAX response yielded nothing, fall back to parsing the main page
        if (chapterUrls.isEmpty()) {
            String preview = chapterListDocument.title() + " | len=" + chapterListDocument.outerHtml().length();
            log.warn("AJAX chapter archive returned 0 links. Response title/len: {}", preview);
            log.info("Falling back to parsing chapter links directly from the main page");
            chapterUrls = getChapterUrlsInRange(document, baseUri, null, null);
        }

        novelData.setChapterUrls(chapterUrls);
        log.info("Found {} chapters (AJAX+fallback)", chapterUrls.size());
        
        // Set the first and last chapter URLs for tracking
        if (!chapterUrls.isEmpty()) {
            novelData.setFirstChapter(chapterUrls.get(0));
            novelData.setCurrentChapterUrl(chapterUrls.get(chapterUrls.size() - 1));
            log.info("First chapter: {}", novelData.getFirstChapter());
            log.info("Last chapter: {}", novelData.getCurrentChapterUrl());
        }

        return novelData;
    }
    
    /**
     * Extracts the novel slug from the table of contents URL
     * e.g., "https://novelbin.me/novel-book/shadow-slave" -> "shadow-slave"
     */
    private String extractNovelSlug(URI uri) {
        String path = uri.getPath();
        String[] segments = path.split("/");
        // Return the last segment (novel slug)
        return segments[segments.length - 1];
    }

    private String extractNumericNovelId(Document document) {
        try {
            // Common patterns: data-novel-id, data-id, input[name=novelId], meta[name=novel-id]
            Element el = document.selectFirst("[data-novel-id], [data-id], input[name=novelId], meta[name=novel-id]");
            if (el != null) {
                String val = el.hasAttr("data-novel-id") ? el.attr("data-novel-id") :
                             el.hasAttr("data-id") ? el.attr("data-id") :
                             el.tagName().equalsIgnoreCase("input") ? el.attr("value") :
                             el.tagName().equalsIgnoreCase("meta") ? el.attr("content") : null;
                if (val != null && val.matches("\\d+")) {
                    return val;
                }
            }
            // Try to parse from inline scripts
            for (Element script : document.select("script")) {
                String data = script.data();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("novelId\"?\s*[:=]\s*\"?(\\d+)\"?").matcher(data);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting numeric novelId: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public NovelDataBuffer fetchNovelDataFromTableOfContents(Document document) {
        NovelDataBuffer buffer = new NovelDataBuffer();

        try {
            // Title
            Element titleElement = document.selectFirst(siteConfig.getSelectors().getNovelTitle());
            if (titleElement != null) {
                buffer.setTitle(titleElement.text().trim());
            }

            // Author
            Element authorElement = document.selectFirst(siteConfig.getSelectors().getNovelAuthor());
            if (authorElement != null) {
                buffer.setAuthor(authorElement.text().trim());
            }

            // Status
            Element statusElement = document.selectFirst(siteConfig.getSelectors().getNovelStatus());
            if (statusElement != null) {
                buffer.setNovelStatus(statusElement.text().trim());
                buffer.setNovelCompleted(buffer.getNovelStatus().toLowerCase()
                        .contains(siteConfig.getCompletedStatus()));
            }

            // Description
            Elements descElements = document.select(siteConfig.getSelectors().getNovelDescription());
            for (Element desc : descElements) {
                buffer.getDescription().add(desc.text().trim());
            }

            // Genres
            Elements genreElements = document.select(siteConfig.getSelectors().getNovelGenres());
            for (Element genre : genreElements) {
                buffer.getGenres().add(genre.text().trim());
            }

            // Thumbnail
            Element thumbnailElement = document.selectFirst(siteConfig.getSelectors().getNovelThumbnailUrl());
            if (thumbnailElement != null) {
                String thumbnailUrl = thumbnailElement.attr(siteConfig.getSelectors().getThumbnailUrlAttribute());
                buffer.setThumbnailUrl(thumbnailUrl);
                // Download thumbnail image
                try {
                    // TODO: Implement image download
                } catch (Exception e) {
                    log.warn("Failed to download thumbnail", e);
                }
            }

        } catch (Exception e) {
            log.error("Error fetching novel data from table of contents", e);
        }

        return buffer;
    }

}
