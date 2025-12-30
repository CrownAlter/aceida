package com.adewunmi.acedia.scraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages a pool of Selenium WebDriver instances to avoid the overhead of
 * creating a new browser instance for every chapter.
 */
@Slf4j
@Component
public class SeleniumWebDriverPool {

    private static final int MAX_POOL_SIZE = 2; // Reduced from 3 to 2 for memory optimization
    private static final long MAX_DRIVER_AGE_MS = 600000; // 10 minutes - refresh to avoid memory leaks

    private final ConcurrentLinkedQueue<PooledDriver> availableDrivers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger activeDriverCount = new AtomicInteger(0);
    private final List<String> USER_AGENTS = Arrays.asList(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    private final Random random = new Random();
    private volatile boolean initialized = false;
    private final Object initLock = new Object(); // For thread-safe lazy initialization

    /**
     * Wrapper for WebDriver with creation timestamp
     */
    private static class PooledDriver {
        final WebDriver driver;
        final long createdAt;

        PooledDriver(WebDriver driver) {
            this.driver = driver;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - createdAt) > MAX_DRIVER_AGE_MS;
        }
    }

    /**
     * Initialize WebDriverManager once - LAZY initialization
     * Only called when first WebDriver is actually needed, not at application
     * startup
     * This saves memory if Selenium is never used during application lifetime
     */
    private void ensureInitialized() {
        // Double-checked locking for thread-safe lazy initialization
        if (!initialized) {
            synchronized (initLock) {
                if (!initialized) {
                    try {
                        log.info("Lazy initializing ChromeDriver (first use - this happens only once)...");
                        log.info("Setting up ChromeDriver via WebDriverManager...");

                        // Configure WebDriverManager with more options
                        WebDriverManager wdm = WebDriverManager.chromedriver();

                        // Try to use local Chrome installation first, then download if needed
                        wdm.clearDriverCache().clearResolutionCache();

                        // Set up with timeout and retry logic
                        wdm.timeout(120); // 2 minutes timeout for download
                        wdm.setup();

                        initialized = true;
                        log.info("ChromeDriver setup complete (lazy initialization successful)");
                        log.info("Memory savings: ChromeDriver was not initialized until actually needed");

                        // Log Chrome version for debugging
                        try {
                            String chromeVersion = wdm.getDownloadedDriverVersion();
                            log.info("Using ChromeDriver version: {}", chromeVersion);
                        } catch (Exception ex) {
                            log.debug("Could not determine ChromeDriver version");
                        }

                    } catch (Exception e) {
                        log.error("Failed to setup ChromeDriver. Please ensure Chrome/Chromium is installed.", e);
                        log.error(
                                "You can manually download ChromeDriver from: https://chromedriver.chromium.org/downloads");
                        throw new RuntimeException("ChromeDriver setup failed: " + e.getMessage() +
                                ". Please ensure Chrome/Chromium is installed on your system.", e);
                    }
                }
            }
        }
    }

    /**
     * Acquire a WebDriver from the pool or create a new one
     * Uses timeout mechanism to prevent infinite hangs on ChromeDriver creation
     */
    public WebDriver borrowDriver() {
        ensureInitialized();

        // Try to get from pool
        PooledDriver pooled = availableDrivers.poll();

        // Check if expired
        if (pooled != null && pooled.isExpired()) {
            log.debug("Pooled driver expired, closing it");
            closeDriver(pooled.driver);
            pooled = null;
        }

        // Return existing or create new
        if (pooled != null) {
            log.debug("Reusing pooled WebDriver (age: {}s)", (System.currentTimeMillis() - pooled.createdAt) / 1000);
            return pooled.driver;
        }

        // Create new if under limit
        if (activeDriverCount.get() < MAX_POOL_SIZE) {
            WebDriver driver = createDriverWithTimeout();
            activeDriverCount.incrementAndGet();
            log.info("Created new WebDriver. Active count: {}/{}", activeDriverCount.get(), MAX_POOL_SIZE);
            return driver;
        }

        // Wait for one to become available (or timeout)
        log.warn("WebDriver pool exhausted, waiting for available driver...");
        int attempts = 0;
        while (attempts < 30) { // 30 seconds max wait
            try {
                Thread.sleep(1000);
                pooled = availableDrivers.poll();
                if (pooled != null) {
                    if (pooled.isExpired()) {
                        closeDriver(pooled.driver);
                        continue;
                    }
                    log.debug("Got available driver after waiting");
                    return pooled.driver;
                }
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for WebDriver", e);
            }
        }

        throw new RuntimeException("Failed to acquire WebDriver - pool exhausted and timeout reached");
    }

