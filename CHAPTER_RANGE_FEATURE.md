# Chapter Selection Feature

## Overview

You can now scrape specific chapters, chapter ranges, or single chapters from novels! This gives you precise control over which chapters to download.

---

## 🎯 Available Options

### Option 1: Single Specific Chapter (NEW! 🎉)
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterNumber": 15
}
```
**Result:** Downloads only chapter 15

---

### Option 2: Scrape All Chapters (Default)
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave"
}
```
**Result:** Downloads all available chapters

---

### Option 3: Chapter Limit (First N Chapters)
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterLimit": 5
}
```
**Result:** Downloads chapters 1-5

---

### Option 4: Chapter Range (Start to End)
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 10,
  "chapterEnd": 20
}
```
**Result:** Downloads chapters 10-20 (inclusive)

---

### Option 5: From Chapter N to End
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 50
}
```
**Result:** Downloads chapter 50 onwards to the latest chapter

---

### Option 6: Up to Chapter N
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterEnd": 100
}
```
**Result:** Downloads chapters 1-100

---

## 📚 Complete API Reference

### 1. Scrape New Novel (POST)

**Endpoint:** `POST /api/novels/scrape`

**Request Body Examples:**

```powershell
# Example 1: Download single chapter 15
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 15
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"

# Example 2: Scrape chapters 10-20
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterStart = 10
    chapterEnd = 20
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"

# Example 3: Scrape from chapter 50 onwards
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterStart = 50
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"

# Example 4: Scrape first 5 chapters
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterLimit = 5
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"
```

---

### 2. Update Existing Novel (PUT with URL)

**Endpoint:** `PUT /api/novels/update?url={url}&chapterStart={start}&chapterEnd={end}`

**Examples:**

```powershell
# Example 1: Add single chapter 15 to existing novel
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterNumber=15" `
    -Method Put

# Example 2: Add chapters 5-10 to existing novel
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterStart=5&chapterEnd=10" `
    -Method Put

# Example 3: Add chapters 20 onwards
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterStart=20" `
    -Method Put

# Example 4: Update to chapter 15 (chapters 1-15)
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterLimit=15" `
    -Method Put

# Example 5: Just get the latest chapters (no parameters)
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave" `
    -Method Put
```

---

## 🔄 How It Works

### Priority System

When multiple parameters are provided, they are prioritized in this order:

1. **`chapterNumber`** (highest priority)
   - If set, downloads only that single chapter
   - Ignores all other parameters

2. **`chapterLimit`**
   - If set, always downloads chapters 1 to N
   - Ignores `chapterStart` and `chapterEnd`

3. **`chapterStart` / `chapterEnd`**
   - Downloads the specified range
   - Used if `chapterLimit` is not set

4. **Auto-update** (lowest priority - only for updates)
   - Downloads all new chapters since last update
   - Used if no parameters are set

### Smart Duplicate Detection

The system automatically skips chapters you already have:

```
Scenario: You have chapters 1-5, you request chapters 3-10
Result: Only chapters 6-10 are downloaded (3-5 already exist)
```

### Validation

- Chapter numbers must be positive integers
- `chapterStart` cannot be greater than `chapterEnd`
- If `chapterStart` exceeds total chapters, an error is thrown
- If range exceeds available chapters, it downloads up to the last available chapter

---

## 💡 Use Cases

### Use Case 1: Preview a Specific Chapter
You want to check if chapter 100 is worth reading:

```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterNumber": 100
}
```

### Use Case 2: Download Specific Arc
You want to read a specific story arc (e.g., chapters 100-150):

```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 100,
  "chapterEnd": 150
}
```

### Use Case 3: Fill Single Missing Chapter
You have chapters 1-10 and 12-20, but missing chapter 11:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=...&chapterNumber=11" -Method Put
```

### Use Case 4: Fill Missing Chapter Range
You have chapters 1-10 and 20-30, but missing 11-19:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=...&chapterStart=11&chapterEnd=19" -Method Put
```

### Use Case 5: Test Before Full Download
Try first 3 chapters to see if you like the novel:

```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterLimit": 3
}
```

Then later, download the rest:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=...&chapterStart=4" -Method Put
```

### Use Case 6: Catch Up to Specific Chapter
Friend recommends reading up to chapter 50:

```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterEnd": 50
}
```

### Use Case 7: Download Latest Chapters Only
Novel has 1000 chapters, you only want the latest 50:

```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 951
}
```

---

## 📊 Response Format

All endpoints return the same response structure:

