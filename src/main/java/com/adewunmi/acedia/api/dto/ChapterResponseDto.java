package com.adewunmi.acedia.api.dto;

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
public class ChapterResponseDto {

    private UUID id;
    private String title;
    private String url;
    private float number;
    private LocalDateTime dateCreated;
    private boolean hasContent;
    private int pageCount;
}
