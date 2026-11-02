# AI usage

This solution was developed by the author with OpenAI Codex in IntelliJ IDEA. The author directed the scope,
architecture, coding conventions, and revisions, made code changes, and tested the application locally.
Codex proposed implementation details and generated and revised much of the application, tests, Docker
configuration, and documentation. The author remains responsible for the submitted solution.

## Human decisions and hands-on work

The author requested Java 21, Maven, Spring Boot, H2 persistence, authentication, Docker, and basic
observability, with a KISS approach. They specified the module boundaries: wallet-api for shared models,
wallet-db for persistence, wallet-rate-fetch-client for CNB retrieval and parsing, and wallet for application
orchestration. They directed the move from JdbcTemplate to JPA and Spring Data repositories, consolidated
configuration into nested property groups, and steered an overengineered rate client toward RestTemplate.
Refresh scheduling, persistence, and fallback stayed in application services. The author and Codex also
updated Spring Boot to 4.1.2.

The author supplied explicit-type and Lombok-class preferences, changed WalletException to a checked
exception, and requested concrete subclasses, contextual logging, and Javadoc. They extracted the currency
conversion into CurrencyUtil.getAmount and asked Codex to review the refactoring and explain quoted rate
units and rounding. They requested a request/response logging filter, then challenged its complexity and
asked for a simpler implementation. They also tested the CSRF/login/deposit flow and reported Docker
build failures for diagnosis.

## AI mistakes and human corrections

- Codex initially proposed only an in-memory rate cache. The author identified that rates should survive
  restarts and suggested H2. The implementation now persists and restores the last successful snapshot.
- The client was overengineered, and the author explicitly requested simplification. Local Docker attempts
  also exposed an obsolete module path and a broad .dockerignore rule that excluded the Java data package.
  Codex corrected the packaging after the author's feedback.

## Tools and verification

Codex consulted the assignment, CNB sources, and framework documentation, implemented the requested changes,
and wrote tests for validation, conversion, concurrency, security, and persistence. Local skills used during
the work included personal-project-rules, ponytail, wultra, and monet; the author supplied shared rules as
AGENTS.md instructions in the conversation. These external skill files were not committed. No subagents
were used. Specific human review and edits above do not imply a complete final review of every generated line.

The final verification pass caught an invalid Set-Cookie value in Codex's logging test; Codex fixed the fixture.
A clean Maven build passed all 29 tests. The Docker build and API workflow also passed, including persisted
balances and cached-rate fallback after container recreation with CNB unavailable. Details are in README.md.
