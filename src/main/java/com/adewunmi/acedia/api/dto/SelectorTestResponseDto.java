package com.adewunmi.acedia.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectorTestResponseDto {

    /**
     * The URL that was tested
     */
    private String url;

    /**
     * Results for each selector test
     */
    private List<SelectorTestResult> results;

    /**
     * Whether the page loaded successfully
     */
    private boolean success;

    /**
     * Error message if the page failed to load
     */
    private String errorMessage;

    /**
     * The HTML source of the page (first 1000 characters for debugging)
     */
    private String htmlPreview;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectorTestResult {
        /**
         * Name/label for this selector
         */
        private String name;

        /**
         * The CSS selector that was tested
         */
        private String selector;

        /**
         * Whether the selector matched any elements
         */
        private boolean found;

        /**
         * Number of elements matched
         */
        private int matchCount;

        /**
         * The extracted values (text or attribute values)
         */
        private List<String> values;

        /**
         * Sample HTML of the first matched element (for debugging)
         */
        private String elementHtml;

        /**
         * Error message if selector test failed
         */
        private String error;
    }
}
