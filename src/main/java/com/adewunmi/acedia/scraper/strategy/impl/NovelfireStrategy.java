package com.adewunmi.acedia.scraper.strategy.impl;

import com.adewunmi.acedia.model.dto.NovelDataBuffer;
import com.adewunmi.acedia.scraper.ScraperStrategy;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Slf4j
@Component("novelfireStrategy")
public class NovelfireStrategy extends ScraperStrategy {
    @Override
    public NovelDataBuffer scrapeNovelData() throws Exception {
        log.info("Starting Novelfire scrape for: {}", siteTableOfContents);
        Document bookDoc;
        if (siteConfig.isSeleniumSite()) {
            log.info("Using Selenium to load Novelfire book page");
            try {
                bookDoc = loadHtmlWithSelenium(siteTableOfContents);
                log.info("Book page loaded successfully, title: {}", bookDoc.title());
            } catch (Exception e) {
                log.error("Failed to load book page with Selenium: {}", e.getMessage());
                throw e;
            }
        } else {
            bookDoc = loadHtml(siteTableOfContents);
        }

        NovelDataBuffer buffer = fetchNovelDataFromTableOfContents(bookDoc);
        buffer.setNovelUrl(siteTableOfContents.toString());

        // Chapters page is typically at /book/<slug>/chapters
        URI chaptersUri = URI.create(siteTableOfContents.toString().replaceAll("/+$", "") + "/chapters");
        log.info("Loading chapters page: {}", chaptersUri);
        Document chaptersDoc;
        if (siteConfig.isSeleniumSite()) {
            chaptersDoc = loadHtmlWithSelenium(chaptersUri);
        } else {
            chaptersDoc = loadHtml(chaptersUri);
        }

        // Determine pagination and collect chapters across all pages
        int lastPage = determineLastChaptersPage(chaptersDoc);
        log.info("Novelfire chapters pagination - last page detected: {}", lastPage);

        java.util.LinkedHashSet<String> chapterSet = new java.util.LinkedHashSet<>();
        // Page 1
        chapterSet.addAll(getChapterUrlsInRange(chaptersDoc, baseUri, null, null));

        // Additional pages 2..lastPage (with early exit if we have enough chapters)
        for (int i = 2; i <= lastPage; i++) {
            // Early exit if we already have enough chapters for the limit
            if (buffer.getChapterLimit() != null && chapterSet.size() >= buffer.getChapterLimit()) {
                log.info("Early exit from pagination: collected {} chapters (limit: {})", chapterSet.size(), buffer.getChapterLimit());
                break;
            }
            
            String pageUrl = chaptersUri.toString() + (chaptersUri.toString().contains("?") ? "&" : "?") + "page=" + i;
            log.info("Loading chapters page {}/{}: {}", i, lastPage, pageUrl);
            try {
                Document pageDoc = siteConfig.isSeleniumSite() ? loadHtmlWithSelenium(URI.create(pageUrl)) : loadHtml(URI.create(pageUrl));
                List<String> pageChapters = getChapterUrlsInRange(pageDoc, baseUri, null, null);
                chapterSet.addAll(pageChapters);
                log.info("Page {} yielded {} chapters (total so far: {})", i, pageChapters.size(), chapterSet.size());
            } catch (Exception e) {
                log.warn("Failed to load chapters page {}: {}", i, e.getMessage());
                // Continue to next page instead of failing entirely
            }
        }

        List<String> chapterUrls = new java.util.ArrayList<>(chapterSet);
        if (chapterUrls.isEmpty()) {
            log.warn("No chapter links found on chapters page(s) - attempting fallback on book page");
            chapterUrls = getChapterUrlsInRange(bookDoc, baseUri, null, null);
        }
        buffer.setChapterUrls(chapterUrls);
        log.info("Found {} chapter links for Novelfire", chapterUrls.size());

        if (!chapterUrls.isEmpty()) {
            buffer.setFirstChapter(chapterUrls.get(0));
            buffer.setCurrentChapterUrl(chapterUrls.get(chapterUrls.size() - 1));
        }
        return buffer;
    }

    private int determineLastChaptersPage(Document chaptersDoc) {
       try {
           int maxPage = 1;
           // Look for common pagination anchors with page numbers
           Elements pagerLinks = chaptersDoc.select("ul.pagination a");
           log.info("Found {} pagination links", pagerLinks.size());
           for (Element a : pagerLinks) {
               String href = a.attr("href");
               String text = a.text().trim();
               log.debug("Pagination link: text='{}', href='{}'", text, href);
               
               // Try to extract page number from href first
               java.util.regex.Matcher mHref = java.util.regex.Pattern.compile("[?&]page=(\\d+)").matcher(href);
               if (mHref.find()) {
                   int p = Integer.parseInt(mHref.group(1));
                   if (p > maxPage) maxPage = p;
                   log.debug("Found page {} in href", p);
               }
               
               // Also check text if it's a number
               if (text.matches("^\\d+$")) {
                   int p = Integer.parseInt(text);
                   if (p > maxPage) maxPage = p;
                   log.debug("Found page {} in text", p);
               }
           }
           log.info("Determined last chapters page: {}", maxPage);
           return maxPage;
       } catch (Exception e) {
           log.warn("Failed to detect last chapters page: {}", e.getMessage());
           return 1; // default
       }
   }

   @Override
    public NovelDataBuffer fetchNovelDataFromTableOfContents(Document document) {
        NovelDataBuffer buffer = new NovelDataBuffer();
        try {
            // Title
            Element title = document.selectFirst(siteConfig.getSelectors().getNovelTitle());
            if (title != null) buffer.setTitle(title.text().trim());

            // Author
            Element author = document.selectFirst(siteConfig.getSelectors().getNovelAuthor());
            if (author != null) buffer.setAuthor(author.text().trim());

            // Status
            Element status = document.selectFirst(siteConfig.getSelectors().getNovelStatus());
            if (status != null) {
                String s = status.text().trim();
                buffer.setNovelStatus(s);
                buffer.setNovelCompleted(s.toLowerCase().contains(siteConfig.getCompletedStatus()));
            }

            // Description
            Elements descEls = document.select(siteConfig.getSelectors().getNovelDescription());
            for (Element el : descEls) buffer.getDescription().add(el.text().trim());

            // Genres
            Elements genreEls = document.select(siteConfig.getSelectors().getNovelGenres());
            for (Element el : genreEls) buffer.getGenres().add(el.text().trim());

            // Thumbnail
            Element thumb = document.selectFirst(siteConfig.getSelectors().getNovelThumbnailUrl());
            if (thumb != null) {
                String attr = siteConfig.getSelectors().getThumbnailUrlAttribute();
                buffer.setThumbnailUrl(thumb.attr(attr != null && !attr.isBlank() ? attr : "src"));
            }
        } catch (Exception e) {
            log.error("Failed to extract Novelfire book data", e);
        }
        return buffer;
    }
}
