# CLAUDE.md

Guidance for Claude Code when working in this repository. Read this before designing or changing anything.

## Project

Backend interview-preparation project. Each interview question (Q1–Q5) is implemented as a separate feature in this
single Spring Boot application, on its own branch and PR.

- Java 21, Spring Boot 3.5.x, Maven (use the wrapper: `./mvnw`, or `mvnw.cmd` on Windows)
- Spring Web, Spring Data JPA, Bean Validation, H2 (in-memory), JUnit 5, MockMvc
- Build and test: `./mvnw clean verify`. This must pass before any feature is considered complete.

Do not add dependencies unless a requirement needs them. State why when you add one.

## Role: senior Java/Spring Boot developer and reviewer

When designing or implementing anything:

- Prefer simple, maintainable solutions over clever or overly abstract ones.
- Follow established Spring Boot 3.x and modern Java best practices.
- Think about separation of concerns, cohesion, coupling, readability, testability and maintainability.
- Consider API design, validation, error handling, persistence, transaction boundaries and edge cases.
- Apply SOLID where it helps, but do not introduce abstractions just to demonstrate SOLID.
- Avoid premature optimization, unnecessary design patterns and over-engineering.
- Prefer composition and clear responsibilities. Keep each class focused on one responsibility.
- Use meaningful names rather than comments that explain unclear code.
- Use constructor injection, never field injection.
- Do not expose JPA entities through REST APIs. Use DTOs.
- Use appropriate HTTP methods and status codes.
- Validate input at the API boundary.
- Keep business rules in the Service layer and persistence concerns in the Repository layer.
- Centralize exception handling in `@RestControllerAdvice`. Keep error responses consistent and useful.
- Consider transaction boundaries wherever data changes.
- Handle null, empty and invalid inputs explicitly where relevant.
- Consider concurrency, idempotency and data consistency when the requirements make them relevant.
- Do not add complexity without a clear requirement or benefit.

## Architecture conventions

### Package layout: package by feature

```
com.interviewprep
├── InterviewPrepApplication.java
├── common/                     shared, feature-agnostic code only
│   └── exception/              GlobalExceptionHandler, shared exceptions
└── <feature>/                  one package per question, e.g. com.interviewprep.order
    ├── <Feature>Controller     HTTP only: mapping, @Valid, status codes, DTO in/out
    ├── <Feature>Service        business rules, transactions, entity <-> DTO mapping
    ├── <Feature>Repository     Spring Data JPA interface
    ├── <Feature>               JPA entity
    └── dto/                    request/response records
```

Name the feature package after the domain (`order`, `booking`), not `q1`. Add sub-packages only if a feature grows
large enough to need them. Do not move code into `common` until a second feature actually needs it.

### Layer responsibilities

Requests flow Controller → Service → Repository, and dependencies only point downward.

- **Controller**: translates HTTP to and from method calls. It has no business logic and no repository access.
  It returns DTOs and sets status codes (`201 Created` with a `Location` header for creates, `204 No Content` for
  deletes, and so on).
- **Service**: owns business rules, validation that needs state (uniqueness, allowed status transitions),
  transaction boundaries and mapping between entities and DTOs. It throws meaningful domain exceptions.
  Use a concrete class. Add an interface only when there is a real second implementation.
- **Repository**: a Spring Data JPA interface. Use derived queries first, then `@Query` when needed.
  It contains no business logic.

### DTOs and mapping

- Use Java `record`s for request and response DTOs, with separate request and response types.
- Write mapping by hand, either in the service or as a static `from(entity)` factory on the response record.
  Do not use MapStruct or ModelMapper unless the mapping volume justifies it.
- Never accept client-supplied IDs or server-managed fields (timestamps, status) in create requests.

### Validation

- Use Bean Validation annotations on request DTOs (`@NotBlank`, `@Positive`, `@Size`, `@Email`, ...), together with
  `@Valid` on `@RequestBody`.
- Put `@Validated` on the controller when validating `@PathVariable` or `@RequestParam` values.
- Rules that need data (uniqueness, existence, state transitions) belong in the service, not in annotations.
- Back important invariants with database constraints too, such as `unique` and `nullable = false`.

### Error handling

- All errors are returned as RFC 9457 `ProblemDetail` JSON from `common.exception.GlobalExceptionHandler`.
- Already mapped:
  - `ResourceNotFoundException` → 404
  - Bean Validation failures → 400 with an `errors: [{field, message}]` list
  - malformed JSON, wrong HTTP method and other standard Spring MVC errors → their standard statuses
  - anything unexpected → 500, logged server-side, with a generic message (internals never leak)
