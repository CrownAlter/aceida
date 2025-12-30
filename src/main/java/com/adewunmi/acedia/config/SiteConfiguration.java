package com.adewunmi.acedia.config;

import lombok.Data;

@Data
public class SiteConfiguration {

    private String name;
    private String urlPattern;
    private boolean hasPagination;
    private String paginationType;
    private String paginationQueryPartial;
    private boolean hasNovelInfoOnDifferentPage;
    private int chaptersPerPage;
    private int pageOffSet;
    private String completedStatus;
    private boolean hasImagesForChapterContent;
    private boolean isSeleniumSite;
    private Selectors selectors;
}
