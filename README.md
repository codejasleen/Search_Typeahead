# Search Typeahead System

A backend-focused search typeahead (autocomplete) system that balances low-latency reads with database-friendly write throughput, featuring a distributed cache and trending search boosts.

## Architecture Diagram

```
         React UI (:5173)
              │
     GET /suggest  POST /search
     GET /trending GET /cache/debug
              │
       Spring Boot (:8080)
              │
     SearchController
     CacheController
              │
     SuggestionService
              │
     BatchService          DistributedCache
         │                      │
     H2 Database           Redis Cache
  (query → count)      (prefix → top-10)
  Source of Truth    Consistent Hash Ring
                     ┌────┬────┐
                   node-a node-b node-c
```

## Technology Stack

- **Backend**: Java 17 (Spring Boot 3.2.5) with Spring Data JPA and Spring Data Redis
- **Frontend**: React + Vite (Vanilla CSS, custom debounced hooks)
- **Database (Source of Truth)**: H2 in-memory relational database
- **Distributed Cache**: Redis (Dockerized), sharded across 3 logical cache nodes (`node-a`, `node-b`, `node-c`) via consistent hashing
- **Dataset**: Python-generated corpus of 100,000+ unique queries following Zipf's Law distribution

---

## Prerequisites

- Java 17 or higher
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose
- Python 3.x

---

## How to Run (Step-by-Step)

### 1. Generate Dataset
Navigate to the dataset directory and run the generator script:
```bash
cd dataset
python generate_dataset.py
```
This generates a `seed_queries.csv` containing ~100k+ unique queries and their search counts.

### 2. Start Redis Cache
Start the sharded cache instance in Docker:
```bash
docker-compose up -d
```
This runs a Redis instance on `localhost:6379`.

### 3. Start Backend Application
Navigate to the backend directory and run the Spring Boot app:
```bash
cd backend
mvn spring-boot:run
```
Upon startup, `DataSeeder.java` reads `seed_queries.csv`, populates the H2 database, and pre-builds the prefix-to-suggestion map, distributing items across the three Redis cache nodes using consistent hashing.

### 4. Start Frontend Client
Navigate to the frontend directory, install dependencies, and launch the dev server:
```bash
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## API Documentation

### 1. Suggest API
Retrieve top-10 suggestions starting with the prefix.
* **URL**: `/api/suggest`
* **Method**: `GET`
* **Params**: `q=[prefix]`
* **Response**:
  ```json
  [
    { "query": "iphone", "score": 500000 },
    { "query": "iphone 15 pro", "score": 24000 }
  ]
  ```

### 2. Search Submission API
Record a new search query (enqueued in batch service for aggregation).
* **URL**: `/api/search`
* **Method**: `POST`
* **Body**: `{"query": "iphone 16"}`
* **Response**:
  ```json
  {
    "message": "Searched",
    "query": "iphone 16"
  }
  ```

### 3. Trending Searches API
Get top trending searches, including recent search boosts.
* **URL**: `/api/trending`
* **Method**: `GET`
* **Params**: `limit=[int]` (default 10)
* **Response**:
  ```json
  [
    { "query": "rtx 4090 specs", "score": 105000 },
    { "query": "iphone 16", "score": 98000 }
  ]
  ```

### 4. Cache Debug API
Inspect which Redis node contains the prefix data and verify hit/miss behavior.
* **URL**: `/api/cache/debug`
* **Method**: `GET`
* **Params**: `prefix=[prefix]`
* **Response**:
  ```json
  {
    "prefix": "iph",
    "cacheNode": "node-b",
    "hit": true,
    "suggestions": [...]
  }
  ```

---

## Design Decisions

1. **HashMap vs. Trie**
   * **O(1) Lookup**: A HashMap offers direct key lookup, bypassing $O(L)$ pointer traversals in a Trie.
   * **Simpler Distributed Caching**: Distributing prefix strings sharded by hash is much easier than partitioning a Trie tree structure across remote nodes.
   * **Atomic Invalidation**: Invalidating cache records on flush is as simple as replacing single Redis hash fields.

2. **Separation of SQL and Redis**
   * **H2 (SQL)** serves as the persistent source of truth holding raw search query frequencies.
   * **Redis (Cache)** acts as a read-serving layer keeping ready-to-serve top-10 list formats mapped directly to input prefixes.

3. **Consistent Hashing**
   * **Virtual Nodes (150 per node)** are mapped on a TreeMap ring. This prevents key distribution imbalances (hotspots) and handles cluster growth/shrinkage gracefully with minimal re-sharding.

4. **Batch Writes**
   * High-throughput search calls are enqueued instantly into memory via a `ConcurrentLinkedQueue`.
   * A scheduled worker (`BatchService`) drains and aggregates the queries every 5 seconds, executing single-batch SQL merge/upsert operations to limit database connection overhead.

---

## Complexity Analysis

| Operation | Time Complexity | Details |
|---|---|---|
| **Autocomplete Suggest** | $O(1)$ | Direct HGET key-value fetch |
| **Search Submission** | $O(1)$ | ConcurrentLinkedQueue insert |
| **Batch Aggregation** | $O(N)$ | Aggregate query map ($N$ is queue size) |
| **Consistent Hash Ring Routing** | $O(\log M)$ | ceilingEntry lookup on TreeMap ($M$ is virtual nodes count) |
