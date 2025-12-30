package com.adewunmi.acedia.scraper.factory;

import com.adewunmi.acedia.config.SiteConfiguration;
import com.adewunmi.acedia.scraper.ScraperStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NovelScraperFactory {

    private final ApplicationContext applicationContext;
    private final Map<String, String> websiteStrategyMap = new HashMap<>();

    {
        websiteStrategyMap.put("novelbin.me", "novelBinStrategy");
        websiteStrategyMap.put("novelfire.net", "novelfireStrategy");
    }

    public ScraperStrategy createScraper(URI tableOfContentsUri, SiteConfiguration siteConfig) {
        String host = tableOfContentsUri.getHost();

        for (Map.Entry<String, String> entry : websiteStrategyMap.entrySet()) {
            if (host.contains(entry.getKey())) {
                try {
                    ScraperStrategy strategy = applicationContext.getBean(entry.getValue(), ScraperStrategy.class);
                    log.info("Created scraper strategy: {}", entry.getValue());
                    return strategy;
                } catch (Exception e) {
                    log.error("Error creating scraper for: {}", host, e);
                    throw new RuntimeException("Failed to create scraper for: " + host, e);
                }
            }
        }

        throw new IllegalArgumentException("No scraper strategy found for: " + host);
    }
}
