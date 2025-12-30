package com.adewunmi.acedia.service;

import com.adewunmi.acedia.api.dto.SelectorTestRequestDto;
import com.adewunmi.acedia.api.dto.SelectorTestResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Service for testing CSS selectors against web pages
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelectorTestService {

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

    private final Random random = new Random();

    /**
     * Test CSS selectors against a URL
     */
    public SelectorTestResponseDto testSelectors(SelectorTestRequestDto request) {
        log.info("Testing selectors for URL: {}", request.getUrl());

        SelectorTestResponseDto.SelectorTestResponseDtoBuilder responseBuilder = SelectorTestResponseDto.builder()
                .url(request.getUrl())
                .results(new ArrayList<>());

        try {
            // Load the page
            Document document;
            if (request.isUseSelenium()) {
                // TODO: Implement Selenium support if needed
                log.warn("Selenium support not yet implemented, falling back to Jsoup");
                document = loadPageWithJsoup(request.getUrl());
            } else {
                document = loadPageWithJsoup(request.getUrl());
            }

            // Get HTML preview (first 1000 chars)
            String html = document.html();
            String preview = html.length() > 1000 ? html.substring(0, 1000) + "..." : html;
            responseBuilder.htmlPreview(preview);

            // Test each selector
            for (SelectorTestRequestDto.SelectorTest selectorTest : request.getSelectors()) {
                SelectorTestResponseDto.SelectorTestResult result = testSingleSelector(document, selectorTest);
                responseBuilder.results(responseBuilder.build().getResults());
                responseBuilder.build().getResults().add(result);
            }

            responseBuilder.success(true);

        } catch (Exception e) {
            log.error("Error testing selectors for URL: {}", request.getUrl(), e);
            responseBuilder.success(false)
                    .errorMessage(e.getMessage());
        }

        return responseBuilder.build();
    }

    /**
     * Test a single CSS selector
     */
    private SelectorTestResponseDto.SelectorTestResult testSingleSelector(
            Document document, SelectorTestRequestDto.SelectorTest selectorTest) {

        SelectorTestResponseDto.SelectorTestResult.SelectorTestResultBuilder resultBuilder = SelectorTestResponseDto.SelectorTestResult
                .builder()
                .name(selectorTest.getName())
                .selector(selectorTest.getSelector())
                .values(new ArrayList<>());

        try {
            Elements elements;
            if (selectorTest.isSelectAll()) {
                elements = document.select(selectorTest.getSelector());
            } else {
                Element element = document.selectFirst(selectorTest.getSelector());
                elements = element != null ? new Elements(element) : new Elements();
            }

            resultBuilder.found(!elements.isEmpty())
                    .matchCount(elements.size());

            // Extract values
            List<String> values = new ArrayList<>();
            for (Element element : elements) {
                String value;
                if (selectorTest.getAttribute() != null && !selectorTest.getAttribute().isEmpty()) {
                    value = element.attr(selectorTest.getAttribute());
                } else {
                    value = element.text();
                }

                // Limit value length for display
                if (value.length() > 200) {
                    value = value.substring(0, 200) + "...";
                }
                values.add(value);
            }
            resultBuilder.values(values);

            // Get HTML of first matched element
            if (!elements.isEmpty()) {
                String elementHtml = elements.first().outerHtml();
                if (elementHtml.length() > 500) {
                    elementHtml = elementHtml.substring(0, 500) + "...";
                }
                resultBuilder.elementHtml(elementHtml);
            }

        } catch (Exception e) {
            log.error("Error testing selector: {}", selectorTest.getSelector(), e);
            resultBuilder.found(false)
                    .matchCount(0)
                    .error(e.getMessage());
        }

        return resultBuilder.build();
    }

    /**
     * Load a page using Jsoup
     */
    private Document loadPageWithJsoup(String url) throws Exception {
        log.debug("Loading page with Jsoup: {}", url);

        String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

        Connection connection = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(30000)
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .maxBodySize(10 * 1024 * 1024)
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                // Note: Removed Accept-Encoding to let JSoup handle compression automatically
                // .header("Accept-Encoding", "gzip, deflate, br")
                // Note: "Connection" header is restricted in Java's HttpClient and managed automatically
                .header("Upgrade-Insecure-Requests", "1");

        Document doc = connection.get();
        log.debug("Successfully loaded page: {}", url);

        return doc;
    }
}
