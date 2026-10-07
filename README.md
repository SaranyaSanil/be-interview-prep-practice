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
| Q2       | TBD     | TBD    | Not started |
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

Create request (`status` is optional and defaults to `TODO`; `description` and `dueDate` are optional):

```json
{ "title": "Prepare demo", "description": "Slides + live run", "status": "TODO", "dueDate": "2026-12-01" }
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
- `createdDate` is set by the server and can't be supplied or changed by clients.
- `PUT` replaces the editable fields, so `title` and `status` are required. A due date can't be *moved* into the
  past, but an overdue task can be updated (for example, marked `DONE`) while keeping its existing due date.
- Lists are sorted by `id`. There is no pagination; see the trade-offs below.

Design decisions and trade-offs:

- **Separate `CreateTaskRequest` and `UpdateTaskRequest`.** Their rules differ: on create, `status` is optional and
  the past-date check is static (`@FutureOrPresent`). On update, `status` is required and the past-date check
  depends on the stored value, so it lives in `TaskService` and raises `FieldValidationException`, which returns
  the same 400 field-error shape.
- **`LocalDate`** for both dates, because the requirements don't mention a time of day. An injected `Clock` makes
  "today" deterministic in tests.
- **Enum stored as `STRING`**, so reordering enum constants can't corrupt existing data.
- **No pagination.** It keeps the API simple for this exercise. With real data volumes, the list endpoint should take
  a `Pageable`.
- **`PUT` rather than `PATCH`.** Full replacement is simpler to validate and explain. `PATCH` could be added if
  partial updates are needed.
