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
public class SelectorTestRequestDto {

    /**
     * The URL of the page to test selectors against
     */
    private String url;

    /**
     * List of CSS selectors to test
     */
    private List<SelectorTest> selectors;

    /**
     * Whether to use Selenium for JavaScript-rendered sites
     */
    private boolean useSelenium;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectorTest {
        /**
         * Name/label for this selector (e.g., "novelTitle", "chapterLinks")
         */
        private String name;

        /**
         * The CSS selector to test
         */
        private String selector;

        /**
         * Optional attribute to extract (e.g., "src", "href"). If null, extracts text
         * content
         */
        private String attribute;

        /**
         * Whether to select all matching elements (true) or just the first (false)
         */
        private boolean selectAll;
    }
}
