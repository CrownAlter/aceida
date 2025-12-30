package com.adewunmi.acedia.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ScrapeRequestDto {

    @NotBlank(message = "URL is required")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    private String url;

    private String fileType; // EPUB, PDF, CBZ, etc. (optional, will use defaults)
    
    private Integer chapterLimit; // Limit number of chapters to scrape (optional, for testing)
    
    // New: Chapter selection options
    private Integer chapterNumber; // Single specific chapter to scrape (1-based)
    private Integer chapterStart;  // Starting chapter number (1-based, inclusive)
    private Integer chapterEnd;    // Ending chapter number (1-based, inclusive)
    
    /**
     * Priority logic:
     * 1. If chapterNumber is set → downloads only that single chapter
     * 2. If chapterLimit is set → downloads chapters 1 to chapterLimit
     * 3. If chapterStart/chapterEnd are set → downloads that range
     * 4. If only chapterStart is set → downloads from that chapter to the end
     * 5. If only chapterEnd is set → downloads from chapter 1 to chapterEnd
     * 6. If nothing is set → downloads all chapters
     */
}
