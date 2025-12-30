package com.adewunmi.acedia.model.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Buffer class for storing novel data during scraping process
 */
@Data
public class NovelDataBuffer implements AutoCloseable {

    private String title;
    private List<String> chapterUrls = new ArrayList<>();
    private String novelStatus;
    private String lastTableOfContentsPageUrl;
    private boolean isNovelCompleted;
    private String thumbnailUrl;
    private double rating;
    private int totalRatings;
    private List<String> description = new ArrayList<>();
    private String author;
    private List<String> genres = new ArrayList<>();
    private List<String> alternativeNames = new ArrayList<>();
    private String mostRecentChapterTitle;
    private String currentChapterUrl;
    private String firstChapter;
    private byte[] thumbnailImage;
    private String novelUrl;

    @Override
    public void close() {
        if (chapterUrls != null) {
            chapterUrls.clear();
        }
        if (description != null) {
            description.clear();
        }
        if (genres != null) {
            genres.clear();
        }
        if (alternativeNames != null) {
            alternativeNames.clear();
        }
        thumbnailImage = null;
    }
}
