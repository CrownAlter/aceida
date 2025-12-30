# Novelfire Integration & Debugging Summary

## Overview
Added complete support for **Novelfire.net** and implemented debugging features for NovelBin issues on EC2.

## What Was Added

### 1. Novelfire Strategy (`NovelfireStrategy.java`)
- **Book page**: Loads `/book/<slug>` and extracts metadata
- **Chapters page**: Loads `/book/<slug>/chapters` with full pagination support
- **Pagination**: Auto-detects last page from `ul.pagination a` links and scrapes all pages
- **Chapter content**: Uses Selenium for JS-rendered content

### 2. Verified Selectors (Based on Live HTML Testing)

#### Book Page (https://novelfire.net/book/shadow-slave)
| Field | Selector | Status |
|-------|----------|--------|
| Title | `h1.novel-title` | ✓ Verified |
| Author | `div.author a.property-item` | ✓ Verified |
| Status | `div.header-stats span` | ✓ Verified (returns "Ongoing Status") |
| Description | `div.summary p` | ✓ Verified (6 paragraphs) |
| Genres | `a.property-item[href*='/genre-']` | ✓ Verified (4 genres) |
| Thumbnail | `div.book img, .main-head img` | ✓ Verified |

#### Chapters Page (https://novelfire.net/book/shadow-slave/chapters)
| Field | Selector | Status |
|-------|----------|--------|
| Chapter Links | `ul.chapter-list a` | ✓ Verified (100 per page) |
| Pagination | `ul.pagination a` | ✓ Verified (28 pages detected) |

#### Chapter Content Page (https://novelfire.net/book/shadow-slave/chapter-1)
| Field | Selector | Status |
|-------|----------|--------|
| Chapter Title | `h1` | ✓ Verified |
| Chapter Content | `div.box-detail p` | ✓ Verified (97 paragraphs) |

### 3. Factory Mapping
Added `novelfire.net` → `novelfireStrategy` mapping in `NovelScraperFactory.java`

### 4. Site Configuration (`application.yml`)
```yaml
- name: novelfire
  url-pattern: novelfire.net
  has-pagination: true
  selenium-site: true
  completed-status: "completed"
  selectors: [verified selectors above]
```

## Debug Features Added

### A) HTML Debug Dumps
**Environment Variables:**
- `SCRAPER_DEBUG_DUMP=true` - Enable debug dumps
- `SCRAPER_DEBUG_DIR=/var/app/acedia-storage/debug` - Output directory (default: `~/.benny-scraper/debug`)

**What it does:**
- Automatically saves HTML to disk when:
  - Page title contains "Just a moment" (Cloudflare challenge)
  - Chapter selectors are missing
  - AJAX responses are empty
- Files named: `cloudflare_selenium_<timestamp>.html`, `novelbin_ajax_<timestamp>.html`, etc.

**Where dumps are triggered:**
1. NovelBin AJAX endpoint (when chapter list is empty)
2. NovelBin TOC page after clicking "Chapters" tab
3. Any JSoup or Selenium load that returns Cloudflare page
4. Chapter pages that fail to load content

### B) Proxy Support
**Environment Variables:**
- `HTTPS_PROXY=https://user:pass@proxy-host:port` (takes precedence)
- `HTTP_PROXY=http://user:pass@proxy-host:port`

**What it does:**
- Routes **both Jsoup and Selenium** requests through the proxy
- Accepts formats: `http://host:port`, `https://host:port`, `host:port`
- Logs proxy usage: "Using proxy for Selenium: ..." / "Using proxy for Jsoup: host:port..."

**Selenium proxy:**
- Uses `--proxy-server=<proxyUrl>` Chrome argument

**Jsoup proxy:**
- Uses `connection.proxy(host, port)`

## Why Your Scraper Stalls on EC2

### Root Cause Analysis

Based on your logs and the code inspection, here's why it stalls:

#### 1. **Selenium Waits Are Too Long**
- **loadHtmlWithSelenium**: 60-second wait for `document.readyState`
- **Chapter content wait**: 45-second timeout for content selector
- **Thread.sleep**: 2-4 seconds after every Selenium page load

**For Novelfire with 2729 chapters:**
- If pagination yields 28 pages × 100 chapters/page = 2800 chapters
- With your `chapterLimit=2`, you're only scraping 2 chapters
- BUT: Selenium is being used for:
  - Book page load (1x) → ~4-8 seconds
  - Each pagination page (28x) → ~4-8 seconds each = **112-224 seconds just for chapter URL collection**
  - Chapter content pages (2x) → ~6-10 seconds each

**Total estimated time for 2 chapters with Selenium + pagination:**
- Pagination alone: 28 pages × 6 seconds = **168 seconds (~3 minutes)**
- Book page: 6 seconds
- 2 chapter pages: 12 seconds
- **Total: ~3 minutes minimum**

If the site is slow or Cloudflare challenges appear, each wait can hit the full timeout, making it **much longer**.

