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

    /**
     * Loads the TOC page and activates the 'Chapters' tab so that the list is present in DOM.
     * Only used when Selenium is available.
     */
    private Document loadTocAndActivateChaptersTab(URI uri) throws java.io.IOException {
        if (webDriverPool == null) {
            return loadHtml(uri); // fallback; won't click the tab
        }
        org.openqa.selenium.WebDriver driver = null;
        try {
            driver = webDriverPool.borrowDriver();
            driver.get(uri.toString());

            org.openqa.selenium.support.ui.WebDriverWait wait =
                new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(45));
            // Wait for base page load
            try {
                wait.until(d -> {
                    try {
                        return ((org.openqa.selenium.JavascriptExecutor) d)
                            .executeScript("return document.readyState").equals("complete");
                    } catch (Exception ex) { return false; }
                });
            } catch (org.openqa.selenium.TimeoutException te) {
                log.warn("Timeout waiting for document.readyState on TOC page");
            }

            // Try to click the 'Chapters' tab
            try {
                org.openqa.selenium.By tabSelector = org.openqa.selenium.By.cssSelector("a[href='#tab-chapters-title'], [data-target='#tab-chapters-title']");
                org.openqa.selenium.WebElement tab = wait.until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(tabSelector));
                try { tab.click(); } catch (Exception clickEx) {
                    // Fallback: execute JS click
                    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", tab);
                }
            } catch (Exception e) {
                log.debug("Couldn't click 'Chapters' tab: {}", e.toString());
            }

            // Wait for chapter list to appear using configured selector
            String chaptersCss = siteConfig.getSelectors().getChapterLinks();
            if (chaptersCss != null && !chaptersCss.isBlank()) {
                String basic = chaptersCss.split(":")[0].trim();
                try {
                    wait.until(org.openqa.selenium.support.ui.ExpectedConditions.presenceOfElementLocated(
                        org.openqa.selenium.By.cssSelector(basic)));
                } catch (org.openqa.selenium.TimeoutException te) {
                    log.warn("Timeout waiting for chapter links selector: {}", basic);
                }
            }

            // Small delay for content to render
            try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            String html = driver.getPageSource();
            return org.jsoup.Jsoup.parse(html, uri.toString());
        } catch (Exception e) {
            throw new java.io.IOException("Failed to activate chapters tab: " + e.getMessage(), e);
        } finally {
            if (driver != null) webDriverPool.returnDriver(driver);
        }
    }

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
            if (preview.toLowerCase().contains("just a moment")) {
                log.warn("Cloudflare challenge detected on AJAX endpoint");
            }
            log.info("Falling back to parsing chapter links directly from the main page (clicking 'Chapters' tab if needed)");
            Document tocWithChapters = document;
            // Save what we got from AJAX for diagnostics
            maybeDumpDocument(chapterListDocument, "novelbin_ajax");
            try {
                if (siteConfig.isSeleniumSite()) {
                    tocWithChapters = loadTocAndActivateChaptersTab(siteTableOfContents);
                }
            } catch (Exception e) {
                log.warn("Failed to activate 'Chapters' tab via Selenium: {}", e.toString());
            }
            if (tocWithChapters != null && tocWithChapters.title() != null && tocWithChapters.title().toLowerCase().contains("just a moment")) {
                log.warn("Cloudflare challenge detected on TOC page");
            }
            maybeDumpDocument(tocWithChapters, "novelbin_toc_after_tab");
            chapterUrls = getChapterUrlsInRange(tocWithChapters, baseUri, null, null);
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
