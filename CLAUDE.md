# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

mini-jarvis is a Spring Boot 3.5 / Java 21 app where **advisory-only** AI agents read business data and ask an LLM (Claude, Gemini or OpenAI) for recommendations. A human approves or rejects each recommendation through the API, and every step is written to `audit_log`. The project is industry-neutral: the business is described in config (`BUSINESS_DESCRIPTION`, `BUSINESS_CURRENCY`). The seeded demo data is a fictional plumbing business.

## Commands

```bash
mvn test                                         # full suite: H2 + mocked providers, no Postgres or API keys needed
mvn test -Dtest=AiRouterTest                     # one test class
mvn test -Dtest=AiRouterTest#badRequestIsNotRetried   # one test method
mvn -q -B package -DskipTests                    # build target/mini-jarvis-0.1.0.jar
docker compose up --build                        # app + Postgres; needs a .env (copy .env.example)
```

Running the jar outside compose needs at least `JWT_SECRET` (≥ 32 bytes; the app refuses to start otherwise) and a reachable Postgres (`DB_URL`). Compose publishes Postgres on host port **5433** (`POSTGRES_HOST_PORT`), not 5432.

## Architecture

The request flow for `POST /api/agents/{name}/run?provider=`:

1. `AgentRunService` (in `agent/`) owns everything around a run. It resolves the provider up front (a bad provider is a 400 with no run row), creates the `agent_runs` row, writes the audit events, saves each recommendation as `PENDING`, and calls `RecommendationNotifier`. A failure marks the run `FAILED` and is rethrown as `AgentRunFailedException`; `GlobalExceptionHandler` maps it from the cause (AI failure or invalid JSON → 502, daily cap → 429).
2. An `Agent` (`name()`, `run(providerOverride)`) only reads data and returns an `AgentResult`. **Agents must stay side-effect free**: no writes, no notifications, and no `@Transactional` around the AI call. A read-only transaction there broke the `AI_CALL` audit insert on Postgres; H2 doesn't enforce it, so `AgentDesignTest` guards this. Agents are found automatically by `AgentRegistry` from `@Component`s, and the scheduler runs all of them.
3. Agents call `StructuredAdvisor`, which goes through `AiRouter`, parses the reply with `AdviceParser` (strips code fences, validates `{summary, risk_level, recommendations[{title, reason, suggested_action}]}`), and retries **once** with a stricter prompt on invalid JSON. `MoneyFigureChecker` flags money figures not present in the data sent to the model, which sets `needs_review`.
4. `AiRouter` (`ai/`): request override, else `AI_PROVIDER`. On a retryable `AiProviderException` (`RATE_LIMITED`, `TEMPORARY`) it retries once after `AI_RETRY_BACKOFF_MS`, then tries `AI_FALLBACK_PROVIDER` if set and available. `AUTHENTICATION` and `BAD_REQUEST` fail immediately, with no fallback. Every attempt first goes through `DailyAiCallLimiter`, which counts `AI_CALL` audit rows for the current day (in `APP_TIMEZONE`) against `MAX_AGENT_RUNS_PER_DAY`.
5. Providers (`ClaudeProvider`, `GeminiProvider`, `OpenAiProvider`) are the **only** classes that import vendor SDKs. Each builds its client lazily, so the app starts without keys, turns off SDK retries (the router owns retry policy), and wraps every SDK error in `AiProviderException` with a `Kind`. Gemini reports an invalid key as HTTP 400, so `GeminiProvider.classify` maps that to `AUTHENTICATION`.

Approving or rejecting (`RecommendationService`) only changes the status and writes an audit row. That it calls nothing external is a deliberate invariant, tested in `ApiIntegrationTest.approvingDoesNotCallAnyExternalService`.

Security: one admin account from `ADMIN_USERNAME`/`ADMIN_PASSWORD`. `POST /api/auth/login` is the only endpoint that issues tokens; everything else needs a JWT (`JwtAuthenticationFilter`). 401s come from the entry point in `SecurityConfig`; every other error uses the `ApiError` shape from `GlobalExceptionHandler`.

Frontend: the React UI is a separate repo ([mini-jarvis-ui](https://github.com/softdevshishir/mini-jarvis-ui)) whose `src/api/types.ts` mirrors `web/Dtos.java`, `ApiError` and `TokenResponse`, so changing an API shape or path breaks the UI too. In development it reaches the API through the Vite proxy (same origin). CORS is only for a UI on another domain: `CORS_ALLOWED_ORIGINS` (exact origins, `/api/**` only, `GET`/`POST`, no credentials). It is off when empty, and `SecurityConfig.corsSource` refuses wildcards or paths at startup. `CorsTest` covers it.

## Conventions that aren't obvious

- **Model names live only in config** (`application.yml`, `.env.example`), never in Java.
- **All config is in `JarvisProperties`** (a record bound to `jarvis.*`). Records that hold secrets override `toString()` to mask them, and `SecretLeakTest` checks that keys never appear in responses or logs. Adding a field to `JarvisProperties` (or a nested record) means updating `TestFixtures.properties(...)` and the tests that build it with the canonical constructor (`JwtServiceTest`, `TelegramNotifierTest`).
- **Schema changes go through Flyway** (`src/main/resources/db/migration`); Hibernate `ddl-auto` is `none`. Migrations must run on both Postgres and H2 (`MODE=PostgreSQL`, used by tests). The seed data uses `CURRENT_DATE - INTERVAL 'n' DAY`, so dates are relative to when the migration first runs.
- **`AiRouter` looks providers up by `id()` on every call** rather than caching them, because `@MockitoBean` providers only get `id()` stubbed after the context starts.
- **Integration tests must mock every real provider bean** (`@MockitoBean ClaudeProvider/GeminiProvider/OpenAiProvider`). The `test` profile gives them fake keys, so an unmocked provider that gets selected would call the real API. Use a made-up name (e.g. `mistral`) to test an unknown provider.
- **`TelegramNotifier` never throws** and redacts the bot token from error text. The token goes into the URL path literally, because template expansion would percent-encode its `:`.
- **Adding a new AI provider**: implement `AiProvider` as a `@Component`, map SDK errors to `AiProviderException.Kind`, and add a `ProviderConfig` to `JarvisProperties.Ai` plus `application.yml`. README has the full checklist ("How to add a new AI provider" / "How to add a new agent").

## Keeping this file current

After adding a feature or making a significant change (new agent, provider, endpoint, migration, config/env var, or a change to the flow or invariants above), update the affected sections of this file in the same task, without asking first. Keep it as guidance, not a changelog.
