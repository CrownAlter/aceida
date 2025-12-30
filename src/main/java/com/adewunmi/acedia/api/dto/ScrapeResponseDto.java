package com.adewunmi.acedia.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapeResponseDto {

    private UUID novelId;
    private String title;
    private String message;
    private String status; // STARTED, IN_PROGRESS, COMPLETED, FAILED
    private Integer totalChapters;
    private Integer downloadedChapters;
}
