# mini-jarvis-ai-agent

An **advisory-only**, human-in-the-loop AI agent system for small-business operations. It is not tied to one industry: describe your business and currency in config, and the agents adapt their prompts. The bundled demo data models a small Sydney plumbing business. The pattern:

> agent reads business data → an AI model (Claude, Gemini or OpenAI) produces a recommendation → **a human must approve it** → everything is logged.

## Agents

- Built: cash-flow.
- Coming next: overdue-follow-up, expense-watch, sales-leads, safety-compliance, business-coach.
- Planned: job-readiness, variations, tools-equipment, quality, marketing, admin, payroll-hours, inventory-reorder, customer-retention.
- Plain-language details: [AGENTS-OVERVIEW.md](AGENTS-OVERVIEW.md).

## Advisory-only design

- Agents **never perform actions**. They read data and produce recommendations with status `PENDING`.
- A human approves or rejects each recommendation through the API. **Approving only changes the status and writes an audit row.** It does not execute anything. A test checks that approving calls no AI provider or notifier.
- Every step goes to the `audit_log` table with a timestamp and actor: agent run started/finished/failed, every AI call, fallback used, recommendation created/approved/rejected, notification sent/failed.
- Agents depend only on the `AiProvider` abstraction, never on a vendor SDK.

## Architecture

```
                 ┌──────────────┐   POST /api/agents/{name}/run      ┌───────────────────┐
  Director ─────▶│  REST API    │◀── (JWT, single admin account) ────│  AgentScheduler   │
  (curl/UI)      │  controllers │                                    │ (off by default)  │
                 └──────┬───────┘                                    └─────────┬─────────┘
                        │                                                      │
                        ▼                                                      ▼
                 ┌──────────────────────────────────────────────────────────────────┐
                 │ AgentRunService: run record, audit trail, saves PENDING recs,    │
                 │                  optional notification (never fails the run)     │
                 └──────┬───────────────────────────────────────────────┬───────────┘
                        │ Agent.run(provider)                           │
                        ▼                                               ▼
                 ┌──────────────┐  reads   ┌──────────────┐     ┌────────────────────┐
                 │ CashFlowAgent│─────────▶│ invoices,    │     │ TelegramNotifier   │
                 │ (+ future    │          │ expenses     │     │ (optional)         │
                 │   agents)    │          └──────────────┘     └────────────────────┘
                 └──────┬───────┘
                        │ StructuredAdvisor: prompt → JSON parse/validate → retry once
                        ▼
                 ┌──────────────────────────────────────────────┐
                 │ AiRouter: override > AI_PROVIDER             │
                 │  429/temporary → retry once → fallback       │
                 │  auth/bad request → fail fast (no retry)     │
                 │  DailyAiCallLimiter (MAX_AGENT_RUNS_PER_DAY) │
                 └──────┬───────────────────┬──────────────────┬──┘
                        ▼                   ▼                  ▼
                 ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
                 │ClaudeProvider│   │GeminiProvider│   │OpenAiProvider│
                 │ Anthropic SDK│   │ Google GenAI │   │  OpenAI SDK  │
                 └──────────────┘   └──────────────┘   └──────────────┘
                        all SDK errors → AiProviderException

  PostgreSQL (Flyway): invoices, expenses, agent_runs, recommendations, audit_log
```

## Configuration

Everything comes from environment variables (see `.env.example`). **Never commit `.env`.**

