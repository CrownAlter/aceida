package com.adewunmi.acedia.api.dto;

import com.adewunmi.acedia.model.entity.NovelFileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelResponseDto {

    private UUID id;
    private String title;
    private String author;
    private String siteName;
    private String url;
    private String genre;
    private String description;
    private String status;
    private Integer totalChapters;
    private Integer downloadedChapters;
    private LocalDateTime dateCreated;
    private LocalDateTime dateLastModified;
    private String saveLocation;
    private NovelFileType fileType;
    private boolean lastChapter;
    private String currentChapter;
}
