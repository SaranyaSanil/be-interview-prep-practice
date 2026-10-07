# be-interview-prep

A Spring Boot backend for working through five interview questions (Q1–Q5). All questions share one application and
one set of conventions, and each question is delivered as a separate feature branch and pull request.

## Technology stack

| Concern     | Choice                                              |
|-------------|-----------------------------------------------------|
| Language    | Java 21                                             |
| Framework   | Spring Boot 3.5.x (Spring Web, Spring Data JPA)     |
| Validation  | Jakarta Bean Validation (Hibernate Validator)       |
| Database    | H2 in-memory (local development and tests)          |
| Build       | Maven, via the Maven Wrapper                        |
| Testing     | JUnit 5, Mockito, AssertJ, MockMvc                  |

## Build and run

You don't need to install Maven globally because the wrapper downloads it. On Windows, use `mvnw.cmd` in place of
`./mvnw`.

```bash
./mvnw clean verify          # compile and run all tests
./mvnw test                  # run tests only
./mvnw spring-boot:run       # start the app on http://localhost:8080
```

## Architecture

Code is organized **by feature** and layered **Controller → Service → Repository**:

```
src/main/java/com/interviewprep
├── InterviewPrepApplication.java
├── common/exception/          GlobalExceptionHandler, ResourceNotFoundException
└── <feature>/                 one package per question (added per question)
    ├── <Feature>Controller    HTTP mapping, input validation, status codes
    ├── <Feature>Service       business rules, transactions, DTO mapping
    ├── <Feature>Repository    Spring Data JPA
    ├── <Feature>              JPA entity
    └── dto/                   request/response records
```

Key decisions:

- **Entities stay behind DTOs.** The API contract is independent of the database schema.
- **Business logic and transactions live in the service layer.** `open-in-view` is disabled, so data access stays
  inside the service transaction.
- **Errors use one format.** `GlobalExceptionHandler` returns RFC 9457 `ProblemDetail` JSON for every error.
  Validation errors include a per-field `errors` list, and unexpected errors return a generic 500 without leaking
  internals.

Example validation error:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/orders",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

The full conventions are in [`CLAUDE.md`](CLAUDE.md).

## Q1–Q5 workflow

For each question:

1. Read the requirements and note any assumptions or open questions.
2. Create a branch from `main`, for example `feature/q1-<short-name>`.
3. Implement it in a new feature package following the layering above.
4. Write tests: service unit tests, `@WebMvcTest` controller tests, and repository or integration tests where they
   add value. Include failure cases.
5. Run `./mvnw clean verify`. It must pass.
6. Do a senior self-review using the checklist in `CLAUDE.md` and record the decisions and trade-offs.
7. Open a PR.

