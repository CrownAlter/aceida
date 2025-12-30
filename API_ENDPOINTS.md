# Acedia API Endpoints

Base URL when running on EC2: `http://your-ec2-public-ip:5000`

Example EC2 IP: `http://54.123.45.67:5000`

---

## 📚 Novel Endpoints

### 1. **Scrape a Novel**
Start scraping a novel from a URL.

**Endpoint:** `POST /api/novels/scrape`

**EC2 Example:**
```bash
curl -X POST http://54.123.45.67:5000/api/novels/scrape \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://novelbin.me/novel-book/shadow-slave"
  }'
```

**Request Body Options:**

**Option A: Scrape all chapters**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave"
}
```

**Option B: Scrape single chapter**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterNumber": 15
}
```

**Option C: Scrape first N chapters**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterLimit": 5
}
```

**Option D: Scrape chapter range**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 10,
  "chapterEnd": 20
}
```

**Option E: Scrape from chapter X onwards**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "chapterStart": 50
}
```

**Response:**
```json
{
  "novelId": "123e4567-e89b-12d3-a456-426614174000",
  "title": "Shadow Slave",
  "message": "Novel scraped successfully",
  "status": "COMPLETED",
  "totalChapters": 2000,
  "downloadedChapters": 5
}
```

---

### 2. **List All Novels**
Get a paginated list of all scraped novels.

**Endpoint:** `GET /api/novels`

**EC2 Examples:**

Default (page 0, 20 items, sorted by last modified):
```bash
curl http://54.123.45.67:5000/api/novels
```

With pagination:
```bash
curl "http://54.123.45.67:5000/api/novels?page=0&size=10"
```

With custom sorting:
```bash
curl "http://54.123.45.67:5000/api/novels?page=0&size=20&sort=title,asc"
```

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 20) - Items per page
- `sort` (default: dateLastModified,desc) - Sort field and direction

**Response:**
```json
{
  "content": [
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "title": "Shadow Slave",
      "author": "Guiltythree",
      "siteName": "novelbin",
      "url": "https://novelbin.me/novel-book/shadow-slave",
      "genre": "Fantasy, Action",
      "description": "Growing up in poverty...",
      "status": "Ongoing",
      "totalChapters": 2000,
      "downloadedChapters": 50,
      "dateCreated": "2024-01-15T10:30:00",
      "dateLastModified": "2024-01-20T14:45:00",
      "saveLocation": "/var/app/acedia-storage/novels/shadow-slave.epub",
      "fileType": "EPUB",
      "lastChapter": false,
      "currentChapter": 50
    }
  ],
  "pageable": {...},
  "totalPages": 5,
  "totalElements": 100
}
```

---

### 3. **Search Novels**
Search for novels by title.

**Endpoint:** `GET /api/novels/search`

**EC2 Example:**
```bash
curl "http://54.123.45.67:5000/api/novels/search?query=shadow"
```

With pagination:
```bash
curl "http://54.123.45.67:5000/api/novels/search?query=overlord&page=0&size=10"
```

**Query Parameters:**
- `query` (required) - Search term
- `page` (default: 0) - Page number
- `size` (default: 20) - Items per page

**Response:** Array of novel objects (same structure as List All Novels)

---

### 4. **Get Novel Details**
Get detailed information about a specific novel, including all chapters.

**Endpoint:** `GET /api/novels/{id}`

**EC2 Example:**
```bash
curl http://54.123.45.67:5000/api/novels/123e4567-e89b-12d3-a456-426614174000
```

**Response:**
```json
{
  "novel": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "title": "Shadow Slave",
    "author": "Guiltythree",
    ...
  },
  "chapters": [
    {
      "id": "456e4567-e89b-12d3-a456-426614174001",
      "title": "Chapter 1: Nightmare Begins",
      "url": "https://novelbin.me/novel-book/shadow-slave/chapter-1",
      "number": 1.0,
      "dateCreated": "2024-01-15T10:35:00",
      "hasContent": true,
      "pageCount": 1
    },
    {
      "id": "789e4567-e89b-12d3-a456-426614174002",
      "title": "Chapter 2: The Spell",
      "url": "https://novelbin.me/novel-book/shadow-slave/chapter-2",
      "number": 2.0,
      "dateCreated": "2024-01-15T10:36:00",
      "hasContent": true,
      "pageCount": 1
    }
  ]
}
```

---

### 5. **Update Novel by ID**
Fetch new chapters for an existing novel using its ID.

**Endpoint:** `PUT /api/novels/{id}/update`

**EC2 Examples:**

Update all new chapters:
```bash
curl -X PUT http://54.123.45.67:5000/api/novels/123e4567-e89b-12d3-a456-426614174000/update
```

Update with chapter limit:
```bash
curl -X PUT "http://54.123.45.67:5000/api/novels/123e4567-e89b-12d3-a456-426614174000/update?chapterLimit=5"
```

**Query Parameters:**
- `chapterLimit` (optional) - Limit number of new chapters to fetch

**Response:** Same as scrape response

---

### 6. **Update Novel by URL**
Fetch new chapters for an existing novel using its URL (no need to know the ID!).

**Endpoint:** `PUT /api/novels/update`

**EC2 Examples:**

**Update all new chapters:**
```bash
curl -X PUT "http://54.123.45.67:5000/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave"
```

**Update specific chapter:**
```bash
curl -X PUT "http://54.123.45.67:5000/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterNumber=15"
```

**Update with limit:**
```bash
curl -X PUT "http://54.123.45.67:5000/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterLimit=5"
```

**Update chapter range:**
```bash
curl -X PUT "http://54.123.45.67:5000/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterStart=10&chapterEnd=20"
```

**Query Parameters:**
- `url` (required) - Novel URL
- `chapterNumber` (optional) - Single chapter to update
- `chapterLimit` (optional) - Limit number of chapters
- `chapterStart` (optional) - Start from chapter number
- `chapterEnd` (optional) - End at chapter number

**Response:** Same as scrape response

---

### 7. **Delete Novel**
Delete a novel and all its chapters.

**Endpoint:** `DELETE /api/novels/{id}`

**EC2 Example:**
```bash
curl -X DELETE http://54.123.45.67:5000/api/novels/123e4567-e89b-12d3-a456-426614174000
```

**Response:** 204 No Content (on success)

---

### 8. **Test CSS Selectors**
Test CSS selectors against a URL to discover the right selectors for new sites.

**Endpoint:** `POST /api/novels/test-selectors`

**EC2 Example:**
```bash
curl -X POST http://54.123.45.67:5000/api/novels/test-selectors \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://novelbin.me/novel-book/shadow-slave",
    "useSelenium": false,
    "selectors": [
      {
        "name": "novelTitle",
        "selector": "h3.title",
        "selectAll": false
      },
      {
        "name": "chapterLinks",
        "selector": "ul.list-chapter a",
        "attribute": "href",
        "selectAll": true
      },
      {
        "name": "novelAuthor",
        "selector": "ul.info li:has(h3:contains(Author:)) a",
        "selectAll": false
      }
    ]
  }'
