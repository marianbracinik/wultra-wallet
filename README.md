# Multicurrency wallet

Small Java 21 REST API with Spring Boot, Spring Security, JPA/Hibernate and embedded H2.
Uses Spring Boot 4.1.2 and springdoc 3.2.0; Spring Boot manages the remaining dependency and build plugin versions.
Each authenticated user owns a wallet with independent currency balances. Withdrawals do not exchange currencies;
the total endpoint only calculates a valuation. No money is transferred by requesting a total.

## Modules

The root `pom.xml` is the Maven parent and reactor aggregator for four modules:

| Module | Contents |
|---|---|
| `wallet-api` | Shared REST request/response classes (`MoneyRequest`, `BalanceResponse`, `TotalResponse`), validation constraints and domain exceptions |
| `wallet-db` | JPA entities, Spring Data repositories, H2 dependency and `schema.sql` |
| `rate-fetch-client` | Auto-configured RestTemplate client, CNB response parsing and validation |
| `wallet` | Executable application, controllers, security/configuration, business/persistence services, CNB integration and integration tests |

`wallet` depends on the three library modules. `rate-fetch-client` depends on `wallet-api`; the libraries do not depend on the application.
The client auto-configures a `RateFetchClient` bean and uses `restclient.rates.url`, `restclient.rates.connect-timeout` and `restclient.rates.request-timeout`.
Provide your own `RateFetchClient` bean to replace it. Refresh scheduling, persistence and fallback remain in the application.
HTTP responses are read by RestTemplate without a custom response size limit.
Persistence services remain in the application because they coordinate business validation and snapshot mapping.
Exchange rates reference their snapshot through `snapshot_id`; snapshot metadata and rates are loaded with one fetch join.
Schema initialization adds this foreign key to existing H2 databases and associates stored rates with snapshot 1.
The HTTP paths and JSON fields are unchanged. Run build commands below from the repository root.

## Build and run

Requires JDK 21 (`JAVA_HOME` pointing to it) and Maven 3.9+. No database server is needed.

```sh
mvn clean verify
WALLET_ALICE_PASSWORD='replace-with-a-long-password' WALLET_BOB_PASSWORD='replace-with-another-password' WALLET_SECURE_COOKIE=false java -jar wallet/target/wallet-1.0-SNAPSHOT.jar
```

PowerShell equivalent for the second command:

```powershell
$env:WALLET_ALICE_PASSWORD = 'replace-with-a-long-password'
$env:WALLET_BOB_PASSWORD = 'replace-with-another-password'
$env:WALLET_SECURE_COOKIE = 'false'
& "$env:JAVA_HOME/bin/java.exe" -jar wallet/target/wallet-1.0-SNAPSHOT.jar
```

The two demo identities are `alice` and `bob`; passwords must be 12–72 characters (at most 72 UTF-8 bytes).
Set your own values; no application password is committed. Passwords are BCrypt hashed at startup.
Only disable secure cookies for local HTTP. For deployment, use HTTPS termination and leave secure cookies enabled.

H2 writes to `./data/wallet.mv.db` with `WRITE_DELAY=0` to avoid delayed commit writes.
Balances and the last successful rate snapshot survive restarts.
Sessions do not survive restarts. Override `WALLET_DB_URL` or `WALLET_DB_PASSWORD` if needed.
Preserve `;WRITE_DELAY=0` when overriding the file database URL.
The database directory is ignored by Git; keep it private. H2 console and database TCP access are not enabled.

## API

