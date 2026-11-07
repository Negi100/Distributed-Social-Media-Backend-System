# BotGuard — Distributed Social Media Backend

A Spring Boot backend for a social media platform where **humans and AI bots** both post and comment. PostgreSQL stores the content. Redis handles the fast, concurrent parts: **bot guardrails** (rate limits, cooldowns, thread-depth caps), **post virality scoring**, and **batched notifications**, so a flood of bot replies can't spam users or take over a thread.

---

## Features

- **Posts & comments API.** Create posts, list them with their comments, and add nested comments.
- **Bot guardrails (Redis, atomic).**
  - **Depth cap:** bots can't reply deeper than level 20 in a thread.
  - **Per-post bot cap:** at most 100 bot comments per post (atomic `INCR`).
  - **Bot→human cooldown:** a given bot can interact with a given human's post only once every 10 minutes (`SET NX` with TTL).
  - A blocked request returns **`429 Too Many Requests`**.
- **Virality scoring.** Each post keeps a running score in Redis:

  | Interaction     | Points |
  |-----------------|-------:|
  | Bot reply       | +1     |
  | Human like      | +20    |
  | Human comment   | +50    |

- **Notification throttling.** The first bot interaction sends a push notification right away and starts a 15-minute cooldown. Later interactions inside that window are queued in a Redis list.
- **Notification sweeper.** A scheduled job, set to run every 5 minutes, collects each user's queued notifications into one summary ("Bot N interactions for User X") and clears the queue.

---

## Tech Stack

| Layer        | Technology                                  |
|--------------|---------------------------------------------|
| Language     | Java 21                                     |
| Framework    | Spring Boot 4.0.5 (Web MVC, Data JPA, Data Redis) |
| Database     | PostgreSQL                                  |
| Cache / Locks| Redis (`StringRedisTemplate`)               |
| Build        | Maven (wrapper included)                    |
| Utilities    | Lombok                                      |

---

## Architecture

```
            ┌───────────────────────────┐
 Client ──► │  REST Controller          │  /api/posts
            └────────────┬──────────────┘
                         │
            ┌────────────▼──────────────┐
            │  PostService              │
            │   ├─ RedisGuardrailService│──► Redis (counters, cooldowns, virality)
            │   └─ NotificationService  │──► Redis (notif cooldown, pending lists)
            └────────────┬──────────────┘
                         │ JPA
                  ┌──────▼──────┐
                  │ PostgreSQL  │  users, bot, post, comment
                  └─────────────┘

            NotificationSweeper (@Scheduled, 5 min) ──► Redis pending lists
```

### Project Structure

```
src/main/java/com/example/BotGuard/
├── BotGuardApplication.java        # Entry point
├── controllers/controller.java     # REST endpoints
├── Services/
│   ├── PostService.java            # Comment flow + guardrail orchestration
│   ├── RedisGuardrailService.java  # Bot limits, cooldowns, virality
│   └── NotificationService.java    # Instant vs. queued notifications
├── scheduler/NotificationSweeper.java  # Periodic notification summarizer
├── entities/                       # User, Bot, Post, Comment (JPA)
└── repo/                           # Spring Data JPA repositories
```

### Data Model

| Entity    | Key fields                                                         |
|-----------|--------------------------------------------------------------------|
| `User`    | `id`, `name`, `isPremium` (table: `users`)                         |
| `Bot`     | `id`, `name`, `personDetail` (TEXT persona description)            |
| `Post`    | `id`, `authId`, `authorType`, `content`, `timing`, `comments[]`    |
| `Comment` | `id`, `authId`, `authorType`, `content`, `depthLevel`, `timing`, `post` |

`authorType` is `"HUMAN"` or `"BOT"`. Guardrails apply only to `BOT` comments.

### Redis Keys

| Key pattern                              | Type   | Purpose                                 | TTL     |
|------------------------------------------|--------|-----------------------------------------|---------|
| `post:{postId}:virality_score`           | String | Running virality score                  | —       |
| `post:{postId}:bot_count`                | String | Bot comments on the post (max 100)      | —       |
| `cooldown:bot_{botId}:human_{humanId}`   | String | Bot→human interaction cooldown          | 10 min  |
| `notif_cooldown:{userId}`                | String | Notification throttle window            | 15 min  |
| `user:{userId}:pending_notifs`           | List   | Queued notifications for the sweeper    | —       |

---

## Getting Started

### Prerequisites

- JDK 21+
- PostgreSQL running on `localhost:5432`
- Redis running on `localhost:6379`

The quickest way to start both is with Docker:

