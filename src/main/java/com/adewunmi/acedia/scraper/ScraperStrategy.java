package com.adewunmi.acedia.scraper;

import com.adewunmi.acedia.config.SiteConfiguration;
import com.adewunmi.acedia.model.dto.ChapterDataBuffer;
import com.adewunmi.acedia.model.dto.NovelDataBuffer;
import com.adewunmi.acedia.model.dto.PageData;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Base abstract class for all scraper strategies
 */
@Slf4j
public abstract class ScraperStrategy {

    // Debug dump settings via environment variables
    private final boolean debugDumpEnabled = "true".equalsIgnoreCase(System.getenv().getOrDefault("SCRAPER_DEBUG_DUMP", "false"));
    private final String debugDumpDir = System.getenv().getOrDefault("SCRAPER_DEBUG_DIR",
            System.getProperty("user.home") + "/.benny-scraper/debug");

    // Proxy settings via environment variables (HTTPS takes precedence)
    private final String httpsProxyEnv = System.getenv("HTTPS_PROXY");
    private final String httpProxyEnv = System.getenv("HTTP_PROXY");

    protected static class ProxyInfo {
        final String scheme; // http or https
        final String host;
        final int port;
        final String originalUrl;
        ProxyInfo(String scheme, String host, int port, String originalUrl) {
            this.scheme = scheme; this.host = host; this.port = port; this.originalUrl = originalUrl;
        }
    }

    protected ProxyInfo resolveProxy() {
        String proxyUrl = httpsProxyEnv != null && !httpsProxyEnv.isBlank() ? httpsProxyEnv : httpProxyEnv;
        if (proxyUrl == null || proxyUrl.isBlank()) return null;
        try {
            java.net.URI p = java.net.URI.create(proxyUrl);
            String scheme = p.getScheme() != null ? p.getScheme() : "http";
            String host = p.getHost();
            int port = p.getPort() > 0 ? p.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
            if (host == null) {
                // Handle host:port without scheme
                if (!proxyUrl.contains("://")) {
                    String[] parts = proxyUrl.split(":");
                    if (parts.length == 2) {
                        host = parts[0];
                        port = Integer.parseInt(parts[1]);
                        scheme = "http";
                    }
                }
            }
            if (host != null) return new ProxyInfo(scheme, host, port, proxyUrl);
        } catch (Exception ignored) {}
        return null;
    }

    protected void applyProxyToJsoup(Connection connection) {
        ProxyInfo proxy = resolveProxy();
        if (proxy != null && proxy.host != null) {
            try {
                connection.proxy(proxy.host, proxy.port);
                log.info("Using proxy for Jsoup: {}:{} (scheme: {})", proxy.host, proxy.port, proxy.scheme);
            } catch (Exception e) {
                log.warn("Failed to apply proxy to Jsoup: {}", e.getMessage());
            }
        }
    }

