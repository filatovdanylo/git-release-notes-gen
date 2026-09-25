# Git Diff Release Notes Generator
An automated release-note generator that analyzes the changes between two Git tags and uses AI to produce structured release notes in Markdown.

The project is designed as a Spring Boot backend that combines GitHub API integration, Spring AI, PostgreSQL and RabbitMQ for asynchronous processing.

Instead of manually reviewing commits and writing release notes for every release, the application collects information about the changes and lets an AI model turn them into a readable summary.

---

## Table of Contents

- [Example of Generated Release Notes](#example-of-generated-release-notes)
- [Why This Project?](#why-this-project)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
    - [1. Synchronous REST Workflow (dev only)](#1-synchronous-rest-workflow-dev-only)
    - [2. Asynchronous REST Workflow](#2-asynchronous-rest-workflow)
    - [3. Asynchronous GitHub Release Workflow](#3-asynchronous-github-release-workflow)
- [Design Decisions](#design-decisions)
- [GitHub Integration](#github-integration)
- [AI Integration](#ai-integration)
- [Asynchronous Processing](#asynchronous-processing)
- [Idempotency and Duplicate Job Protection](#idempotency-and-duplicate-job-protection)
- [Authentication and Authorization](#authentication-and-authorization)
- [Rate Limiting](#rate-limiting)
- [Webhook Security](#webhook-security)
- [Database](#database)
- [REST API](#rest-api)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
- [Testing](#testing)
- [Future Improvements](#future-improvements)
 
## **Example of generated release notes:**

<details>
  <summary>📂 Click to expand</summary>

```text
## Features
No new features were introduced in this release.

## Fixes
No specific fixes were highlighted in this release.

## Breaking Changes
No breaking changes were introduced in this release.

## Other Changes
- Improved the README file (#80)
- Simplified minor code sections (#83)
- Migrated CI/CD pipeline to GitHub Actions (#85)
- Updated project dependencies (#86)
- Version bumped to `9.0.1`
```
</details>

---

## Why this project?

Writing release notes is repetitive, and it often means having to review all commits to understand what actually changed.

This service automates that workflow:

```text
GitHub Release
      │
      ▼
GitHub Webhook
      │
      ▼
Verify HMAC-SHA256 signature
      │
      ▼
RabbitMQ
      │
      ▼
Resolve previous release tag
      │
      ▼
Fetch GitHub comparison
      │
      ▼
Extract commits, pull requests + changed files
      │
      ▼
Spring AI / Ollama
      │
      ▼
Generate Markdown release notes
      │
      ▼
PostgreSQL
```

---

## Key features

- **GitHub release integration** — reacts to published GitHub releases.
- **Previous-release resolution** — finds the prior non-draft release via the GitHub Releases API (in the async consumer).
- **Git tag comparison** — retrieves commits and changed files between two tags.
- **Pull request enrichment** — extracts PR references from commit messages and, for smaller releases, resolves PR titles via the GitHub Search API without per-commit API calls.
- **Adaptive LLM context** — builds a richer context for small releases and a compact summary for large ones (commit-count threshold).
- **AI-powered generation** — uses Spring AI with Ollama to generate release notes.
- **Asynchronous processing** — RabbitMQ decouples webhook handling from the expensive generation workflow.
- **Webhook security** — validates GitHub's `X-Hub-Signature-256` using HMAC-SHA256 and constant-time comparison.
- **JWT authentication** — protects the REST API with Bearer tokens (HS256).
- **Ownership-based authorization** — users may generate or read notes only for repositories whose owner matches their username (`ADMIN` can access all).
- **Admin-only registration** — new accounts can be created only by users with the `ADMIN` role.
- **Rate limiting** — Bucket4j limits request rates per client IP on `/api/**` (auth endpoints excluded).
- **Idempotency with reclaim** — prevents duplicate generation for the same repository/tag range, while allowing retries for `FAILED` or stale `PROCESSING` jobs.
- **Persistent history** — stores generated notes and their processing status in PostgreSQL.
- **Failure tracking** — failed asynchronous jobs are persisted with a generic error message; details stay in server logs.
- **REST API** — supports manual asynchronous generation; a synchronous path exists only under the `dev` profile.
- **OpenAPI / Swagger** — API documentation is available during development.
- **Database migrations** — PostgreSQL schema is managed with Flyway.
- **Environment-specific configuration** — separate development, test, and production configuration profiles.
- **CI** — GitHub Actions runs the test suite on pushes and pull requests.

---

## Tech Stack

| Category | Technology                          |
|---|-------------------------------------|
| Language | Java 25                             |
| Framework | Spring Boot 4.1.1                   |
| AI | Spring AI 2.0.1                     |
| AI Model | Ollama + Qwen3 8B                   |
| Database | PostgreSQL                          |
| Persistence | Spring Data JPA / Hibernate         |
| Migrations | Flyway                              |
| Messaging | RabbitMQ / Spring AMQP              |
| Security | Spring Security + OAuth2 Resource Server (JWT) |
| Rate limiting | Bucket4j                            |
| GitHub | GitHub REST API + Kohsuke GitHub API |
| HTTP Client | Spring `RestClient`                 |
| JSON | Jackson                             |
| API Documentation | Springdoc OpenAPI / Swagger UI      |
| Validation | Jakarta Bean Validation             |
| Build | Gradle                              |
| Infrastructure | Docker / Docker Compose             |
| Testing | JUnit 5 / Spring Boot Test / Mockito |

---

## Project Structure

```text
src/main/java/
└── me.automatedgitdiffnotesgenerator/
    ├── client/
    ├── config/
    ├── consumer/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── exception/
    ├── interceptor/
    ├── job/
    ├── producer/
    ├── repository/
    ├── security/
    └── service/
```
    
---

## Architecture

The application has three ways to generate release notes.

### 1. Synchronous REST workflow (dev only)

Useful for manual requests and debugging. Enabled only when the `dev` Spring profile is active:

```text
Client
  │
  │ POST /api/release-notes/dev/generate
  ▼
SyncReleaseNotesController
  │
  ▼
GitCompareService
  │
  │ Compare + PR-aware context
  ▼
NoteGenerationService
  │
  │ Spring AI
  ▼
Ollama
  │
  ▼
Generated Markdown
  │
  ▼
HTTP response
```

This endpoint returns the generated release notes directly and does not create a database record.

Because GitHub API and LLM calls can be relatively slow, the asynchronous endpoint is preferred for normal workflows.

### 2. Asynchronous REST workflow

Can be used without a GitHub webhook:

```text
Client (JWT)
  │
  │ POST /api/release-notes/generate-async
  ▼
ReleaseNotesController
  │  ownership check (repoOwner == username or ADMIN)
  ▼
RabbitMQ
  │
  ▼
ReleaseNoteJobConsumer
  │
  ├── Claim / reclaim job in PostgreSQL
  ├── Fetch GitHub comparison
  ├── Generate notes with AI
  │
  ▼
PostgreSQL
  │
  ├── COMPLETED + content
  └── FAILED + error message
```

The controller immediately returns `202 Accepted` after the job is placed onto the RabbitMQ queue.

### 3. Asynchronous GitHub release workflow

The production-oriented workflow:

```text
GitHub
  │
  │ release.published
  ▼
GitHubWebhookController
  │
  ├── Verify X-Hub-Signature-256
  ├── Validate event/action
  ├── Enqueue job (fromTag unresolved)
  │
  ▼  202 Accepted
RabbitMQ
  │
  ▼
ReleaseNoteJobConsumer
  │
  ├── Resolve previous release tag
  ├── Claim / reclaim job in PostgreSQL
  ├── Fetch GitHub comparison
  ├── Generate notes with AI
  │
  ▼
PostgreSQL
  │
  ├── COMPLETED + content
  └── FAILED + error message
```

The webhook controller does not call the GitHub API and does not wait for notes to be generated. It validates the event, publishes a job with `fromTag = null`, and returns `202 Accepted`. The consumer resolves the previous release, then runs generation.

---

## Design Decisions

### Why RabbitMQ?

GitHub webhooks should be acknowledged quickly. AI generation and GitHub
API requests can take significantly longer, so the webhook handler only
validates the event and publishes a job.

This prevents external API latency from blocking the webhook request.

### Why resolve the previous release in the consumer?

Listing releases and calling GitHub from the webhook handler risks GitHub's
webhook timeout and couples acknowledgment to upstream availability.
Deferring resolution keeps the HTTP path fast and lets RabbitMQ retries
handle transient GitHub failures.

### Why PostgreSQL?

The application needs durable storage for generated release notes and job
status. PostgreSQL also provides unique constraints that are used for
idempotent job claiming.


### Why Ollama?

Ollama allows the project to run the LLM locally without requiring a
cloud AI provider or exposing source-code information to an external
model provider during development.

### Why Spring AI?

Spring AI provides a consistent abstraction around chat models while
integrating naturally with the Spring Boot application.

### Why send a compact change summary instead of the raw diff?

Large raw diffs can unnecessarily increase the model context size.
The application therefore sends commit messages, pull-request references,
and changed-file metadata instead.

### Why enrich context with pull requests?

Commit subjects often already contain `#123` or `Merge pull request #123`.
Parsing those references avoids expensive per-commit GitHub calls. For
smaller releases, PR numbers found in commits are matched against a single
(paginated) Search query for merged PRs in the release date window, so the
model receives PR titles without an N+1 API pattern.

### Why different context for small and large releases?

Releases with many commits can drown the model in noise (merge commits,
dependency bots, tiny file churn). When the compare result exceeds a
commit-count threshold (currently 50), the application keeps non-PR
commits and parsed PR references, but summarizes file changes as total
additions/deletions instead of listing every file. Smaller releases keep
per-file detail and Search-enriched PR titles.

### Why reclaim FAILED or stale PROCESSING jobs?

A hard "insert once forever" claim would leave permanent dead jobs after
transient GitHub or LLM failures. The claim query can re-take a row when
status is `FAILED`, or when `PROCESSING` is older than a configured
stale threshold (`app.release-notes.claim-stale-after`).

### Why check if a job is already claimed before processing?

Although the database uniqueness constraint guarantees no duplicate rows
for the same tag range, the claim step avoids wasting resources on
expensive GitHub API calls and LLM processing when another worker already
owns the job.

### Why a Personal Access Token instead of a GitHub App?

A GitHub App would add installation tokens, PEM secrets, and heavier local
setup (tunnels for webhooks during development). This project is aimed at
personal / local use and portfolio demonstration of the Spring stack, not
multi-tenant SaaS. A PAT (ideally fine-grained and repo-scoped) keeps
configuration to one environment variable and makes the project easy to
run. GitHub App authentication remains a future option if the product
direction becomes a shared public service.

---

## GitHub Integration

The application uses the GitHub API to compare two tags:

```text
fromTag...toTag
```

For example:

```text
v1.0.0...v1.1.0
```

For webhook-driven jobs, the previous tag is resolved from the **Releases**
API (newest to older, skipping drafts), not by loading every repository tag
into memory.

The comparison data is transformed into a compact context for the AI model.

Instead of sending a potentially huge raw diff, the current implementation extracts:

- commit messages (commits that do not already look like PR references)
- shortened commit SHAs
- pull request numbers parsed from commit subjects (`#123`)
- for smaller releases: PR titles from the GitHub Search API (merged PRs in the release date window, intersected with numbers found in commits)
- changed file names, status, additions, and deletions (or aggregate line counts for large releases)

Example context (small release):

```text
Commits:
- Add authentication endpoint (a1b2c3d)
- Fix password validation (e4f5g6h)

Pull requests:
- (#42) Add caching layer

Changed files:
- [added] src/main/java/.../AuthController.java (+85 / -0)
- [modified] src/main/java/.../UserService.java (+24 / -7)
```

This keeps the AI input smaller while still providing useful information about the release, without calling `/commits/{sha}/pulls` for every commit.

GitHub API access uses a **Personal Access Token** (`GITHUB_PAT_TOKEN`).

---

## AI Integration

The project uses **Spring AI's `ChatClient`** to abstract interaction with the language model.

The AI receives the Git change context (commits, pull requests, and file
changes) and is instructed to produce structured Markdown.

The system prompt defines four possible sections:

```markdown
## Features
## Fixes
## Breaking Changes
## Other Changes
```

The prompt also explicitly instructs the model **not to invent information
that isn't implied by the commits, pull requests, or file changes provided**.

### Local AI with Ollama

Development uses:

```text
Ollama
└── qwen3:8b
```

This allows the project to run the AI generation locally without requiring a cloud AI provider.

The model configuration is kept in the development Spring profile:

```yaml
spring:
  ai:
    ollama:
      chat:
        model: qwen3:8b
        temperature: 0.3
        think: false
```

---

## Asynchronous Processing

RabbitMQ is used to separate the webhook request from the release-note generation process.

A job contains:

```json
{
  "repoOwner": "example",
  "repoName": "my-project",
  "fromTag": "v1.0.0",
  "toTag": "v1.1.0"
}
```

For webhook-originated jobs, `fromTag` is `null` until the consumer resolves it.

The producer publishes this job to:

```text
release-note-generation-queue
```

Listener retries for transient failures are configured in Spring AMQP
(exponential backoff, limited attempts).

The consumer then performs the expensive work:

1. Resolve `fromTag` when missing (previous non-draft release).
2. Atomically claim (or reclaim) a `PROCESSING` database record.
3. Fetch the GitHub comparison and build an adaptive LLM context (commits, PRs, files).
4. Generate release notes using the LLM.
5. Save the generated content.
6. Mark the record as `COMPLETED`.

If an exception occurs after the claim:

```text
PROCESSING
    │
    └── error
         ▼
      FAILED
```

Full error details are logged; only a generic message is stored on the row.

---

## Idempotency and Duplicate Job Protection

Multiple webhook deliveries or RabbitMQ messages can represent the same release.

To prevent duplicate GitHub API and LLM calls, the consumer attempts to
atomically claim a release-note job in PostgreSQL before performing any
expensive external operations.

The release is uniquely identified by:

repo_owner + repo_name + from_tag + to_tag

PostgreSQL enforces this uniqueness with a unique constraint. The claim uses:

```text
INSERT ... ON CONFLICT DO UPDATE
```

with a guarded `WHERE` so that only `FAILED` rows, or `PROCESSING` rows older
than `app.release-notes.claim-stale-after`, can be reclaimed. `COMPLETED`
jobs and fresh `PROCESSING` jobs are left alone.

Only the consumer that successfully inserts or updates the row continues
with GitHub and AI processing.

Duplicate messages are acknowledged without triggering another generation.

---

## Authentication and Authorization

The REST API (except login, Swagger in dev, and the GitHub webhook) requires a JWT Bearer token.

### Login

```http
POST /api/auth/login
```

Returns an access token, token type `Bearer`, and TTL in milliseconds.

### Register

```http
POST /api/auth/register
```

Requires an authenticated user with the `ADMIN` role. Creates a new user with role `USER`.

The first administrator must be inserted into the `users` table manually (or through a one-off DB seed), because open self-registration is disabled.

### Ownership model

For release-note endpoints:

- A normal user may act only when `repoOwner` / `{owner}` equals their username.
- Users with role `ADMIN` may access any repository.

This is a demo-style ownership check. Stronger binding (for example a GitHub App installation) is listed under Future Improvements.

Passwords are stored with BCrypt. JWTs are signed with HS256 using a Base64-encoded secret of at least 32 raw bytes.

---

## Rate Limiting

Bucket4j applies per-client-IP limits on `/api/**`, excluding `/api/auth/**`:

| Endpoint pattern | Limit |
|---|---|
| Paths containing `/generate` | 3 requests per minute |
| Other matched API paths | 10 requests per minute |

Exceeded limits return `429 Too Many Requests`.

---

## Webhook Security

GitHub webhook requests are protected with:

```text
X-Hub-Signature-256
```

The application:

1. Reads the raw request body.
2. Extracts the SHA-256 signature.
3. Computes an HMAC-SHA256 hash using the configured webhook secret.
4. Compares the hashes using `MessageDigest.isEqual`.
5. Rejects invalid requests with `401 Unauthorized`.

Only verified `release` events with the `published` action are processed.

The webhook secret is provided through an environment variable rather than being stored in source code.

Local development without a public URL can use a tunnel (ngrok, Cloudflare Tunnel, Smee, and similar) pointed at `/api/webhooks/github`, or signed fake webhook requests against localhost.

---

## Database

PostgreSQL stores generated release notes and application users.

### `release_notes`

```text
id
repo_owner
repo_name
from_tag
to_tag
status
content
error_message
created_at
updated_at
```

Processing status:

```text
PROCESSING
COMPLETED
FAILED
```

### `users`

```text
id
username
password
role
enabled
created_at
updated_at
```

Flyway manages the database schema.

The initial migration also creates:

- repository indexes
- a unique constraint/index for release-note idempotency
- a creation-time index
- an `updated_at` trigger

The schema also contains a `webhook_events` table prepared for webhook event persistence and delivery-ID tracking (not yet used by the application code).

---

## REST API

Protected endpoints expect:

```http
Authorization: Bearer <jwt>
```

### Login

```http
POST /api/auth/login
```

Request:

```json
{
  "username": "octocat",
  "password": "password1"
}
```

---

### Register (ADMIN only)

```http
POST /api/auth/register
```

Request:

```json
{
  "username": "new-user",
  "password": "password1"
}
```

HTTP status:

```text
201 Created
```

---

### Generate release notes synchronously (dev profile only)

```http
POST /api/release-notes/dev/generate
```

Request:

```json
{
  "repoOwner": "octocat",
  "repoName": "hello-world",
  "fromTag": "v1.0.0",
  "toTag": "v1.1.0"
}
```

Returns the generated Markdown directly. Not available outside the `dev` profile.

---

### Queue release-note generation

```http
POST /api/release-notes/generate-async
```

Requires ownership: `repoOwner` must equal the authenticated username, or the caller must be `ADMIN`.

Request:

```json
{
  "repoOwner": "octocat",
  "repoName": "hello-world",
  "fromTag": "v1.0.0",
  "toTag": "v1.1.0"
}
```

Response:

```text
Queued for generation
```

HTTP status:

```text
202 Accepted
```

---

### Get generated release notes

```http
GET /api/release-notes/{owner}/{repo}
```

Requires ownership: `{owner}` must equal the authenticated username, or the caller must be `ADMIN`.

Returns stored release notes for a repository.

---

### GitHub webhook

```http
POST /api/webhooks/github
```

Supported *event*:

```text
release
```

Supported *action*:

```text
published
```

The webhook queues a job with an unresolved `fromTag`. The consumer determines the previous release and runs generation.

---

## Configuration

The project uses Spring profiles:

```text
application.yaml
application-dev.yaml
application-prod.yaml
application-test.yaml
```

Common configuration includes:

```yaml
github:
  api:
    token: ${GITHUB_PAT_TOKEN}
  webhook:
    secret: ${WEBHOOK_SECRET}

app:
  jwt:
    secret: ${JWT_SECRET}
    issuer: git-diff-notes-generator
    access-token-ttl: 1h
  release-notes:
    claim-stale-after: 15m
```

Development configuration contains the local PostgreSQL and Ollama settings.

Production configuration expects database connection details through environment variables and disables Swagger / OpenAPI docs.

### Required environment variables

At minimum (local `dev`):

```text
GITHUB_PAT_TOKEN
WEBHOOK_SECRET
DATABASE_DEV_PASSWORD
JWT_SECRET
```

`JWT_SECRET` must be Base64-encoded and decode to at least 32 bytes.

For production:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
GITHUB_PAT_TOKEN
WEBHOOK_SECRET
JWT_SECRET
```

Docker Compose starts PostgreSQL on host port `5433` and RabbitMQ (AMQP `5672`, management UI `15672`).

---

## Running Locally

### Prerequisites

Make sure you have:

- Java 25+
- Docker
- Docker Compose
- Ollama (`qwen3:8b` pulled/running)
- GitHub Personal Access Token with access to the repositories you want to process (a fine-grained, repo-scoped token is recommended)

### 1. Clone the repository

```bash
git clone https://github.com/filatovdanylo/git-release-notes-gen.git
```

### 2. Configure environment variables

```text
GITHUB_PAT_TOKEN=your_token
WEBHOOK_SECRET=your_secret
DATABASE_DEV_PASSWORD=your_db_password
JWT_SECRET=your_base64_secret_at_least_32_bytes_when_decoded
```

### 3. Start infrastructure

```bash
docker compose up -d
```

### 4. Make sure the Ollama model is running

### 5. Bootstrap an admin user

Insert an admin row into `users` (BCrypt password hash), then use `/api/auth/login`. Additional users can be created via `/api/auth/register` with that admin token.

### 6. Start the application

Using the Gradle wrapper:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

On Windows:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

### 7. Explore the API

Once the application is running, interactive API documentation is available at:

```text
http://localhost:8080/swagger-ui.html
```

Authorize in Swagger with a Bearer JWT obtained from `/api/auth/login`.

---

## Testing

The project uses **JUnit 5**, **Mockito**, and **Spring Boot Test** utilities,
with different testing strategies chosen per layer:

| Layer | Strategy | Example |
|---|---|---|
| Controllers | `MockMvc` (standalone setup) | `GitHubWebhookControllerTest` |
| Services with external HTTP calls | `@RestClientTest` + `MockRestServiceServer` | `GitCompareServiceTest` |
| Services wrapping third-party SDKs | Mockito with an injected client factory seam | `GitHubTagServiceTest` |
| Async message consumers | Mockito, verifying persisted entity state per branch | `ReleaseNoteJobConsumerTest` |

### What's covered

- **Webhook signature verification** — valid, missing, malformed, and
  cryptographically invalid signatures; enqueue with unresolved `fromTag`.
- **GitHub Releases previous-tag resolution** — previous release, drafts skipped,
  missing tag, single release, oldest release.
- **Consumer resolve path** — webhook jobs resolve `fromTag` before claim;
  first-release and not-found cases skip generation; GitHub IO failures are rethrown for retry.
- **GitHub Compare API failure modes** — non-2xx responses mapped to the correct
  status, network/transport failures wrapped without leaking internals.
- **Pull-request context path** — small releases call Releases + Search with
  auth headers; PR titles are intersected with `#N` references from commits.
- **Idempotent job claiming** — duplicate jobs are skipped without
  re-triggering GitHub or AI calls.
- **Error handling boundaries** — internal exception messages are logged
  in full but never persisted on `FAILED` records; only a generic safe message is stored.

### Running tests

```bash
./gradlew test
```

---

## Future Improvements

Possible next steps include:

**Security & Reliability**
- Persisting and deduplicating GitHub webhook delivery IDs (`webhook_events`)
- RabbitMQ dead-letter queues
- First-admin bootstrap without manual SQL
- Stronger tenancy than username == repository owner (for example a repo allowlist, or a GitHub App if the project becomes multi-tenant SaaS)
- Sanitizing upstream GitHub error bodies before returning them to API clients

**AI & Content Quality**
- AI structured output instead of plain Markdown
- PR labels and further noise filtering (bots / merge commits) in large releases
- Explicit LLM token-budget / truncation handling when Compare results are capped
- Fallback when `/releases/tags/{tag}` is missing (tag without a GitHub Release)
- Release-note regeneration endpoint

**API & Operations**
- Job status endpoint
- Metrics and observability with Spring Boot Actuator
- Docker image for the application itself
- Deployment to a cloud environment (only if the project moves beyond local / portfolio use)
