package com.adewunmi.acedia.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for page data during scraping
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageData {
    private String url;
    private String imagePath;
}