    /**
     * Create WebDriver with timeout to prevent infinite hangs
     */
    private WebDriver createDriverWithTimeout() {
        final int TIMEOUT_SECONDS = 120; // 2 minute timeout

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<WebDriver> future = executor.submit(this::createDriver);

        try {
            log.info("Creating WebDriver with {}-second timeout", TIMEOUT_SECONDS);
            return future.get(TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            log.error("ChromeDriver creation timed out after {} seconds", TIMEOUT_SECONDS);
            throw new RuntimeException("ChromeDriver creation timed out after " + TIMEOUT_SECONDS + " seconds. " +
                    "This usually indicates Chrome is not properly installed or there are permission issues.", e);
        } catch (Exception e) {
            log.error("Error creating ChromeDriver: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create ChromeDriver: " + e.getMessage(), e);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Return a WebDriver to the pool for reuse
     * Enhanced with aggressive memory cleanup to reduce Chrome memory footprint
     */
    public void returnDriver(WebDriver driver) {
        if (driver == null)
            return;

        try {
            // Aggressive memory cleanup before returning to pool
            log.debug("Cleaning WebDriver before returning to pool...");

            // Clear cookies
            driver.manage().deleteAllCookies();

            // Navigate to blank page to free memory
            driver.get("about:blank");

            // Execute JavaScript to clear cache and storage
            if (driver instanceof org.openqa.selenium.JavascriptExecutor) {
                try {
                    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                            "window.localStorage.clear(); " +
                                    "window.sessionStorage.clear(); " +
                                    "window.name = '';");
                    log.debug("Cleared browser storage");
                } catch (Exception e) {
                    log.debug("Could not clear storage: {}", e.getMessage());
                }
            }

            availableDrivers.offer(new PooledDriver(driver));
            log.debug("WebDriver returned to pool (cleaned). Available: {}, Active: {}",
                    availableDrivers.size(), activeDriverCount.get());
        } catch (Exception e) {
            log.warn("Failed to clean driver before returning to pool, closing it", e);
            closeDriver(driver);
            activeDriverCount.decrementAndGet();
            log.debug("Driver closed due to cleanup failure. Active count: {}", activeDriverCount.get());
        }
    }

    /**
     * Create a new WebDriver with stealth options
     * Enhanced for AWS EC2/Elastic Beanstalk environments
     */
    private WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();

        // Try to find Chrome binary (different locations on different systems)
        String chromeBinary = findChromeBinary();
        if (chromeBinary != null) {
            log.info("Using Chrome binary at: {}", chromeBinary);
            options.setBinary(chromeBinary);
        }

        // Critical flags for headless Chrome on EC2/Linux without display
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox"); // Required for Docker/EC2
        options.addArguments("--disable-dev-shm-usage"); // Overcome limited resource problems
        options.addArguments("--disable-gpu"); // Applicable to windows os only but doesn't hurt on Linux

        // Memory optimization flags - AGGRESSIVE memory reduction
        options.addArguments("--disable-software-rasterizer");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-setuid-sandbox");
        options.addArguments("--single-process"); // Critical for AWS - prevents hanging
        options.addArguments("--disable-background-networking");
        options.addArguments("--disable-default-apps");
        options.addArguments("--disable-sync");
        options.addArguments("--disable-translate");
        options.addArguments("--metrics-recording-only");
        options.addArguments("--mute-audio");
        options.addArguments("--no-first-run");
        options.addArguments("--safebrowsing-disable-auto-update");

        // Additional memory pressure reduction
        options.addArguments("--disable-background-timer-throttling");
        options.addArguments("--disable-backgrounding-occluded-windows");
        options.addArguments("--disable-breakpad"); // Disable crash reporting
        options.addArguments("--disable-component-extensions-with-background-pages");
        options.addArguments("--disable-features=TranslateUI,BlinkGenPropertyTrees");
        options.addArguments("--disable-ipc-flooding-protection");
        options.addArguments("--disable-renderer-backgrounding");
        options.addArguments("--enable-features=NetworkService,NetworkServiceInProcess");
        options.addArguments("--force-color-profile=srgb");
        options.addArguments("--hide-scrollbars");
        options.addArguments("--disable-hang-monitor");
        options.addArguments("--disable-client-side-phishing-detection");
        options.addArguments("--disable-component-update");
        options.addArguments("--disable-domain-reliability");

        // Memory limits
        options.addArguments("--js-flags=--max-old-space-size=512"); // Limit JS heap to 512MB
        options.addArguments("--memory-pressure-off"); // Disable memory pressure warnings

        // Window size
        options.addArguments("--window-size=1920,1080");

        // Stability on cloud
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-blink-features=AutomationControlled");

        // Anti-detection
        options.setExperimentalOption("excludeSwitches", Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        // Performance and memory optimizations
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("profile.default_content_setting_values.images", 2); // Don't load images
        prefs.put("profile.managed_default_content_settings.images", 2);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("download.prompt_for_download", false);
        prefs.put("download.default_directory", "/dev/null");
        prefs.put("profile.default_content_settings.popups", 2); // Block popups
        prefs.put("profile.default_content_setting_values.automatic_downloads", 2); // Block auto downloads
        prefs.put("profile.content_settings.exceptions.automatic_downloads.*.setting", 2);
        prefs.put("safebrowsing.enabled", false); // Disable safe browsing
        prefs.put("safebrowsing.disable_download_protection", true);
        prefs.put("profile.default_content_setting_values.media_stream", 2); // Block media
        prefs.put("profile.default_content_setting_values.media_stream_mic", 2);
        prefs.put("profile.default_content_setting_values.media_stream_camera", 2);
        prefs.put("profile.default_content_setting_values.geolocation", 2); // Block geolocation
        options.setExperimentalOption("prefs", prefs);

        // Random user agent from realistic pool
        String userAgent = USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
        options.addArguments("--user-agent=" + userAgent);

        // Additional stealth
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-web-security");
        options.addArguments("--disable-features=IsolateOrigins,site-per-process");
        options.addArguments("--allow-running-insecure-content");
        options.addArguments("--disable-features=VizDisplayCompositor");

        // Proxy support via environment variables
        try {
            String proxyUrl = System.getenv("HTTPS_PROXY");
            if (proxyUrl == null || proxyUrl.isBlank()) proxyUrl = System.getenv("HTTP_PROXY");
            if (proxyUrl != null && !proxyUrl.isBlank()) {
                log.info("Using proxy for Selenium: {}", proxyUrl);
                // Prefer chrome arg form which supports scheme
                options.addArguments("--proxy-server=" + proxyUrl);
            }
        } catch (Exception e) {
            log.warn("Failed to configure proxy for Selenium: {}", e.getMessage());
        }

        // Page load strategy
        options.setPageLoadStrategy(org.openqa.selenium.PageLoadStrategy.EAGER); // Changed from NORMAL to EAGER

        log.info("Creating ChromeDriver with user-agent: {}", userAgent);
        log.debug("Chrome options: {}", options.asMap());

        try {
            log.info("Attempting to start ChromeDriver...");
            long startTime = System.currentTimeMillis();

            ChromeDriver driver = new ChromeDriver(options);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("ChromeDriver started successfully in {}ms", elapsedTime);

            // Set timeouts - reduced to prevent stalling
            driver.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
            driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(45));
            driver.manage().timeouts().scriptTimeout(java.time.Duration.ofSeconds(20));

            // Execute stealth scripts to hide automation
            try {
                // Override the navigator.webdriver property
                driver.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

                // Override the navigator.plugins to appear more realistic
                driver.executeScript(
                        "Object.defineProperty(navigator, 'plugins', {" +
                                "  get: () => [1, 2, 3, 4, 5]" +
                                "});");

                // Override navigator.languages
                driver.executeScript(
                        "Object.defineProperty(navigator, 'languages', {" +
                                "  get: () => ['en-US', 'en']" +
                                "});");

                log.debug("Applied stealth JavaScript overrides");
            } catch (Exception e) {
                log.warn("Failed to apply some stealth scripts: {}", e.getMessage());
            }

            log.info("ChromeDriver fully initialized and ready");
            return driver;
        } catch (Exception e) {
            log.error("Failed to create ChromeDriver. Chrome path: {}", findChromeBinary(), e);
            log.error("Exception type: {}", e.getClass().getName());
            log.error("Exception message: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("Caused by: {} - {}", e.getCause().getClass().getName(), e.getCause().getMessage());
            }
            throw new RuntimeException("Failed to create ChromeDriver: " + e.getMessage(), e);
        }
    }

    /**
     * Find Chrome binary location (varies by system)
     */
    private String findChromeBinary() {
        String[] possiblePaths = {
                "/usr/bin/google-chrome",
                "/usr/bin/google-chrome-stable",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
        };

        for (String path : possiblePaths) {
            java.io.File file = new java.io.File(path);
            if (file.exists() && file.canExecute()) {
                return path;
            }
        }

        log.warn("Could not find Chrome binary in standard locations, relying on PATH");
        return null; // Let ChromeDriver find it via PATH
    }

    /**
     * Close a specific driver
     */
    private void closeDriver(WebDriver driver) {
        try {
            driver.quit();
        } catch (Exception e) {
            log.warn("Error closing WebDriver", e);
        }
    }

    /**
     * Shutdown all drivers in the pool
     */
    public void shutdown() {
        log.info("Shutting down WebDriver pool...");

        PooledDriver pooled;
        int closed = 0;
        while ((pooled = availableDrivers.poll()) != null) {
            closeDriver(pooled.driver);
            closed++;
        }

        log.info("Closed {} pooled WebDrivers. Active count was: {}", closed, activeDriverCount.get());
        activeDriverCount.set(0);
    }
}