    protected void maybeDumpDocument(Document doc, String label) {
        if (!debugDumpEnabled || doc == null) return;
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(debugDumpDir);
            java.nio.file.Files.createDirectories(dir);
            String safeLabel = label.replaceAll("[^a-zA-Z0-9-_]", "_");
            String fileName = String.format("%s/%s_%d.html", dir.toString(), safeLabel, System.currentTimeMillis());
            java.nio.file.Files.writeString(java.nio.file.Paths.get(fileName), doc.outerHtml());
            log.info("Debug dump saved: {} (title='{}', len={})", fileName, doc.title(), doc.outerHtml().length());
        } catch (Exception e) {
            log.warn("Failed to dump document: {}", e.getMessage());
        }
    }

    protected static final int MAX_RETRIES = 6;
    protected static final int MINIMUM_PARAGRAPH_THRESHOLD = 5;
    protected static final int TOTAL_POSSIBLE_PAGINATION_TABS = 6;

    protected SiteConfiguration siteConfig;
    protected URI baseUri;
    protected URI siteTableOfContents;
    protected int concurrentRequestsLimit = 2;
    protected Semaphore semaphore;
    protected ExecutorService executorService;
    
    // Selenium WebDriver pool for JS-rendered sites
    protected SeleniumWebDriverPool webDriverPool;

    protected static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0");
    private int userAgentIndex = 0;
    private java.util.Random random = new java.util.Random();

    /**
     * Main scraping method to be implemented by each strategy
     */
    public abstract NovelDataBuffer scrapeNovelData() throws Exception;

    /**
     * Fetches novel data from table of contents
     */
    public abstract NovelDataBuffer fetchNovelDataFromTableOfContents(Document document);

    /**
     * Sets the configuration variables
     */
    public void setVariables(SiteConfiguration siteConfig, URI siteTableOfContents, int concurrencyLimit) {
        this.siteConfig = siteConfig;
        this.siteTableOfContents = siteTableOfContents;

        // Ensure minimum concurrency limit of 1, default to 2 if invalid
        if (concurrencyLimit <= 0) {
            log.warn("Invalid concurrency limit ({}), using default value of 2", concurrencyLimit);
            concurrencyLimit = 2;
        }

        this.concurrentRequestsLimit = Math.min(concurrencyLimit, Runtime.getRuntime().availableProcessors());

        // Ensure we have at least 1 thread
        if (this.concurrentRequestsLimit <= 0) {
            this.concurrentRequestsLimit = 1;
        }

        this.semaphore = new Semaphore(this.concurrentRequestsLimit);

        // Create a thread pool with enough threads to handle concurrent requests
        // Add buffer threads to avoid deadlock with semaphore
        int threadPoolSize = this.concurrentRequestsLimit * 2;
        log.debug("Creating thread pool with {} threads for {} concurrent requests",
                threadPoolSize, this.concurrentRequestsLimit);
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        setBaseUri(siteTableOfContents);
        
        // Initialize WebDriver pool if this site needs Selenium
        if (siteConfig.isSeleniumSite()) {
            log.info("Site requires Selenium - initializing WebDriver pool");
            this.webDriverPool = new SeleniumWebDriverPool();
        }
    }

    /**
     * Loads HTML using Selenium WebDriver for JS-rendered pages (uses pool)
     */
    protected Document loadHtmlWithSelenium(URI uri) throws IOException {
        return loadHtmlWithSelenium(uri, false);
    }
    
    /**
     * Loads HTML using Selenium WebDriver for JS-rendered pages (uses pool)
     * @param uri The URI to load
     * @param isChapterPage Whether this is a chapter content page (affects which selectors to wait for)
     */
    protected Document loadHtmlWithSelenium(URI uri, boolean isChapterPage) throws IOException {
        if (webDriverPool == null) {
            throw new IOException("WebDriver pool not initialized - site configuration issue");
        }
        
        org.openqa.selenium.WebDriver driver = null;
        try {
            driver = webDriverPool.borrowDriver();
            java.time.Duration timeout = java.time.Duration.ofMillis(30000);

            log.debug("Loading page with Selenium: {}", uri);
            driver.get(uri.toString());

            // Wait for page ready with longer timeout for slow-loading pages
            org.openqa.selenium.support.ui.WebDriverWait wait = 
                new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(60));
            
            try {
                // Wait for document ready
                wait.until(d -> {
                    try {
                        return ((org.openqa.selenium.JavascriptExecutor) d)
                                .executeScript("return document.readyState").equals("complete");
                    } catch (Exception ex) {
                        return false;
                    }
                });
                
                // Only wait for chapter content selector if this is actually a chapter page
                if (isChapterPage) {
                    String contentSelector = siteConfig.getSelectors().getChapterContent();
                    if (contentSelector != null && !contentSelector.isBlank()) {
                        // Convert JSoup selector to basic CSS (remove pseudo-selectors if any)
                        String basicSelector = contentSelector.split(":")[0].trim();
                        log.debug("Waiting for chapter content selector: {}", basicSelector);
                        
                        // Use a shorter timeout for the content check (30s) and handle failure gracefully
                        org.openqa.selenium.support.ui.WebDriverWait contentWait = 
                            new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(45));
                        contentWait.until(org.openqa.selenium.support.ui.ExpectedConditions
                                .presenceOfElementLocated(org.openqa.selenium.By.cssSelector(basicSelector)));
                        log.debug("Chapter content selector found successfully");
                    }
                } else {
                    log.debug("Not a chapter page - skipping content selector wait");
                }
            } catch (org.openqa.selenium.TimeoutException e) {
                log.warn("Selenium timeout waiting for content on {}: {}", uri, e.getMessage());
                // Continue anyway - we'll check for content later
            } catch (Exception e) {
                log.warn("Selenium wait condition not fully met for {}: {}", uri, e.toString());
                // Continue anyway - we'll check for content later
            }

            // Add delay to ensure all dynamic content loads and avoid rate limiting
            Thread.sleep(2000 + random.nextInt(2000)); // 2-4 seconds delay

            String pageSource = driver.getPageSource();
            log.debug("Page source retrieved, length: {} chars", pageSource.length());
            
            // Parse with Jsoup so downstream logic can reuse selectors
            Document parsed = org.jsoup.Jsoup.parse(pageSource, uri.toString());
            if (parsed.title() != null && parsed.title().toLowerCase().contains("just a moment")) {
                maybeDumpDocument(parsed, "cloudflare_selenium");
            }
            return parsed;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while loading page with Selenium", e);
        } catch (Exception e) {
            throw new IOException("Selenium failed to load page: " + uri + " - " + e.getMessage(), e);
        } finally {
            if (driver != null) {
                webDriverPool.returnDriver(driver);
            }
        }
    }

    /**
     * Loads HTML document for chapter pages with special handling to avoid redirects
     */
    protected Document loadChapterHtml(URI uri) throws IOException {
        // For novelbin, we need to avoid the redirect to .com which has stricter anti-bot
        // So we disable redirects for chapter pages
        int attempt = 0;
        IOException lastException = null;

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;

                if (attempt > 1) {
                    int baseDelay = 1000 * (int) Math.pow(2, attempt - 2);
                    int jitter = random.nextInt(1000);
                    int delay = Math.min(baseDelay + jitter, 20000);
                    log.info("Retry attempt {}/{} for {} - waiting {}ms before retry",
                            attempt, MAX_RETRIES, uri, delay);
                    Thread.sleep(delay);
                }

                log.debug("Loading chapter HTML from: {} (attempt {}/{})", uri, attempt, MAX_RETRIES);

                String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
                String domain = uri.getHost();
                String scheme = uri.getScheme();
                String referrer = scheme + "://" + domain + "/";

                // Create connection with enhanced headers to avoid bot detection on .com domain
                Connection connection = Jsoup.connect(uri.toString())
                        .userAgent(userAgent)
                        .timeout(30000)
                        .followRedirects(true) // Follow redirects but with better headers
                        .ignoreHttpErrors(false)
                        .maxBodySize(10 * 1024 * 1024)
                        .header("Accept",
                                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", "cross-site") // Important for redirects
                        .header("Sec-Fetch-User", "?1")
                        .header("Cache-Control", "no-cache")
                        .header("Pragma", "no-cache")
                        .header("Sec-CH-UA",
                                "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                        .header("Sec-CH-UA-Mobile", "?0")
                        .header("Sec-CH-UA-Platform", "\"Windows\"");

                // Always add referrer for chapter pages to look like navigation from the site
                connection.referrer(referrer);

                applyProxyToJsoup(connection);
                Document doc = connection.get();
                if (doc.title() != null && doc.title().toLowerCase().contains("just a moment")) {
                    maybeDumpDocument(doc, "cloudflare_jsoup_chapter");
                }
                Thread.sleep(500 + random.nextInt(1500));
                log.debug("Successfully loaded chapter HTML from: {}", uri);
                return doc;

            } catch (IOException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Failed to load chapter HTML from {} (attempt {}/{}): {}",
                        uri, attempt, MAX_RETRIES, errorMsg);

                if (errorMsg.contains("Status=404")) {
                    log.error("Page not found (404), not retrying: {}", errorMsg);
                    throw e;
                }

                if (errorMsg.contains("Status=403")) {
                    log.warn("Got 403 Forbidden - trying with different approach");
                }
                
                // If we get a redirect response, we need to handle it differently
                if (e instanceof org.jsoup.HttpStatusException) {
                    org.jsoup.HttpStatusException httpEx = (org.jsoup.HttpStatusException) e;
                    int statusCode = httpEx.getStatusCode();
                    if (statusCode >= 300 && statusCode < 400) {
                        log.warn("Got redirect ({}), but redirects are disabled for chapter pages. This might be expected.", statusCode);
                        // For novelbin, the .me domain redirects to .com, but we want to stay on .me
                        // So we'll treat redirects as errors and retry
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while loading HTML", e);
            }
        }

        throw new IOException("Failed to load chapter HTML after " + MAX_RETRIES + " attempts. Last error: " +
                (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    /**
     * Loads HTML document with retry logic and exponential backoff
     * Automatically falls back to Selenium if JSoup fails with 403 on selenium-enabled sites
     */
    protected Document loadHtml(URI uri) throws IOException {
        int attempt = 0;
        IOException lastException = null;
        boolean triedSelenium = false;

        while (attempt < MAX_RETRIES) {
            try {
                attempt++;

                // Add delay before retry (exponential backoff with jitter)
                if (attempt > 1) {
                    int baseDelay = 1000 * (int) Math.pow(2, attempt - 2); // 1s, 2s, 4s, 8s, 16s
                    int jitter = random.nextInt(1000); // Random 0-1000ms jitter
                    int delay = Math.min(baseDelay + jitter, 20000); // Cap at 20 seconds

                    log.info("Retry attempt {}/{} for {} - waiting {}ms before retry",
                            attempt, MAX_RETRIES, uri, delay);
                    Thread.sleep(delay);
                }

                // If we've failed 2+ times with 403 and site supports Selenium, try Selenium
                if (attempt >= 2 && !triedSelenium && siteConfig != null && siteConfig.isSeleniumSite() && lastException != null && lastException.getMessage() != null && lastException.getMessage().contains("Status=403")) {
                    log.info("Switching to Selenium after 403 errors from JSoup");
                    triedSelenium = true;
                    try {
                        return loadHtmlWithSelenium(uri);
                    } catch (IOException e) {
                        log.warn("Selenium also failed, continuing with JSoup retries: {}", e.getMessage());
                        lastException = e;
                        continue;
                    }
                }

                log.debug("Loading HTML from: {} (attempt {}/{})", uri, attempt, MAX_RETRIES);

                // Rotate user agents
                String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));

                // Extract domain for referrer
                String domain = uri.getHost();
                String scheme = uri.getScheme();
                String referrer = scheme + "://" + domain + "/";

                // Create connection with realistic browser headers
                Connection connection = Jsoup.connect(uri.toString())
                        .userAgent(userAgent)
                        .timeout(30000) // Increased timeout to 30 seconds
                        .followRedirects(true)
                        .ignoreHttpErrors(false)
                        .maxBodySize(10 * 1024 * 1024) // 10MB max
                        // Add realistic browser headers
                        .header("Accept",
                                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        // Note: Removed Accept-Encoding to let JSoup handle compression automatically
                        // .header("Accept-Encoding", "gzip, deflate, br")
                        // Note: "Connection" header is restricted in Java's HttpClient and managed automatically
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("Sec-Fetch-Dest", "document")
                        .header("Sec-Fetch-Mode", "navigate")
                        .header("Sec-Fetch-Site", attempt > 1 ? "same-origin" : "none")
                        .header("Sec-Fetch-User", "?1")
                        .header("Cache-Control", "max-age=0")
                        .header("DNT", "1")
                        .header("Sec-CH-UA",
                                "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                        .header("Sec-CH-UA-Mobile", "?0")
                        .header("Sec-CH-UA-Platform", "\"Windows\"");

                // Add referrer on retry attempts to look like navigation from the site itself
                if (attempt > 1) {
                    connection.referrer(referrer);
                }

                Document doc = connection.get();

                // Add small random delay after successful fetch (0.5-2 seconds)
                Thread.sleep(500 + random.nextInt(1500));

                log.debug("Successfully loaded HTML from: {}", uri);
                return doc;

            } catch (IOException e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("Failed to load HTML from {} (attempt {}/{}): {}",
                        uri, attempt, MAX_RETRIES, errorMsg);

                // Don't retry on 404 (page doesn't exist)
                if (errorMsg.contains("Status=404")) {
                    log.error("Page not found (404), not retrying: {}", errorMsg);
                    throw e;
                }

                // For 403, retry with different user agent and longer delay
                if (errorMsg.contains("Status=403")) {
                    log.warn("Got 403 Forbidden - site may be blocking AWS IPs or detecting automation");
                    // Continue to retry with exponential backoff
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while loading HTML", e);
            }
        }

        throw new IOException("Failed to load HTML after " + MAX_RETRIES + " attempts. Last error: " +
                (lastException != null ? lastException.getMessage() : "Unknown"), lastException);
    }

    /**
     * Gets chapter URLs from the table of contents
     */
    protected List<String> getChapterUrlsInRange(Document document, URI baseSiteUri,
            Integer startChapter, Integer endChapter) {
        log.info("Getting chapter urls from table of contents");

        try {
            Elements chapterLinks = document.select(siteConfig.getSelectors().getChapterLinks());

            if (chapterLinks.isEmpty()) {
                log.warn("No chapter links found");
                return new ArrayList<>();
            }

            List<String> chapterUrls = new ArrayList<>();
            int chapterIndex = 0;

            for (Element link : chapterLinks) {
                chapterIndex++;
                String chapterUrl = link.attr("href");

                if (chapterUrl != null && !chapterUrl.isEmpty() &&
                        (startChapter == null || chapterIndex >= startChapter) &&
                        (endChapter == null || chapterIndex <= endChapter)) {

                    if (!isValidHttpUrl(chapterUrl)) {
                        chapterUrl = baseSiteUri.resolve(chapterUrl).toString();
                    }
                    chapterUrls.add(chapterUrl);
                }
            }

            return chapterUrls;
        } catch (Exception e) {
            log.error("Error getting chapter urls", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets paginated chapter URLs
     */
    protected CompletableFuture<List<String>> getPaginatedChapterUrlsAsync(
            URI tableOfContentUri, boolean getAllChapters, int pageToStopAt) {

        return CompletableFuture.supplyAsync(() -> {
            List<String> chapterUrls = new ArrayList<>();
            String baseTableOfContentUrl = tableOfContentUri + siteConfig.getPaginationType();

            for (int i = 1; i <= pageToStopAt; i++) {
                String tableOfContentUrl = String.format(baseTableOfContentUrl, i);

                try {
                    log.info("Navigating to page {}: {}", i, tableOfContentUrl);
                    Document document = loadHtml(URI.create(tableOfContentUrl));
                    List<String> pageUrls = getChapterUrlsInRange(document, baseUri, 1, null);
                    chapterUrls.addAll(pageUrls);

                } catch (IOException e) {
                    log.error("Error loading page {}", tableOfContentUrl, e);
                }
            }

            return chapterUrls;
        });
    }

    /**
     * Gets chapter data asynchronously for multiple chapters
     */
    public List<ChapterDataBuffer> getChaptersDataAsync(List<String> chapterUrls) throws Exception {
        int totalChapters = chapterUrls.size();
        log.info("Starting to scrape {} chapters with {} concurrent threads...",
                totalChapters, concurrentRequestsLimit);

        List<CompletableFuture<ChapterDataBuffer>> futures = new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < chapterUrls.size(); i++) {
                final String url = chapterUrls.get(i);
                final int chapterNum = i + 1;

                // Use dedicated executor service instead of default ForkJoinPool
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        log.debug("Waiting for semaphore permit for chapter {}...", chapterNum);
                        semaphore.acquire();
                        log.debug("Acquired semaphore permit for chapter {}, starting scrape...", chapterNum);

                        ChapterDataBuffer buffer = getChapterData(url);
                        int completed = completedCount.incrementAndGet();

                        // Log progress every 10 chapters or on specific milestones
                        if (completed % 10 == 0 || completed == totalChapters || completed == 1) {
                            long elapsed = System.currentTimeMillis() - startTime;
                            double avgTimePerChapter = elapsed / (double) completed;
                            int remaining = totalChapters - completed;
                            long estimatedTimeLeft = (long) (remaining * avgTimePerChapter);

                            double progress = (completed * 100.0) / totalChapters;
                            log.info(
                                    "Progress: {}/{} chapters scraped ({}%) - Current: Chapter {} - Est. time remaining: {}s",
                                    completed, totalChapters, String.format("%.1f", progress), chapterNum,
                                    estimatedTimeLeft / 1000);
                        }

                        return buffer;
                    } catch (Exception e) {
                        int failed = failedCount.incrementAndGet();
                        int completed = completedCount.incrementAndGet();
                        log.error("ERROR getting chapter data for Chapter {} (URL: {}). Failed count: {}", 
                                chapterNum, url, failed);
                        log.error("Exception details: ", e);
                        ChapterDataBuffer emptyBuffer = new ChapterDataBuffer();
                        emptyBuffer.setUrl(url);
                        return emptyBuffer;
                    } finally {
                        semaphore.release();
                        log.debug("Released semaphore permit for chapter {}", chapterNum);
                    }
                }, executorService));
            }

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));

            log.info("All scraping tasks submitted. Waiting for completion...");
            allFutures.join();

            long totalTime = System.currentTimeMillis() - startTime;
            log.info("Chapter scraping completed in {}s! Successfully scraped: {}, Failed: {}",
                    totalTime / 1000, completedCount.get() - failedCount.get(), failedCount.get());

            List<ChapterDataBuffer> results = new ArrayList<>();
            int sequenceNumber = 1;
            for (CompletableFuture<ChapterDataBuffer> future : futures) {
                ChapterDataBuffer buffer = future.get();
                buffer.setSequenceNumber(sequenceNumber++);
                results.add(buffer);
            }

            return results;
        } finally {
            // Clean up executor service after use
            if (executorService != null && !executorService.isShutdown()) {
                log.debug("Shutting down executor service...");
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            // Clean up WebDriver pool after scraping
            if (webDriverPool != null) {
                log.info("Shutting down WebDriver pool after chapter scraping");
                webDriverPool.shutdown();
                webDriverPool = null;
            }
        }
    }

    /**
     * Gets chapter data for a single chapter
     */
    protected ChapterDataBuffer getChapterData(String url) {
        ChapterDataBuffer buffer = new ChapterDataBuffer();
        buffer.setUrl(url);
        buffer.setDateLastModified(LocalDateTime.now());

        try {
            log.debug("Getting chapter data from: {}", url);
            // If site requires JS, use Selenium to fetch
            Document document;
            if (siteConfig.isSeleniumSite()) {
                log.info("Using Selenium to load chapter page: {}", url);
                document = loadHtmlWithSelenium(URI.create(url), true); // true = this is a chapter page
            } else {
                // Use specialized method for chapter pages that may need different settings
                document = loadChapterHtml(URI.create(url));
            }

            // Get title
            String titleSelector = siteConfig.getSelectors().getChapterTitle();
            log.info("Extracting chapter title with selector: {}", titleSelector);
            Element titleElement = document.selectFirst(titleSelector);
            if (titleElement != null) {
                String title = titleElement.text().trim();
                buffer.setTitle(title);
                log.info("✓ Chapter title extracted: '{}'", title);
            } else {
                log.warn("✗ No chapter title found with selector: {}", titleSelector);
            }

            // Get content
            String contentSelector = siteConfig.getSelectors().getChapterContent();
            log.info("Extracting chapter content with selector: {}", contentSelector);
            Elements contentElements = document.select(contentSelector);
            log.info("Found {} content elements with selector: {}", contentElements.size(), contentSelector);
            
            if (contentElements.size() < MINIMUM_PARAGRAPH_THRESHOLD &&
                    siteConfig.getSelectors().getAlternativeChapterContent() != null) {
                log.info("Using alternative content selector (below minimum threshold)");
                contentElements = document.select(siteConfig.getSelectors().getAlternativeChapterContent());
            }

            StringBuilder content = new StringBuilder();
            for (Element element : contentElements) {
                String text = element.text().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n");
                }
            }
            
            String finalContent = content.toString();
            buffer.setContent(finalContent);
            log.info("✓ Chapter content extracted: {} characters, {} paragraphs", 
                finalContent.length(), contentElements.size());

        } catch (Exception e) {
            log.error("Error getting chapter data", e);
        }

        return buffer;
    }

    /**
     * Sets the base URI from the site URI
     */
    protected void setBaseUri(URI siteUri) {
        if (siteUri == null) {
            throw new IllegalArgumentException("Site URI cannot be null");
        }
        this.baseUri = URI.create(siteUri.getScheme() + "://" + siteUri.getHost());
    }

    /**
     * Checks if a URL is a valid HTTP/HTTPS URL
     */
    protected boolean isValidHttpUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * Decodes HTML entities
     */
    protected String decodeHtml(String html) {
        return URLDecoder.decode(html, StandardCharsets.UTF_8);
    }
}
