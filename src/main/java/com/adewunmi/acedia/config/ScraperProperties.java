package com.adewunmi.acedia.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "scraper")
@Data
public class ScraperProperties {

    private String userAgent;
    private int httpTimeout;
    private int concurrentRequests = 2;
    private String saveLocation;
    private String novelSaveLocation;
    private String mangaSaveLocation;
    private boolean saveAsSingleFile = true;
    private String defaultMangaExtension = "PDF";
    private List<SiteConfiguration> siteConfigurations = new ArrayList<>();
    private SeleniumSettings seleniumSettings;
}
