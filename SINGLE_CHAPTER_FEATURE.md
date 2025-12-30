# Single Chapter Download Feature ✨

## Quick Answer

**Yes! You can now download just 1 specific chapter!**

---

## 🎯 How to Download a Single Chapter

### Method 1: Using JSON Body (POST)
```powershell
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 15
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"
```

### Method 2: Using Query Parameters (PUT - Update)
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterNumber=15" `
    -Method Put
```

---

## 💡 Common Scenarios

### Scenario 1: Preview a Chapter Before Committing
```powershell
# Check out chapter 50 to see if the story gets better
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 50
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" -Method Post -Body $body -ContentType "application/json"
```

### Scenario 2: Fill a Single Missing Chapter
```powershell
# You have 1-10 and 12-20, but missing chapter 11
Invoke-RestMethod -Uri "http://localhost:8080/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterNumber=11" -Method Put
```

### Scenario 3: Download a Famous Chapter
```powershell
# Friend says "You MUST read chapter 100, it's amazing!"
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 100
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" -Method Post -Body $body -ContentType "application/json"
```

### Scenario 4: Check Latest Chapter Without Downloading Everything
```powershell
# Novel has 2720 chapters, you just want to see the latest
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 2720
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" -Method Post -Body $body -ContentType "application/json"
```

---

## 🔥 All Methods Comparison

| Method | Parameter | Example | Downloads |
|--------|-----------|---------|-----------|
| **Single Chapter** | `chapterNumber: 15` | `{"chapterNumber": 15}` | Only chapter 15 |
| **Chapter Limit** | `chapterLimit: 5` | `{"chapterLimit": 5}` | Chapters 1-5 |
| **Chapter Range** | `chapterStart: 10, chapterEnd: 20` | `{"chapterStart": 10, "chapterEnd": 20}` | Chapters 10-20 |
| **From Chapter N** | `chapterStart: 50` | `{"chapterStart": 50}` | Chapter 50 to end |
| **Up to Chapter N** | `chapterEnd: 100` | `{"chapterEnd": 100}` | Chapters 1-100 |
| **All Chapters** | (none) | `{"url": "..."}` | All chapters |

---

## ⚙️ Technical Details

### Priority System
When multiple parameters are set, they follow this priority:

1. **`chapterNumber`** (HIGHEST) - Downloads only that single chapter
2. `chapterLimit` - Downloads chapters 1 to N
3. `chapterStart/chapterEnd` - Downloads range
4. Auto-update - Downloads all new chapters

### Example:
```json
{
  "chapterNumber": 15,  // This will be used
  "chapterLimit": 5,    // Ignored
  "chapterStart": 10,   // Ignored
  "chapterEnd": 20      // Ignored
}
```
**Result:** Only downloads chapter 15

---

## ✅ Smart Features

### 1. Duplicate Detection
If you already have the chapter, it won't re-download it:
```
You have: Chapter 15
Request: chapterNumber = 15
Result: "Chapter 15 is already downloaded" (skipped)
```

### 2. Validation
```
Total chapters: 2720
Request: chapterNumber = 3000
Result: Error - "Chapter number (3000) exceeds total chapters (2720)"
```

### 3. Failed Chapter Handling
If the chapter fails to load (timeout, etc.), it's skipped and logged—no database corruption.

---

## 📊 Response Format

```json
{
  "novelId": "3d437067-18e3-4d7a-baea-7e2bbf10e02b",
  "title": "Shadow Slave",
  "message": "Novel scraped successfully",
  "status": "COMPLETED",
  "totalChapters": 2720,
  "downloadedChapters": 1
}
```

Notice: `downloadedChapters: 1` - confirms only 1 chapter was downloaded.

---

## 🧪 Quick Test

```powershell
# Test: Download chapter 1
$body = @{
    url = "https://novelbin.me/novel-book/shadow-slave"
    chapterNumber = 1
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/novels/scrape" `
    -Method Post `
    -Body $body `
    -ContentType "application/json"

Write-Host "Downloaded chapters: $($response.downloadedChapters)"
# Expected output: Downloaded chapters: 1
```

---

## 📚 See Also

- **Full Documentation:** `CHAPTER_RANGE_FEATURE.md`
- **All Fixes:** `FINAL_FIXES.md`
- **Complete Analysis:** `ANALYSIS_AND_FIXES.md`

---

## ✨ Summary

**You can now download a single specific chapter using `chapterNumber`!**

**Quick Examples:**
- Download chapter 15: `{"url": "...", "chapterNumber": 15}`
- Update to add chapter 11: `PUT /api/novels/update?url=...&chapterNumber=11`

Enjoy precise chapter control! 📖🎯