| Question | Feature | Branch | Status      |
|----------|---------|--------|-------------|
| Q1       | [Task Manager API](#q1--task-manager-api) | `feature/q1-task-api` | Implemented |
| Q2       | [URL Shortener](#q2--url-shortener) | `feature/q2-url-shortener` | Implemented |
| Q3       | TBD     | TBD    | Not started |
| Q4       | TBD     | TBD    | Not started |
| Q5       | TBD     | TBD    | Not started |

## PR workflow

- One PR per question, targeting `main`.
- The PR description includes a requirements summary, the design decisions and trade-offs, the API endpoints with
  example requests and responses, the tests added, and the senior-review summary.
- `./mvnw clean verify` must pass before you request review.
- Keep PRs focused, with no unrelated refactoring.

## Q1 — Task Manager API

Package `com.interviewprep.task`, with endpoints under `/api/tasks`:

| Method   | Path              | Description                                | Success | Errors   |
|----------|-------------------|--------------------------------------------|---------|----------|
| `POST`   | `/api/tasks`      | Create a task                              | 201 + `Location` | 400 |
| `GET`    | `/api/tasks`      | List tasks, with optional `?status=` filter | 200     | 400      |
| `GET`    | `/api/tasks/{id}` | Get one task                               | 200     | 404      |
| `PUT`    | `/api/tasks/{id}` | Replace a task's editable fields           | 200     | 400, 404 |
| `DELETE` | `/api/tasks/{id}` | Delete a task                              | 204     | 404      |

Create request (`description` and `dueDate` are optional). New tasks always start as `TODO`:

```json
{ "title": "Prepare demo", "description": "Slides + live run", "dueDate": "2026-12-01" }
```

Update request (`PUT`, full replacement of the editable fields):

```json
{ "title": "Prepare demo", "description": "Slides + live run", "status": "IN_PROGRESS", "dueDate": "2026-12-01" }
```

Response:

```json
{ "id": 1, "title": "Prepare demo", "description": "Slides + live run", "status": "TODO",
  "dueDate": "2026-12-01", "createdDate": "2026-10-07" }
```

Rules:

- `title` is required, cannot be blank, and has at most 100 characters. `description` has at most 1000 characters
  (an assumption).
- `status` is one of `TODO`, `IN_PROGRESS`, `DONE`. Any other value returns 400 listing the allowed values.
- `dueDate` (ISO `yyyy-MM-dd`) cannot be in the past. It is optional, because the requirements don't say it is
  required.
- `id`, `createdDate` and the initial `status` are set by the server. A client can't supply them on create, and
  `createdDate` can never change.
- `PUT` replaces the editable fields, so `title` and `status` are required. A due date can't be *moved* into the
  past, but an overdue task can be updated (for example, marked `DONE`) while keeping its existing due date.
- Lists are sorted by `id`. There is no pagination; see the trade-offs below.

Design decisions and trade-offs:

- **Separate `CreateTaskRequest` and `UpdateTaskRequest`.** Their rules differ. Create has no `status`, because new
  tasks start as `TODO`, and its past-date check is static (`@FutureOrPresent`). Update requires `status`, and its
  past-date check depends on the stored value, so it lives in `TaskService` and raises `FieldValidationException`,
  which returns the same 400 field-error shape.
- **`LocalDate`** for both dates, because the requirements don't mention a time of day. One injected `Clock` is used
  both by the service and by Bean Validation (`@FutureOrPresent`), so "today" is consistent and can be fixed in
  tests.
- **Enum stored as `STRING`**, so reordering enum constants can't corrupt existing data.
- **No pagination.** It keeps the API simple for this exercise. With real data volumes, the list endpoint should take
  a `Pageable`.
- **`PUT` rather than `PATCH`.** Full replacement is simpler to validate and explain. `PATCH` could be added if
  partial updates are needed.

## Q2 — URL Shortener

Package `com.interviewprep.shorturl`:

| Method | Path                       | Description                          | Success              | Errors   |
|--------|----------------------------|--------------------------------------|----------------------|----------|
| `POST` | `/api/urls`                | Shorten a URL                        | 201 + `Location`     | 400      |
| `GET`  | `/{shortCode}`             | Redirect to the original URL, counting the visit | 302 + `Location` | 404, 410 |
| `GET`  | `/api/urls/{shortCode}/stats` | Original URL, visit count and created time | 200            | 404      |

Request (`expiryDate` is optional):

```json
{ "url": "https://example.com/some/very/long/path", "expiryDate": "2026-12-31" }
```

Response:

```json
{ "shortCode": "aZ3k9Qx", "shortUrl": "http://localhost:8080/aZ3k9Qx",
  "originalUrl": "https://example.com/some/very/long/path", "expiryDate": "2026-12-31",
  "createdAt": "2026-10-07T06:30:00Z" }
```

Stats response:

```json
{ "originalUrl": "https://example.com/some/very/long/path", "visitCount": 42, "createdAt": "2026-10-07T06:30:00Z" }
```

Design decisions and trade-offs:

- **Short codes are 7 random Base62 characters** (`A–Z a–z 0–9`) from `SecureRandom`.
  - They are URL-safe without encoding and within the 8-character limit.
  - There are about 3.5 trillion combinations.
  - Random codes can't be guessed in sequence, unlike codes built from the database id.
- **Codes are unique** because the `short_code` column has a unique constraint. That is the real guarantee. The service
  also checks for an existing code before saving and retries up to 5 times. After 5 collisions, which is practically
  impossible, the request fails with a 500.
- **Shortening the same URL twice creates a new code each time.** Each link then has its own expiry date and visit
  stats, which is useful when one URL is shared in different places. It also avoids two problems with reusing a code:
  deciding which link wins when expiry dates differ, and two identical requests racing each other. The cost is some
  duplicate rows. Reusing the code would need a unique index on the long URL and rules for differing expiry dates.
- **Visit counts stay accurate under concurrency** because each visit runs a single atomic
  `UPDATE ... SET visit_count = visit_count + 1`. The database locks the row for that update.
  - A read, add one, save approach loses visits. In the concurrency test it counted only 77 of 500.
  - Optimistic locking (`@Version`) would fail and retry constantly on a popular link.
  - A pessimistic lock makes every request wait in line.
  - An in-memory counter is lost on restart and doesn't work across several servers.
- **Expiry is inclusive.** A link with `expiryDate` 2026-12-31 works until the end of that day, based on the
  application's `Clock`.
  - A past expiry date is rejected with 400.
  - Every redirect checks expiry.
  - Visits to an expired link aren't counted.
  - Stats stay available after expiry, because they describe past activity.
- **Status codes:** an unknown code returns 404. An expired code returns **410 Gone**, which tells the client the
  link existed but is permanently unavailable. That is clearer than a 404.
- **Redirects use 302, not 301.** Browsers cache 301s and skip the server, so repeat visits wouldn't be counted.
  `Cache-Control: no-store` stops caches in between from storing the redirect.
- **The redirect route only matches `/{code:[A-Za-z0-9]{1,8}}`.** It can't clash with `/api/**`, and invalid
  codes return 404 without reaching the service.
- **URL validation:**
  - Only absolute `http`/`https` URLs of at most 2048 characters, with no whitespace, are accepted.
  - This rejects `javascript:`, `data:` and `ftp:` URLs, so the redirect can't be used for script injection.
  - It also rejects line breaks, so a URL can't inject extra HTTP headers.
- **The short URL is built from the incoming request's base address.** Behind a reverse proxy, Spring's forwarded-header
  support (`server.forward-headers-strategy`) or a configured base URL would be needed.
- **Not included:** code deletion, custom aliases, per-visit analytics and rate limiting.
