# AI usage

This solution was developed with OpenAI Codex in IntelliJ IDEA. Codex read the assignment, proposed design choices,
looked up the official CNB feed and framework documentation, and generated the application, tests, Dockerfile and documentation.
The human author must review and understand the submitted code; this document does not claim independent human implementation.

## Steering

The author requested the Ponytail full / KISS approach, Java 21 and Maven with Spring Boot,
H2 file persistence, simple authentication and authorization, Dockerfile, and Actuator/Prometheus.
They restricted currencies to CNB-supported currencies, deferred AI-agent exposure, and explicitly declined publication for now.
They also requested business-oriented TRACE logs and short English comments above phases of longer methods.

Codex applied the local `personal-project-rules`, `ponytail`, `wultra`, and `monet` skills.
Their relevant instructions were: explicit variable types instead of `var`, minimal complete implementation,
thin controllers, coordinated validation/security/persistence/tests/docs, no invented Wultra architecture or response wrappers,
and contextual SLF4J logging without sensitive payloads. The shared personal-rule instruction was provided as AGENTS.md text
in the conversation. Skill files live outside this repository and were not copied here.
No subagents were used.

The author subsequently requested replacing all Java records with Lombok classes and adding this preference to
the shared personal skill. Codex updated all eight types and their callers, preserving JSON fields, validation,
immutable exchange-rate snapshots and password redaction.

The author then requested replacing JdbcTemplate with JPA. Codex mapped the existing tables to Lombok entities,
kept the repository API, used JPA pessimistic locking for wallet updates, and adapted persistence/rollback tests.
The database schema remains unchanged and Hibernate validates it instead of recreating it.

The author clarified that repository methods should use Spring Data query derivation. Codex replaced direct
EntityManager operations with JpaRepository interfaces and derived methods, and moved transactional operations
to persistence services while preserving wallet locking and snapshot replacement semantics.

The author requested one WalletConfigurationProperties class with nested rates/users groups. Codex consolidated
property binding, retained existing YAML keys and password redaction, and tested nested validation. The full test suite
also exposed missing ProblemDetail extension flattening in the standalone error ObjectMapper; Codex registered Spring's mixin.

The author requested three Maven modules: wallet-db, wallet-api, and the main wallet application. Codex moved
entities/repositories/schema to wallet-db, extracted shared REST DTOs to wallet-api, retained application orchestration
and integration tests in wallet, and updated Maven dependencies, Docker packaging and run instructions.

The author changed WalletException to a checked exception and requested concrete subclasses. Codex retained that
choice, grouped amount/rate failures by reason, updated callers and added explicit transaction rollback for checked
wallet failures. Tests cover the typed failure attributes, balance limits, missing rates and rollback of earlier changes.

## Corrections and human decisions

- Codex initially proposed an in-memory-only rate cache. The author identified that rates should survive restart
  and suggested H2; the design now atomically persists the last successful fixing and restores it at startup.
- Codex's first file database configuration used H2's default delayed writes. A runtime test killed the process
  immediately after successful API calls and exposed loss of the latest committed data. The default file URL now sets
  `WRITE_DELAY=0`; the same restart/fallback scenario was repeated to verify the correction.

The author chose the scope, persistence, authentication requirement, coding conventions, and optional features;
they accepted the proposed precision/fallback rules. Codex chose the detailed implementation: Spring Security sessions,
BCrypt, per-wallet database row locks, Spring Data JPA repositories, immutable rate snapshots, and local HTTP test fixtures.
No claims of manual human-written code or final human review are made here.

## Verification

`mvn verify` passed all 18 tests. Automated tests use controlled rates. The packaged JAR also completed the example
against live CNB and restored balances/rates after forced termination with CNB unavailable. Docker execution was not
verified because the local daemon was not running.
The author subsequently requested publishing the project to their GitHub account.