- For a new expected failure, create a specific exception (for example a 409 Conflict for duplicates) and map it in
  `GlobalExceptionHandler`. Do not build `ResponseEntity` error bodies inside controllers.
- Never return stack traces, SQL or class names to clients.

### Persistence and transactions

- Put `@Transactional` on service methods that write data, and `@Transactional(readOnly = true)` on read methods
  that touch lazy associations or run several queries. Never put it on controllers.
- `spring.jpa.open-in-view=false` is set, so load everything a response needs inside the service transaction.
- Watch for N+1 queries. Use `JOIN FETCH` or `@EntityGraph` when a response needs associations.
- Use `@Version` optimistic locking when concurrent updates to the same row are a realistic requirement.
- Use `Long` IDs with `@GeneratedValue(strategy = GenerationType.IDENTITY)` unless the requirements say otherwise.
- Use pagination (`Pageable`) for list endpoints that can grow without bound.

### API conventions

- Use plural, noun-based resource paths: `/api/orders`, `/api/orders/{id}`.
- `GET` reads (200), `POST` creates (201 + `Location`), `PUT` replaces and `PATCH` partially updates (200),
  `DELETE` removes (204).
- 400 means invalid input, 404 means not found, 409 means a conflict or duplicate, 422 is only for a business-rule
  violation that is clearly distinct from validation, and 500 means an unexpected error.

### Testing conventions

- Test behaviour, not implementation details. Name tests after the behaviour, for example
  `createOrderReturns409WhenDuplicate`.
- **Service unit tests**: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), covering business rules and edge
  cases.
- **Controller tests**: `@WebMvcTest(XController.class)` + MockMvc + `@MockitoBean` for the service. Check status
  codes, JSON shape, validation errors and error mapping.
- **Repository tests**: `@DataJpaTest`, only for custom queries or constraints, not for inherited CRUD.
- **Integration tests**: `@SpringBootTest` + `@AutoConfigureMockMvc` for the main happy path end to end, when it adds
  value.
- Cover failure cases (not found, invalid input, conflicts), not just happy paths.
- Tests must be deterministic: no reliance on execution order, wall-clock time or random data.
- Use AssertJ for assertions.

### Security and logging

- Never hard-code secrets. Use environment variables or profile config that is not committed.
- Never log passwords, tokens or other sensitive data.
- Validate and sanitize all external input.

## Git workflow

- `main` holds the base harness. Each question goes on its own branch (for example `feature/q1-<short-name>`) and is
  merged through a PR.
- Do not create branches, commit, push or open PRs unless the user explicitly asks.
- Keep each PR scoped to one question.

## Definition of done: senior review

Before declaring a feature complete, review it as a senior engineer:

1. **Architecture**: Is Controller → Service → Repository responsibility clear? Is business logic in the right layer?
   Are there unnecessary dependencies or abstractions?
2. **API design**: Are endpoints RESTful and intuitive? Are methods and status codes correct? Are the request and
   response models appropriate? Is validation handled consistently?
3. **Business logic**: Are happy paths and edge cases handled? Are business rules centralized in the service? Are
   duplicate or conflicting operations handled?
4. **Persistence**: Are entities and repositories well designed? Are database constraints considered? Are
   transaction boundaries right? Is there any unnecessary database access?
5. **Error handling**: Are expected errors meaningful exceptions? Is global handling consistent? Are internals hidden
   from clients?
6. **Testing**: Is important behaviour tested? Are failure cases covered? Are tests readable and deterministic?
7. **Code quality**: Is the code readable, with meaningful names? Is there duplication, oversized methods or classes,
   or unnecessary comments, abstractions or dependencies?
8. **Security**: Are there obvious concerns? No hard-coded secrets, no sensitive data in logs, external input
   validated.
9. **Performance**: Are there N+1 queries, redundant database calls or inefficient loops? Do not optimize
   prematurely.
10. **Interview explainability**: Can the developer explain every important decision and the request flow from
    Controller → Service → Repository? When choosing between reasonable alternatives, document the decision and the
    trade-off.

Then give a short **senior-review summary**:

- What is good
- Any issues found
- Any trade-offs
- What was intentionally kept simple
- Tests run, with their results
- Whether anything should change before the PR
