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
| Q1       | TBD     | TBD    | Not started |
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