```

**Request Body:**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "useSelenium": false,
  "selectors": [
    {
      "name": "novelTitle",
      "selector": "h3.title",
      "selectAll": false,
      "attribute": null
    }
  ]
}
```

**Response:**
```json
{
  "url": "https://novelbin.me/novel-book/shadow-slave",
  "success": true,
  "results": [
    {
      "name": "novelTitle",
      "success": true,
      "value": "Shadow Slave",
      "count": 1,
      "errorMessage": null
    },
    {
      "name": "chapterLinks",
      "success": true,
      "value": "https://novelbin.me/novel-book/shadow-slave/chapter-1",
      "count": 2000,
      "errorMessage": null
    }
  ],
  "errorMessage": null
}
```

---

## 🏥 Health Check Endpoint

### Health Check
Check if the application is running and healthy.

**Endpoint:** `GET /actuator/health`

**EC2 Example:**
```bash
curl http://54.123.45.67:5000/actuator/health
```

**Response:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 10737418240,
        "free": 5368709120,
        "threshold": 10485760,
        "path": "/var/app/.",
        "exists": true
      }
    },
    "livenessState": {
      "status": "UP"
    },
    "readinessState": {
      "status": "UP"
    }
  }
}
```

---

## 📝 Quick Test Commands

### Full Workflow Example on EC2:

```bash
# Replace with your EC2 IP
EC2_IP="54.123.45.67"
BASE_URL="http://${EC2_IP}:5000"

# 1. Check health
curl "${BASE_URL}/actuator/health"

# 2. Scrape a novel (first 5 chapters)
curl -X POST "${BASE_URL}/api/novels/scrape" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://novelbin.me/novel-book/shadow-slave",
    "chapterLimit": 5
  }'

# 3. List all novels
curl "${BASE_URL}/api/novels"

# 4. Search for a novel
curl "${BASE_URL}/api/novels/search?query=shadow"

# 5. Get novel details (replace {id} with actual UUID from step 2)
curl "${BASE_URL}/api/novels/{id}"

# 6. Update novel with 5 more chapters
curl -X PUT "${BASE_URL}/api/novels/update?url=https://novelbin.me/novel-book/shadow-slave&chapterLimit=5"

# 7. Delete novel (replace {id} with actual UUID)
curl -X DELETE "${BASE_URL}/api/novels/{id}"
```

---

## 🔧 Notes

1. **Port**: Application runs on port **5000** in production mode
2. **Content-Type**: All POST/PUT requests require `Content-Type: application/json` header
3. **Novel URLs**: Currently supports `novelbin.me` domain
4. **File Storage**: Generated files are stored in `/var/app/acedia-storage/` on the server
5. **Database**: All novel metadata and chapters are stored in PostgreSQL

---

## 🚨 Common Issues

**Connection Refused:**
- Check EC2 security group allows inbound on port 5000
- Verify application is running: `curl http://localhost:5000/actuator/health` from EC2

**404 Not Found:**
- Ensure you're using the correct base path: `/api/novels`
- Check application logs for errors

**Database Connection Error:**
- Verify RDS security group allows connections from EC2
- Check environment variables are set correctly