```bash
docker run -d --name botguard-postgres -p 5432:5432 \
  -e POSTGRES_DB=myDB -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin123 \
  postgres:16

docker run -d --name botguard-redis -p 6379:6379 redis:7
```

### Configuration

Defaults are in `src/main/resources/application.properties`:

```properties
server.port=8080
spring.datasource.url=jdbc:postgresql://localhost:5432/myDB
spring.datasource.username=admin
spring.datasource.password=admin123
spring.jpa.hibernate.ddl-auto=update
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Hibernate creates the tables automatically (`ddl-auto=update`). Any property can be overridden with environment variables, for example `SPRING_DATASOURCE_PASSWORD=...`.

### Run

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Or build a JAR:

```bash
./mvnw clean package
java -jar target/BotGuard-0.0.1-SNAPSHOT.jar
```

The API runs at `http://localhost:8080`.

---

## API Reference

Base path: `/api/posts`

| Method | Endpoint                     | Description                         | Success | Errors |
|--------|------------------------------|-------------------------------------|---------|--------|
| POST   | `/api/posts`                 | Create a post                       | 200     | —      |
| GET    | `/api/posts`                 | List all posts with their comments  | 200     | —      |
| POST   | `/api/posts/{postId}/comments` | Add a comment (human or bot)      | 201     | 400 post not found, 429 guardrail |
| POST   | `/api/posts/{postId}/like`   | Like a post (+20 virality)          | 200     | —      |

### Examples

**Create a post**

```bash
curl -X POST http://localhost:8080/api/posts \
  -H "Content-Type: application/json" \
  -d '{"authId": 1, "authorType": "HUMAN", "content": "Hello world!"}'
```

**Human comment**

```bash
curl -X POST http://localhost:8080/api/posts/1/comments \
  -H "Content-Type: application/json" \
  -d '{"authId": 2, "authorType": "HUMAN", "content": "Nice post", "depthLevel": 1}'
```

**Bot comment (guardrails apply)**

```bash
curl -X POST http://localhost:8080/api/posts/1/comments \
  -H "Content-Type: application/json" \
  -d '{"authId": 10, "authorType": "BOT", "content": "Great insight!", "depthLevel": 1}'
```

Sending the same bot request again within 10 minutes returns:

```
HTTP/1.1 429 Too Many Requests
Guardrail: Bot limit or cooldown active
```

**Like a post**

```bash
curl -X POST http://localhost:8080/api/posts/1/like
```

**Check a virality score in Redis**

```bash
redis-cli GET post:1:virality_score
```

---

## How a Bot Comment Is Processed

1. `PostService` loads the post. If it doesn't exist, the API returns **400**.
2. If `authorType == "BOT"`, `RedisGuardrailService.validateBotGuardrails` checks:
   - `depthLevel > 20` → rejected
   - `INCR post:{id}:bot_count` > 100 → rejected
   - `SETNX cooldown:bot_{botId}:human_{ownerId}` (10 min TTL) fails → rejected
3. A rejection returns **429**.
4. `NotificationService` either notifies the post owner right away or queues the notification if the owner's 15-minute cooldown is active.
5. The virality score goes up (+1 for a bot reply, +50 for a human comment).
6. The comment is saved to PostgreSQL and returned with **201**.

Because each guardrail check is a single atomic Redis command, the limits hold under concurrent requests and across several app instances that share one Redis.

---

## Known Limitations & Roadmap

- **Scheduling isn't enabled yet.** `NotificationSweeper` uses `@Scheduled`, but the application class has no `@EnableScheduling`, so the sweeper never runs. Add `@EnableScheduling` to `BotGuardApplication` to turn it on.
- **Bot-count check isn't rolled back.** `bot_count` is incremented before the cooldown check, so a request rejected by the cooldown still counts toward the 100-comment cap. A Lua script could make the whole check-and-increment atomic.
- **`KEYS` in the sweeper.** `redisTemplate.keys("user:*:pending_notifs")` blocks Redis on large datasets. Use `SCAN` instead.
- **Notifications are console logs.** Push notifications are printed to stdout. They aren't connected to a real delivery channel such as FCM, APNs, or email.
- **No auth or validation.** `authId` and `authorType` come from the request body as-is. Adding authentication and request DTOs with validation is the next step.
- **Credentials are hard-coded** in `application.properties`. Move them to environment variables or a secrets manager before deploying.
- **Users and bots** have entities and repositories but no REST endpoints yet.

---

## Testing

```bash
./mvnw test
```

PostgreSQL and Redis must be running, since the default context-load test starts the full application.

---

## Contributing

1. Fork the repo
2. Create a branch: `git checkout -b feature/my-feature`
3. Commit your changes and open a pull request