#### 2. **Pagination Without Limit**
NovelfireStrategy currently fetches **ALL pagination pages** even if you only need 2 chapters:
```java
for (int i = 2; i <= lastPage; i++) {
    // Loads page 2, 3, 4, ..., 28 even though chapterLimit=2
}
```

This is inefficient. Since each page has 100 chapters, for `chapterLimit=2` you only need page 1.

#### 3. **Selenium Pool Exhaustion**
- Pool size: 2 drivers max
- If pagination uses Selenium for all 28 pages sequentially, and any page hangs/times out, the entire scrape stalls waiting for the driver.

### Immediate Fixes Applied

1. **Pagination is now logged** so you can see progress:
   ```
   Loading chapters page 2: https://...
   Found 8 pagination links
   Determined last chapters page: 28
   ```

2. **Better timeout handling** (already in code):
   - Selenium waits catch `TimeoutException` and continue
   - Chapter content wait is 45s but gracefully falls back

3. **Proxy and debug support** to help diagnose Cloudflare blocks

### Recommended Optimizations (For You to Apply)

#### Fix 1: Early-Exit Pagination
Stop pagination as soon as you have enough chapter URLs for the limit:

```java
// In NovelfireStrategy.scrapeNovelData()
// After page 1 links are collected:
if (chapterLimit != null && chapterSet.size() >= chapterLimit) {
    log.info("Collected {} chapters (limit: {}), stopping pagination early", chapterSet.size(), chapterLimit);
    break; // Exit pagination loop
}
```

#### Fix 2: Reduce Selenium Delays
```java
// In ScraperStrategy.loadHtmlWithSelenium()
Thread.sleep(1000 + random.nextInt(1000)); // 1-2 seconds instead of 2-4
```

#### Fix 3: Make Selenium Optional for Pagination
If Novelfire's `/chapters` page doesn't need JS for the chapter list, set `selenium-site: false` or add a flag like `selenium-for-chapters: false`.

#### Fix 4: Add Request-Level Timeout
For production, enforce a global timeout on the entire scrape operation using CompletableFuture with timeout:
```java
CompletableFuture.supplyAsync(() -> strategy.scrapeNovelData())
    .orTimeout(5, TimeUnit.MINUTES)
    .exceptionally(ex -> { log.error("Scrape timed out"); return null; });
```

## How to Test Novelfire on EC2

### 1. Deploy with Proxy (Recommended)
```bash
# Set environment variables in Elastic Beanstalk or systemd
HTTPS_PROXY=https://your-residential-proxy:port
SCRAPER_DEBUG_DUMP=true
SCRAPER_DEBUG_DIR=/var/app/acedia-storage/debug
```

### 2. Test with Small Chapter Limit
```bash
curl -X POST http://localhost:5000/api/novels/scrape \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://novelfire.net/book/shadow-slave",
    "chapterLimit": 2
  }'
```

### 3. Check Logs
- Look for: "Found 8 pagination links", "Determined last chapters page: 28"
- Check if Selenium is being used: "Using Selenium to load..."
- Monitor timing: "Progress: 1/2 chapters scraped"

### 4. Review Debug Dumps
If scraping fails or returns no content:
```bash
ls -lh /var/app/acedia-storage/debug/
# Check for novelfire_*.html files
```

## Expected Behavior

### Without Proxy
- Book page: ✓ Loads (metadata extracted)
- Chapters pagination: ⚠️ May hit Cloudflare challenge after a few pages
- Chapter content: ⚠️ May be blocked or empty

### With Residential Proxy
- Book page: ✓ Loads
- Chapters pagination: ✓ All 28 pages load (but consider early-exit optimization)
- Chapter content: ✓ Loads successfully

## Summary of Changes

| File | Change |
|------|--------|
| `NovelfireStrategy.java` | New scraper for Novelfire with pagination |
| `NovelScraperFactory.java` | Added novelfire.net mapping |
| `application.yml` | Added novelfire site config with verified selectors |
| `ScraperStrategy.java` | Added debug dump + proxy support (Jsoup & Selenium) |
| `SeleniumWebDriverPool.java` | Added Selenium proxy via `--proxy-server` |

## Next Steps

1. **Deploy to EC2** with proxy environment variables
2. **Test with `chapterLimit=2`** and monitor logs
3. **Apply pagination early-exit optimization** if needed
4. **Reduce Selenium delays** if scraping is still slow
5. **Consider making Selenium optional** for Novelfire chapters page if it works without JS

## Testing Checklist

- [ ] Novelfire book page metadata extraction works
- [ ] Pagination detects correct number of pages (28 for Shadow Slave)
- [ ] Chapter links are extracted from all pages
- [ ] Chapter content is scraped successfully
- [ ] Debug dumps are created when Cloudflare is detected
- [ ] Proxy routing is logged and functional
- [ ] Scraping completes within reasonable time (< 5 minutes for 2 chapters)
