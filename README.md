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
    - [1. Synchronous REST Workflow](#1-synchronous-rest-workflow)
    - [2. Asynchronous REST Workflow](#2-asynchronous-rest-workflow)
    - [3. Asynchronous GitHub Release Workflow](#3-asynchronous-github-release-workflow)
- [Design Decisions](#design-decisions)
- [GitHub Integration](#github-integration)
- [AI Integration](#ai-integration)
- [Asynchronous Processing](#asynchronous-processing)
- [Idempotency and Duplicate Job Protection](#idempotency-and-duplicate-job-protection)
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
Find previous tag
      │
      ▼
RabbitMQ
      │
      ▼
Fetch GitHub comparison
      │
      ▼
Extract commits + changed files
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
- **Git tag comparison** — retrieves commits and changed files between two tags.
- **AI-powered generation** — uses Spring AI with Ollama to generate release notes.
- **Asynchronous processing** — RabbitMQ decouples webhook handling from the expensive generation workflow.
- **Webhook security** — validates GitHub's `X-Hub-Signature-256` using HMAC-SHA256 and constant-time comparison.
- **Idempotency** — prevents generating duplicate release notes for the same repository/tag range.
- **Persistent history** — stores generated notes and their processing status in PostgreSQL.
- **Failure tracking** — failed asynchronous jobs are persisted with an error message.
- **REST API** — supports manual synchronous and asynchronous generation.
- **OpenAPI / Swagger** — API documentation is available during development.
- **Database migrations** — PostgreSQL schema is managed with Flyway.
- **Environment-specific configuration** — separate development and production configuration profiles.

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
    ├── job/
    ├── producer/
    ├── repository/
    └── service/
```
    
---

## Architecture

The application has two main ways to generate release notes.

### 1. Synchronous REST workflow

Useful for manual requests and development:

```text
Client
  │
  │ POST /api/release-notes/generate
  ▼
ReleaseNotesController
  │
  ▼
GitCompareService
  │
  │ GitHub Compare API
  ▼
Git changes
  │
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

This endpoint is intended for manual generation and development/debugging.
Because GitHub API and LLM calls can be relatively slow, the asynchronous
endpoint is preferred for production workflows.

### 2. Asynchronous REST workflow

Can be used without GitHub webhook:

```text
Client
  │
  │ POST /api/release-notes/generate-async
  ▼
ReleaseNotesController
  │
  │
  ▼
RabbitMQ
  │
  ▼
ReleaseNoteJobConsumer
  │
  ├── Check for duplicate job
  ├── Create PROCESSING record
  ├── Fetch GitHub comparison
  ├── Generate notes with AI
  │
  ▼
PostgreSQL
  │
  ├── COMPLETED + content
  └── FAILED + error message
```

The controller immediately returns '202 Accepted' after job is placed onto RabbitMQ queue.

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
  ├── Find previous tag
  │
  ▼
RabbitMQ
  │
  ▼
ReleaseNoteJobConsumer
  │
  ├── Check for duplicate job
  ├── Create PROCESSING record
  ├── Fetch GitHub comparison
  ├── Generate notes with AI
  │
  ▼
PostgreSQL
  │
  ├── COMPLETED + content
  └── FAILED + error message
```

The webhook controller does not wait for a release note to generate. Instead, it places a job onto RabbitMQ queue and immediately returns '202 Accepted'. The release note appears in database as generation finishes.

---

## Design Decisions

### Why RabbitMQ?

GitHub webhooks should be acknowledged quickly. AI generation and GitHub
API requests can take significantly longer, so the webhook handler only
validates the event and publishes a job.

This prevents external API latency from blocking the webhook request.

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
The application therefore sends commit messages and changed-file
metadata instead.

### Why check if job is already claimed before processing?
Although safety layer on the database side guarantees that no duplicates
can be inserted, the check exists to not waste resources on expensive 
GitHub API calls and LLM note processing.

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

The comparison data is transformed into a compact context for the AI model.

Instead of sending a potentially huge raw diff, the current implementation extracts:

- commit messages
- shortened commit SHAs
- changed file names
- file change status
- additions
- deletions

Example context:

```text
Commits:
- Add authentication endpoint (a1b2c3d)
- Fix password validation (e4f5g6h)

Changed files:
- [added] src/main/java/.../AuthController.java (+85 / -0)
- [modified] src/main/java/.../UserService.java (+24 / -7)
```

This keeps the AI input smaller while still providing useful information about the release.

---

## AI Integration

The project uses **Spring AI's `ChatClient`** to abstract interaction with the language model.

The AI receives the Git change context and is instructed to produce structured Markdown.

The system prompt defines four possible sections:

```markdown
## Features
## Fixes
## Breaking Changes
## Other Changes
```

The prompt also explicitly instructs the model **not to invent information that is not supported by the commits or changed files**.

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

The producer publishes this job to:

```text
release-note-generation-queue
```

The consumer then performs the expensive work:

1. Check whether the release notes already exist.
2. Create a `PROCESSING` database record.
3. Fetch the GitHub comparison.
4. Generate release notes using the LLM.
5. Save the generated content.
6. Mark the record as `COMPLETED`.

If an exception occurs:

```text
PROCESSING
    │
    └── error
         ▼
      FAILED
```

All error messages are logged.

---

## Idempotency and Duplicate Job Protection

Multiple webhook deliveries or RabbitMQ messages can represent the same release.

To prevent duplicate GitHub API and LLM calls, the consumer attempts to
atomically claim a release-note job in PostgreSQL before performing any
expensive external operations.

The release is uniquely identified by:

repo_owner + repo_name + from_tag + to_tag

PostgreSQL enforces this uniqueness with a unique constraint, while the
job claim uses `INSERT ... ON CONFLICT DO NOTHING`.

Only the consumer that successfully inserts the `PROCESSING` record
continues with GitHub and AI processing.

Duplicate messages are acknowledged without triggering another generation.

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

---

## Database

PostgreSQL stores the generated release notes.

### `release_notes`

The entity contains:

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

Flyway manages the database schema.

The initial migration also creates:

- repository indexes
- a unique constraint/index for release-note idempotency
- a creation-time index
- an `updated_at` trigger

The schema also contains a `webhook_events` table prepared for webhook event persistence and processing tracking.

---

## REST API

### Generate release notes synchronously

```http
POST /api/release-notes/generate
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

Returns the generated Markdown directly.

---

### Queue release-note generation

```http
POST /api/release-notes/generate-async
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

The webhook automatically determines the previous tag and queues the corresponding generation job.

---

## Configuration

The project uses Spring profiles:

```text
application.yaml
application-dev.yaml
application-prod.yaml
```

Common configuration contains:

```yaml
github:
  api:
    token: ${GITHUB_PAT_TOKEN}
  webhook:
    secret: ${WEBHOOK_SECRET}
```

Development configuration contains the local PostgreSQL and Ollama settings.

Production configuration expects database connection details to be provided through environment variables.

### Required environment variables

At minimum:

```text
GITHUB_PAT_TOKEN
WEBHOOK_SECRET
DATABASE_DEV_PASSWORD
```

For production:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

---

## Running locally

### Prerequisites

Make sure you have:

- Java 25+
- Docker
- Docker Compose
- Ollama
- GitHub Personal Access Token

### 1. Clone the repository

```bash
git clone https://github.com/filatovdanylo/git-release-notes-gen.git
```

### 2. Configure environment variables

```text
GITHUB_PAT_TOKEN=your_token
WEBHOOK_SECRET=your_secret
DATABASE_DEV_PASSWORD=your_db_password
```

### 3. Make sure Ollama model is running

### 4. Start the application

Using the Gradle wrapper:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

On Windows:

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=dev"
```

### 5. Explore the API

Once the application is running, interactive API documentation is available at:

```text
http://localhost:8080/swagger-ui.html
```

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
  cryptographically invalid signatures.
- **GitHub API failure modes** — non-2xx responses mapped to the correct
  status, network/transport failures wrapped without leaking internals.
- **Idempotent job claiming** — duplicate jobs are skipped without
  re-triggering GitHub or AI calls.
- **Error handling boundaries** — internal exception messages are logged
  in full but never persisted or exposed through the API; only a generic,
  safe message is stored on `FAILED` records.
- **Tag resolution edge cases** — target tag not found, target tag is the
  newest (nothing to diff against), single-tag repositories.

### Running tests

```bash
./gradlew test
```

---

## Future Improvements

Possible next steps include:

**Security & Reliability**
- GitHub App authentication instead of a personal access token
- Persisting and deduplicating GitHub webhook delivery IDs
- RabbitMQ retry and dead-letter queues
- Authentication/authorization for the REST API

**AI & Content Quality**
- AI structured output instead of plain Markdown
- Support for pull-request metadata and labels
- Large-diff/token-limit handling
- Release-note regeneration

**API & Operations**
- Job status endpoint
- Metrics and observability with Spring Boot Actuator
- Docker image and CI/CD pipeline
- Deployment to a cloud environment