```json
{
  "novelId": "3d437067-18e3-4d7a-baea-7e2bbf10e02b",
  "title": "Shadow Slave",
  "message": "Novel scraped successfully",
  "status": "COMPLETED",
  "totalChapters": 2720,
  "downloadedChapters": 11
}
```

**Fields:**
- `novelId`: UUID of the novel in the database
- `title`: Novel title
- `message`: Status message
- `status`: "COMPLETED" or "FAILED"
- `totalChapters`: Total chapters available on the site
- `downloadedChapters`: How many chapters you have in your database

---

## ⚠️ Important Notes

### 1. Chapter Numbers are 1-Based
```
chapterStart = 1  → First chapter
chapterStart = 10 → Tenth chapter
```

### 2. chapterEnd is Inclusive
```
chapterStart = 10, chapterEnd = 20
→ Downloads chapters 10, 11, 12, ..., 19, 20 (11 chapters total)
```

### 3. Existing Chapters are Skipped
The system won't re-download chapters you already have. It's safe to run the same request multiple times.

### 4. Failed Chapters are Skipped
If a chapter fails to load (timeout, etc.), it's skipped and not saved. See logs for details.

### 5. Priority: chapterNumber > chapterLimit > Range > Auto
```json
// This will ONLY use chapterNumber (downloads chapter 3):
{
  "chapterNumber": 3,   // Used
  "chapterLimit": 5,    // Ignored!
  "chapterStart": 10,   // Ignored!
  "chapterEnd": 20      // Ignored!
}

// This will ONLY use chapterLimit (downloads 1-5):
{
  "chapterLimit": 5,    // Used
  "chapterStart": 10,   // Ignored!
  "chapterEnd": 20      // Ignored!
}
```

---

## 🧪 Testing Examples

### Test 1: Initial Download with Range
```powershell
# Download chapters 1-3
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterStart = 1
    chapterEnd = 3
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post -Body $body -ContentType "application/json"

Write-Host "Downloaded chapters: $($response.downloadedChapters)"
```

### Test 2: Add More Chapters
```powershell
# Add chapters 4-6
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterStart=4&chapterEnd=6" `
    -Method Put
```

### Test 3: Verify in Database
```sql
SELECT 
    n.title,
    COUNT(c.id) as chapter_count,
    MIN(c.number) as first_chapter,
    MAX(c.number) as last_chapter
FROM novels n
LEFT JOIN chapters c ON c.novel_id = n.id
GROUP BY n.id, n.title;
```

Expected result: 6 chapters (1-6)

---

## 🔧 Technical Details

### Request DTO
```java
public class ScrapeRequestDto {
    private String url;            // Required
    private Integer chapterNumber; // Optional (1-based, single chapter)
    private Integer chapterLimit;  // Optional (chapters 1 to N)
    private Integer chapterStart;  // Optional (1-based, range start)
    private Integer chapterEnd;    // Optional (1-based, range end, inclusive)
}
```

### Validation Rules
- `url`: Must start with http:// or https://
- `chapterNumber`: Must be positive if set
- `chapterLimit`: Must be positive if set
- `chapterStart`: Must be positive if set
- `chapterEnd`: Must be positive if set
- `chapterStart` <= `chapterEnd` (if both set)
- `chapterNumber` cannot exceed total available chapters

### Error Messages
```json
// Invalid range
{
  "message": "Chapter start (50) cannot be greater than chapter end (20)",
  "status": "FAILED"
}

// Exceeds available chapters
{
  "message": "Chapter start (5000) exceeds total chapters (2720)",
  "status": "FAILED"
}

// Single chapter exceeds available
{
  "message": "Chapter number (3000) exceeds total chapters (2720)",
  "status": "FAILED"
}
```

---

## ✅ Summary

**What You Can Do Now:**
1. ✅ Download a single specific chapter (e.g., chapter 15) **NEW!**
2. ✅ Download specific chapter ranges (e.g., chapters 10-20)
3. ✅ Download from a specific chapter to the end
4. ✅ Download up to a specific chapter
5. ✅ Still use chapter limit for convenience (chapters 1-N)
6. ✅ Smart duplicate detection (skips existing chapters)
7. ✅ Works with both scrape (new) and update (existing) endpoints

**Try It Out:**
```powershell
# Quick test: Download single chapter
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 1
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post -Body $body -ContentType "application/json"

# Or download a range
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterStart = 1
    chapterEnd = 2
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post -Body $body -ContentType "application/json"
```

Enjoy your precise chapter control! 📚✨
