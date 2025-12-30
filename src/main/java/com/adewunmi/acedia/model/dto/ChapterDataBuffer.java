package com.adewunmi.acedia.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buffer class for storing chapter data during scraping
 */
@Data
public class ChapterDataBuffer implements AutoCloseable {

    private String url;
    private String content;
    private String title;
    private int sequenceNumber;
    private LocalDateTime dateLastModified;
    private List<PageData> pages;
    private String tempDirectory;

    public float getNumber() {
        if (title == null || title.isEmpty()) {
            return 0f;
        }
        Pattern pattern = Pattern.compile("[+-]?([0-9]*[.])?[0-9]+");
        Matcher matcher = pattern.matcher(title);
        if (matcher.find()) {
            return Float.parseFloat(matcher.group(0));
        }
        return 0f;
    }

    @Override
    public void close() {
        if (pages != null) {
            for (PageData page : pages) {
                page.setImagePath(null);
            }
            pages.clear();
        }
    }
}