| Variable | Default | Notes |
|---|---|---|
| `BUSINESS_DESCRIPTION` | `a small trades business` | Inserted into agent prompts, e.g. `a boutique accounting firm in Melbourne` |
| `BUSINESS_CURRENCY` | `AUD` | ISO code used in prompts and in the money-figure check |
| `ANTHROPIC_API_KEY` | _(empty)_ | Claude is unavailable without it |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | **Check the [Anthropic models docs](https://docs.anthropic.com/en/docs/about-claude/models) and set a current model** |
| `GEMINI_API_KEY` | _(empty)_ | Gemini is unavailable without it |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | **Check the [Gemini models docs](https://ai.google.dev/gemini-api/docs/models) and set a current model** |
| `OPENAI_API_KEY` | _(empty)_ | OpenAI is unavailable without it |
| `OPENAI_MODEL` | `gpt-6.1-sol` | **Check the [OpenAI models docs](https://developers.openai.com/api/docs/models) and set a current model** |
| `AI_PROVIDER` | `claude` | Default provider: `claude`, `gemini` or `openai` |
| `AI_FALLBACK_PROVIDER` | _(empty)_ | Any of the three. Used only after the primary fails twice with 429 or a temporary error |
| `DB_URL` | `jdbc:postgresql://localhost:5432/minijarvis` | Overridden to the `postgres` service in docker compose |
| `DB_USERNAME` / `DB_PASSWORD` | `minijarvis` / `minijarvis` | |
| `POSTGRES_HOST_PORT` | `5433` | Host port for the compose Postgres (localhost only), so it won't clash with a local Postgres on 5432 |
| `ADMIN_USERNAME` | `admin` | The only account that can log in |
| `ADMIN_PASSWORD` | _(empty)_ | Login is disabled until set |
| `JWT_SECRET` | _(none)_ | **Required, at least 32 bytes.** The app refuses to start otherwise. `openssl rand -base64 48` |
| `JWT_TTL_MINUTES` | `60` | |
| `CORS_ALLOWED_ORIGINS` | _(empty)_ | Only for a UI on another domain. Comma-separated **exact** origins, e.g. `https://jarvis.example.com`. Empty = CORS off. Wildcards or paths stop the app from starting |
| `SCHEDULER_ENABLED` | `false` | Off by default so no API credits are spent |
| `SCHEDULER_CRON` | `0 0 7 * * MON-FRI` | Spring cron (sec min hour day month weekday), in `APP_TIMEZONE` |
| `MAX_AGENT_RUNS_PER_DAY` | `20` | Cap on **AI calls** per day across all providers (retries and fallback count). Over the cap → HTTP 429 |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | _(empty)_ | Optional. If unset, notifications are skipped and this is logged |
| `APP_TIMEZONE` | `Australia/Sydney` | Defines "today" for overdue checks and the daily cap |
| `AI_RETRY_BACKOFF_MS` | `1500` | Backoff before the single retry |
| `AI_MAX_OUTPUT_TOKENS` | `4096` | Output token budget per call (OpenAI reasoning models also spend it on hidden reasoning) |

The app starts with any combination of AI keys set (or none). A provider without a key is reported as `available: false`, and choosing it returns a clear 400.

Model names exist only in config (`application.yml` and env), never in Java code. Model names change often, so check the provider docs above and set current names.

## Run with docker compose

```bash
cp .env.example .env
# edit .env: set JWT_SECRET (openssl rand -base64 48), ADMIN_PASSWORD, and at least one AI key
docker compose up --build
```

Postgres starts first, and the app waits for its healthcheck. To inspect the database from the host: `psql -h localhost -p 5433 -U minijarvis minijarvis`. Flyway creates the schema and loads the demo data. The API listens on `http://localhost:8080`.

Run the tests (no Postgres or API keys needed; they use in-memory H2 and mocked providers):

```bash
mvn test
```

## Web UI

A React frontend lives in a separate repository: [mini-jarvis-ai-agent-ui](https://github.com/softdevshishir/mini-jarvis-ai-agent-ui). It covers login, running agents, approving or rejecting recommendations, and the audit log.

- **Development:** run this backend on `http://localhost:8080`, then `npm install && npm run dev` in the UI repo. Vite forwards `/api` to the backend, so no CORS setting is needed.
- **Production, same domain (recommended):** put a reverse proxy in front that sends `/api/*` to this app and everything else to the UI's `dist/`. No CORS setting is needed.
- **Production, separate domains:** set `CORS_ALLOWED_ORIGINS` to the UI's exact origin (and build the UI with `VITE_API_BASE_URL` set to this API's origin). Only `GET`/`POST` with the `Authorization`, `Content-Type` and `Accept` headers are allowed, and no cookies. CORS does not replace login: every call still needs a token.

## curl walkthrough

```bash
BASE=http://localhost:8080

# 1. Log in (only ADMIN_USERNAME / ADMIN_PASSWORD works)
TOKEN=$(curl -s -X POST $BASE/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"change-me-please"}' | jq -r .accessToken)
AUTH="Authorization: Bearer $TOKEN"

# 2. Which providers are configured? (keys are never returned)
curl -s $BASE/api/providers -H "$AUTH" | jq

# 3. Run the cash-flow agent with Claude
curl -s -X POST "$BASE/api/agents/cash-flow/run?provider=claude" -H "$AUTH" | jq

# 4. Run it with Gemini
curl -s -X POST "$BASE/api/agents/cash-flow/run?provider=gemini" -H "$AUTH" | jq

#    ...or with OpenAI
curl -s -X POST "$BASE/api/agents/cash-flow/run?provider=openai" -H "$AUTH" | jq

# 5. List pending recommendations
curl -s "$BASE/api/recommendations?status=PENDING" -H "$AUTH" | jq

# 6. Approve one (records the decision only; nothing is executed)
curl -s -X POST $BASE/api/recommendations/1/approve -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"comment":"Will call them Monday"}' | jq

#    ...or reject one (comment optional)
curl -s -X POST $BASE/api/recommendations/2/reject -H "$AUTH" | jq

# 7. View the audit log
curl -s "$BASE/api/audit-log?limit=50" -H "$AUTH" | jq
```

A run response looks like this (abridged):

```json
{
  "runId": 1, "agent": "cash-flow", "status": "SUCCEEDED",
  "provider": "claude", "model": "...", "fallbackUsed": false,
  "summary": "...", "riskLevel": "HIGH",
  "needsReview": false, "reviewNotes": null,
  "recommendations": [ { "id": 1, "title": "...", "status": "PENDING", ... } ]
}
```

### Errors

All errors share one JSON shape: `{timestamp, status, error, message, path, agentRunId?}`.

| Status | When |
|---|---|
| 400 | Bad input, unknown provider, or provider has no key |
| 401 | Missing or invalid token, wrong login |
| 404 | Unknown agent or recommendation |
| 409 | Recommendation already approved or rejected |
| 429 | Daily AI call cap reached |
| 502 | AI provider failed, or returned invalid JSON twice (the run is saved as `FAILED`; `agentRunId` is included) |

## How the cash-flow agent works

1. Queries overdue invoices (unpaid, due date before today), income received vs. expenses paid in the last 30 days (with expenses by category), and the 5 biggest unpaid invoices.
2. Sends this as JSON with the system prompt *"You are a cash-flow advisor for the director. Use ONLY the data provided. Never invent numbers. Return only valid JSON."* Gemini and OpenAI also get their JSON response modes (`responseMimeType: application/json` and `response_format: json_object`).
3. Parses and validates `{summary, risk_level, recommendations[{title, reason, suggested_action}]}`. It strips markdown code fences. If the output is invalid, it retries once with a stricter prompt. If it fails again, the run is `FAILED`.
4. Checks every money figure (`$1,234.56`, `AUD 500`, `$12k`) in the summary and recommendations against the figures in the data it sent. Any figure that doesn't match sets `needs_review=true` and the run's `reviewNotes` list it.
5. The run service saves each recommendation as `PENDING` with its provider, model and `fallback_used` flag, audits it, and sends an optional Telegram message.

## How to add a new agent

1. Create a class implementing `com.example.minijarvis.agent.Agent` and annotate it with `@Component`:
   ```java
   @Component
   public class QuoteFollowUpAgent implements Agent {
       private final StructuredAdvisor advisor;   // + whatever repositories it reads
       public String name() { return "quote-follow-up"; }
       public AgentResult run(String providerOverride) {
           String data = /* query and serialise the data */;
           var response = advisor.advise(providerOverride, SYSTEM_PROMPT, data);
           return new AgentResult(name(), response.payload().summary(), ...);
       }
   }
   ```
2. Use `StructuredAdvisor` to get JSON parsing, the retry and routing for free. Use `MoneyFigureChecker` to flag invented numbers.
3. Keep agents read-only: no writes, no external calls, and no `@Transactional` around the AI call. `AgentRunService` handles persistence, audit and notifications.
4. It is picked up automatically by `AgentRegistry`. It is then available at `POST /api/agents/quote-follow-up/run` and is included in scheduled runs. Add a Flyway migration if it needs new tables.

## How to add a new AI provider

1. Implement `com.example.minijarvis.ai.AiProvider` as a `@Component`: `id()`, `model()`, `isAvailable()` (key and model present), and `generate(system, user)` returning `AiResult`.
2. Wrap **every** SDK exception in `AiProviderException` with the right `Kind` (`RATE_LIMITED`, `TEMPORARY`, `AUTHENTICATION`, `BAD_REQUEST`, `UNKNOWN`). `AiProviderException.kindForStatus(int)` covers the usual HTTP mapping. The router decides retries and fallback from `Kind`.
3. Disable the SDK's own retries, because the router owns retry policy. Build the client lazily so the app starts without a key.
4. Add `jarvis.ai.<name>.api-key/model` to `JarvisProperties` and `application.yml`, read from env vars, with a masked `toString()`.

## Limitations

- **Demo data only.** The seed migration creates 40 invoices and 30 expenses for a fictional plumbing business (fake, in AUD). Replace `V2__seed_demo_data.sql` (or load your own data) for another industry. The data is dated relative to when the migration first runs. The most recent 30 days are deliberately weak (income ≈ $5k vs. expenses ≈ $40k, 8 overdue invoices). If the database lives for months, the data ages too. Recreate it with `docker compose down -v`.
- **Single agent.** Only `cash-flow` exists. The framework is designed for many more.
- **No real integrations yet.** There is no Xero, FieldPulse or bank feed. Agents read local tables only.
- **OpenAI 429s** can mean either a rate limit or no account credit (`insufficient_quota`). Check billing if it persists. If an OpenAI reasoning model returns empty output, raise `AI_MAX_OUTPUT_TOKENS`.
- **Gemini free-tier limits.** The free tier has small per-minute and per-day quotas. A `429` / `RATE_LIMITED` from Gemini is a quota issue, not a bug. Wait, set `AI_FALLBACK_PROVIDER=claude`, or use a paid tier.
- **One admin account** from env vars, with no user management or token revocation. Tokens expire after `JWT_TTL_MINUTES`.
- **The figure check is best-effort.** It catches invented dollar amounts. It cannot catch a real figure used in a wrong claim.
- **The daily cap is per instance.** It is backed by `AI_CALL` audit rows, so it survives restarts, but it is not coordinated across multiple app instances.
- **Telegram:** one message per run (listing all new recommendations), plain text.