OpenAPI: http://localhost:8080/v3/api-docs; Swagger UI: http://localhost:8080/swagger-ui.html.
The API uses session cookies and CSRF protection, including on login and logout.
Fetch `/api/auth/csrf` before login and again after successful login, then send its `token` in its `headerName`.
Login accepts `application/x-www-form-urlencoded`; wallet requests accept JSON.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/csrf` | Obtain CSRF token and session cookie |
| POST | `/api/auth/login` | Login with `username`, `password`; returns 204 |
| GET | `/api/auth/me` | Current username |
| POST | `/api/auth/logout` | Invalidate session; returns 204 |
| POST | `/api/wallet/deposits` | Deposit `{"currency":"USD","amount":10}` |
| POST | `/api/wallet/withdrawals` | Withdraw the same request shape |
| GET | `/api/wallet/balances` | Own balances by currency |
| DELETE | `/api/wallet/balances` | Delete all own balances; requires CSRF token, returns 204 |
| GET | `/api/wallet/total?currency=EUR` | Own total and exchange-rate metadata |
| GET | `/actuator/health` | Public status, no internal details |
| GET | `/actuator/prometheus` | Metrics; HTTP Basic authentication, demo identities above |

Wallet endpoints take ownership from the authenticated principal, never from a request parameter.
Unknown JSON properties are rejected. The API supports uppercase currency codes from the CNB snapshot plus CZK.

Example session using PowerShell (while the app runs in another terminal):

```powershell
$base = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable walletSession
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-WebRequest "$base/api/auth/login" -Method Post -WebSession $walletSession -Headers $headers -Body @{ username = 'alice'; password = $env:WALLET_ALICE_PASSWORD } | Out-Null
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $walletSession
$headers = @{ $csrf.headerName = $csrf.token }
Invoke-RestMethod "$base/api/wallet/deposits" -Method Post -WebSession $walletSession -Headers $headers -ContentType 'application/json' -Body '{"currency":"USD","amount":10}'
Invoke-RestMethod "$base/api/wallet/deposits" -Method Post -WebSession $walletSession -Headers $headers -ContentType 'application/json' -Body '{"currency":"EUR","amount":5}'
Invoke-RestMethod "$base/api/wallet/withdrawals" -Method Post -WebSession $walletSession -Headers $headers -ContentType 'application/json' -Body '{"currency":"USD","amount":3}'
Invoke-RestMethod "$base/api/wallet/total?currency=USD" -WebSession $walletSession
Invoke-WebRequest "$base/api/wallet/balances" -Method Delete -WebSession $walletSession -Headers $headers | Out-Null
Invoke-WebRequest "$base/api/auth/logout" -Method Post -WebSession $walletSession -Headers $headers | Out-Null
```

For Swagger's **Try it out**, obtain the token with `/api/auth/csrf` and put its value in **Authorize → csrf**.
Login using the documented form operation, then fetch and authorize the new CSRF token again.
The browser sends its session cookie automatically; no manual JSESSIONID entry is needed.
The README session example handles the complete flow explicitly.

## Decisions

1. **Missing rates:** use the latest published CNB fixing, including before today's fixing and on weekends/holidays.
   CNB publishes around 14:30 Prague time on working days. A prior date is not itself an outage.
   A converted total includes `rateExchangeUsed: true`, `rateDate`, `ratesFetchedAt` (last successful retrieval), and `freshRateSnapshot`.
   Without conversion `rateExchangeUsed` is false and rate metadata fields are omitted.
2. **Rate units:** retain the quoted units and CZK amount. Convert A to B with
   `amount × rateA × unitsB / (unitsA × rateB)`, avoiding premature normalization/rounding.
3. **CNB unavailable:** lazily refresh at most once per five minutes, including after failures; use 2s connect and 4s request timeouts.
   Requests share one immutable snapshot. Validate the entire response (maximum 64 KiB), reject duplicates, nonpositive rates,
   future dates and regression to an older date. Save the complete snapshot in one H2 transaction before publishing it in memory.
   At startup reload it from H2 and attempt a refresh on the first request that needs rates.
   On fetch failure retain it and return `freshRateSnapshot: false` for converted totals. No automatic age cutoff: the date is explicit,
   and this demo favors availability. If no snapshot exists, a required conversion returns 503, never a partial total.
   CZK deposits and withdrawals, withdrawals of existing holdings and same-currency valuations do not need CNB.
   Foreign deposits need a supported currency from a cached or freshly retrieved snapshot.
4. **Precision:** `BigDecimal`, DB `DECIMAL(30,4)`, positive input below `10^26`, and currency-specific ISO fractional digits.
   Excess nonzero decimals are rejected; trailing zeros are accepted. XDR has no ISO minor unit, so this application uses four decimals.
   Conversion division uses DECIMAL128 (34 significant digits); sum converted values before final `HALF_EVEN` rounding to target minor units.
   JSON decimals should be handled with a decimal type by clients too.
5. **Concurrency:** JPA `PESSIMISTIC_WRITE` on the wallet row serializes balance changes, including concurrent first deposits.
   Validation against the current balance and its update share one transaction. DB constraints prohibit negative balances.
   A total reads all holdings in a single SELECT and uses a single rate snapshot; no DB lock is held during a network call.
6. **Errors:** RFC 9457 Problem Details with a stable `code`. Invalid input or unsupported currency: 400;
   missing/invalid credentials: 401; denied access/CSRF: 403; insufficient funds: 409; unavailable required rates: 503.
   Unexpected failures return a generic 500. Security filtering may reject a request before validating its body.
   Domain errors extend the abstract checked `WalletException`: `AmountException` distinguishes invalid input from
   balance limits; `RateException` distinguishes unavailable rates from a missing currency rate. Other concrete types
   cover unsupported currency, insufficient funds and a missing wallet. Context attributes remain on Java exceptions;
   HTTP statuses, error codes and public response fields are unchanged. Wallet transactions explicitly roll back on `WalletException`.
7. **CZK:** a synthetic rate of exactly 1 CZK per 1 CZK, independent of CNB availability.

Official source: [CNB daily text feed](https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt),
[publication rules](https://www.cnb.cz/en/financial-markets/foreign-exchange-market/central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing).
The exchange-rate feed changes which currencies are supported. If a held currency disappears, withdrawals remain possible,
but valuations requiring its missing rate return 503.

## Security and observability

Spring Security handles password verification, session fixation protection and CSRF. Sessions expire after 15 minutes idle;
cookies are HttpOnly, SameSite=Strict, Secure by default. No JWT implementation, custom cryptography, registration, or password reset.
HTTP Basic is enabled only for the read-only Prometheus endpoint so an external scraper need not manage a login session;
it does not authenticate wallet requests. Use HTTPS for both sessions and metrics.

Actuator exposes health and Prometheus only. Framework metrics cover HTTP/JVM/DB connections.
Enable business flow logs with `LOGGING_LEVEL_COM_WULTRA_WALLET=TRACE`.
Logs describe actions and safe metadata, without credentials, authorization headers, balances or request bodies.
These are operational logs, not a durable financial audit ledger.

## Docker

```sh
docker build -t wultra-wallet .
docker run --rm -p 127.0.0.1:8080:8080 -v wallet-data:/app/data -e WALLET_ALICE_PASSWORD -e WALLET_BOB_PASSWORD -e WALLET_SECURE_COOKIE=false wultra-wallet
```

Export the password environment variables first. This is a local HTTP example. The image runs as a non-root user and
the build runs tests. Named volume `wallet-data` preserves balances and rates when the container is replaced.

## Tests and limits

`mvn verify` tests API validation, cross rates, rate units, rounding, ownership isolation, authentication, session rotation,
CSRF, monitoring protection, concurrent withdrawals, corrupt/oversized CNB responses, fallback/recovery,
and H2 snapshot persistence across database reopening. Network tests use a local HTTP server, not live CNB.

Verified locally: all 18 tests pass; the packaged JAR completed the example workflow against live CNB,
then restored the same valuation from file H2 after forced process termination with CNB unavailable.
Dockerfile execution was not verified because no Docker daemon was running.

Single application instance with embedded H2; two configured demo users, no account lifecycle, transfer endpoint,
transaction history, distributed sessions or idempotency keys. Retrying a successful deposit/withdrawal can apply it twice.
No login rate limiter: deployment would need an ingress limit before public exposure. No schema migration framework;
`wallet-db/src/main/resources/schema.sql` initializes this small fixed schema and is not an upgrade mechanism.
Hibernate validates the existing schema (`ddl-auto=validate`); it does not recreate tables.
Repositories use Spring Data JPA (`JpaRepository`) with derived methods such as `findByOwner` and `findAllByIdOwner`.
`@Lock(PESSIMISTIC_WRITE)` protects wallet changes. Persistence services own transaction boundaries and validation;
managed balance updates are persisted by dirty checking inside the transaction. No handwritten JPQL is needed.
Open EntityManager in View is disabled, so persistence contexts remain within database operations.
Cache can remain old throughout a prolonged outage, with its date exposed. No historical rate archive.
No MCP server or Prometheus deployment in this step.
