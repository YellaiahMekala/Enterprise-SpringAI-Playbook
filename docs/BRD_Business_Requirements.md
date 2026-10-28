# Business Requirements Document (BRD)

**Product:** Enterprise Spring AI Playbook: a secure, production-minded AI backend service
**Version:** 1.0 · **Status:** Baseline implemented · **Last updated:** 2026-10-28
**Document type:** Business Requirements Document

> **How to read this document.** The Playbook is a reference implementation built to demonstrate how an enterprise Java backend should expose LLM capabilities safely. The business context below is a *representative scenario* (an internal AI gateway at a regulated enterprise), not a description of a real client. Success metrics are **targets and measurement approaches**, not measured production results. Where something is built and tested, the status says so; where it is not, it is listed as planned.

---

## 1. Document control

| Item | Detail |
|---|---|
| Owner | Project author (Senior Java / Backend AI Integration engineer) |
| Reviewers | Engineering lead, security reviewer, product owner (roles, in a team setting) |
| Related documents | [Technical Design Document](TDD_Technical_Design.md) · [README](../README.md) |
| Requirement ID scheme | `BR-nn` business requirement · `FR-nn` functional · `NFR-nn` non-functional |

### Revision history

| Version | Date | Change |
|---|---|---|
| 1.0 | 2026-10-28 | First baseline, aligned to the implemented system (36 automated tests) |

---

## 2. Executive summary

Enterprises want to add generative AI to existing Java systems, but the prototype-to-production gap is wide: a working notebook demo does not give authentication, input validation, conversation privacy, bounded cost, safe tool use, tested behaviour or a deployable artifact.

The Playbook closes that gap with a single Spring Boot service that exposes chat with durable memory, tool-assisted answers, image generation and understanding, and speech-to-text and text-to-speech behind **API-key authentication, validated REST contracts, a consistent error model and an automated test suite**. It is built on Spring Boot 4 and Spring AI 2 so the integration layer stays provider-agnostic.

**Value proposition:** *GenAI is only as valuable as the backend that scales, secures and monitors it.* The Playbook shows that backend.

---

## 3. Business context and problem statement

### 3.1 Problem
| # | Problem | Consequence if unaddressed |
|---|---|---|
| P1 | AI prototypes expose models directly with no authentication | Uncontrolled access and spend; no accountability per caller |
| P2 | Conversation history is global or in-memory | Users can see each other's context; history is lost on restart; cannot scale to several instances |
| P3 | Model calls and third-party tool calls fail in uncontrolled ways | Raw provider errors, stack traces or credentials leak to clients; poor reliability |
| P4 | User text flows into prompts and tool URLs unchecked | Prompt-injection and request-manipulation risk |
| P5 | LLM-backed code is rarely tested without a paid key | Regressions go unnoticed; upgrades are risky |
| P6 | Framework generations change quickly (Spring AI 1.x to 2.x) | Dependency conflicts and broken builds |

### 3.2 Representative scenario
An internal platform team at a regulated enterprise (for example a bank's operations or reconciliation group) wants a **shared AI gateway** that product teams can call for chat, document or image understanding, and voice features, without each team re-solving security, memory and error handling. The Playbook is the reference for that gateway's core.

---

## 4. Business objectives and success measures

| ID | Objective | Measure of success | Target | Status |
|---|---|---|---|---|
| BO-1 | Prevent anonymous use of paid AI capabilities | Share of `/api/**` requests without a valid key that are rejected | 100% rejected with 401 | **Met** (automated test) |
| BO-2 | Isolate every caller's conversation | Cross-caller leakage in tests | 0 leaks | **Met** (automated test) |
| BO-3 | Give clients a predictable error experience | Responses that expose provider messages, hosts or credentials | 0 | **Met** (automated test) |
| BO-4 | Make the service verifiable without a paid model key | Tests that run offline | Full suite passes in CI with no key | **Met** (35 pass; 1 needs Docker) |
| BO-5 | Keep conversation cost bounded | Max messages replayed per request | Configurable cap (default 20) | **Met** |
| BO-6 | Be deployable by any engineer in minutes | Steps from clone to running | One command with Docker Compose | **Built, not yet run end to end** |
| BO-7 | Demonstrate observable operations | Metrics, traces, token cost per caller | Dashboards and alerts | **Planned** |

---

## 5. Scope

### 5.1 In scope (implemented)
- Authenticated chat with durable per-caller memory (MySQL, sliding window)
- Prompt-policy advisor (tutor-style answers)
- Tool-assisted assistant: date and time, weather, news
- Prompt templating with validated variables (movie recommendation example)
- Image generation, storage and download; image understanding (vision)
- Audio: transcription with translation, and text-to-speech
- API-key security, input validation, uniform error model
- Health probes, containerization, CI pipeline, automated tests

### 5.2 Out of scope (this release)
| Item | Reason / future home |
|---|---|
| End-user identity, SSO, JWT/OAuth2 | Roadmap R1; API key models service-to-service access |
| Retrieval-augmented generation (RAG) over enterprise documents | Covered in a separate portfolio project |
| Multi-tenant administration UI | Not needed for the reference |
| Cost accounting and billing | Roadmap R4 |
| Model fine-tuning or hosting | Provider-hosted models only |

---

## 6. Stakeholders and personas

| Stakeholder | Interest | Needs from the system |
|---|---|---|
| Product team (API consumer) | Add AI features quickly | Stable, documented REST API; clear errors |
| Platform engineering | Operate the gateway | One-command deployment, health probes, CI |
| Security and compliance | Control access and data exposure | Authentication, no secret or provider leakage, auditability (planned) |
| Finance / FinOps | Control AI spend | Bounded context, per-caller usage (planned) |
| Hiring manager / reviewer | Judge engineering quality | Readable architecture, tests, documented decisions and trade-offs |

---

## 7. Business requirements

Priority uses MoSCoW (M = Must, S = Should, C = Could). Status: **Done** (implemented and tested), **Partial**, **Planned**.

| ID | Requirement | Pri | Status | Verified by |
|---|---|---|---|---|
| BR-01 | Only authenticated callers may use AI capabilities | M | Done | `ApiWebTests`, `ApiKeyAuthenticationFilterTests` |
| BR-02 | Each caller has a private, persistent conversation | M | Done | `ChatServiceMemoryTests`, MySQL integration test |
| BR-03 | Bound the cost and latency of conversation context | M | Done | Window size setting (`app.chat.max-messages`) |
| BR-04 | The assistant can use approved external tools (time, weather, news) | S | Done | `DateTimeToolTests`, `WeatherToolTests`, `NewsToolTests`, live run |
| BR-05 | Failures never expose internals to callers | M | Done | `ApiWebTests` (502 mapping, no leakage on 500) |
| BR-06 | Invalid input is rejected before any paid call | M | Done | `ApiWebTests` (400 and 415 cases) |
| BR-07 | Support multimodal use: images and voice | S | Done | Live run: image, STT, TTS |
| BR-08 | Provider can be changed with minimal code change | S | Done (by design) | Code depends on `ChatClient`, not a vendor SDK |
| BR-09 | Quality is enforced automatically on every change | M | Done | CI workflow runs `mvn verify` |
| BR-10 | The service is deployable as a container | S | Partial | Dockerfile and Compose written; first build pending |
| BR-11 | Operations can observe health, usage and cost | S | Partial | Health probes done; metrics and tracing planned |
| BR-12 | Detect and block prompt injection and PII leakage | S | Planned | Guardrail advisors (roadmap R3) |
| BR-13 | Resilient to provider outages (retry, circuit breaker) | S | Planned | Roadmap R2 |

---

## 8. Functional requirements

| ID | Requirement | Maps to | Endpoint |
|---|---|---|---|
| FR-01 | Return a model answer for a text prompt, remembering the caller's earlier messages | BR-01, 02, 03 | `POST /api/v1/chat` |
| FR-02 | Same, with a tutor-style response policy applied centrally | BR-02, 08 | `POST /api/v1/chat/tutor` |
| FR-03 | Answer using tools when needed (date and time, weather, news) | BR-04 | `POST /api/v1/assistant/ask` |
| FR-04 | Produce a multi-tool day plan | BR-04 | `POST /api/v1/assistant/plan-my-day` |
| FR-05 | Recommend a movie from validated parameters (stateless) | BR-06 | `GET /api/v1/recommendations/movie` |
| FR-06 | Generate an image from a prompt and return an identifier | BR-07 | `POST /api/v1/images` |
| FR-07 | Download a generated image by identifier | BR-07 | `GET /api/v1/images/{id}` |
| FR-08 | Describe an uploaded image | BR-07 | `POST /api/v1/images/descriptions` |
| FR-09 | Transcribe uploaded audio and translate it to a target language | BR-07 | `POST /api/v1/audio/transcriptions` |
| FR-10 | Synthesize speech from text | BR-07 | `POST /api/v1/audio/speech` |
| FR-11 | Expose liveness and readiness without credentials | BR-11 | `GET /actuator/health` |

---

## 9. Non-functional requirements

| ID | Category | Requirement | Status |
|---|---|---|---|
| NFR-01 | Security | Deny by default; secrets only from environment; keys never in URLs or logs | Done |
| NFR-02 | Security | Constant-time key comparison; startup fails if no key is configured | Done |
| NFR-03 | Privacy | Conversation scoped to the authenticated caller; prompts not sent in URLs | Done |
| NFR-04 | Reliability | Outbound tool calls time out (5 s default) and degrade gracefully | Done |
| NFR-05 | Reliability | Graceful shutdown with a 30 s drain | Done |
| NFR-06 | Performance | Blocking I/O on virtual threads | Done |
| NFR-07 | Maintainability | Layered packages, typed validated configuration, no business logic in controllers | Done |
| NFR-08 | Testability | Automated tests run offline; fake model for AI behaviour | Done |
| NFR-09 | Portability | Runs on Java 21, any MySQL 8, in a container | Done / partial (container untested) |
| NFR-10 | Observability | Metrics, traces, structured logs, token and cost per caller | Planned |
| NFR-11 | Availability | Stateless service, shared database memory, horizontally scalable | Done (by design); not load-tested |
| NFR-12 | Compliance readiness | Audit trail of AI interactions | Planned |

**Observed latency** (single local runs against real OpenAI, indicative only, not a benchmark): chat 3.5 s, tool-assisted answer 9.3 s, text to speech 1.9 s, image generation 32 s, audio transcription plus translation 22 s, image download 0.2 s.

---

## 10. Assumptions, constraints, dependencies

**Assumptions**
- Callers are services or trusted clients that can keep an API key secret.
- A shared MySQL instance is available to hold conversation memory.
- The model provider (OpenAI) is reachable and billing is enabled.

**Constraints**
- Java 21, Spring Boot 4.0.x, Spring AI 2.0.x (Spring AI 2.x requires Boot 4).
- `conversation_id` in the memory table is 36 characters, so caller ids are capped at 36.
- Uploads are limited to 20 MB.

**External dependencies**
| Dependency | Use | Failure behaviour |
|---|---|---|
| OpenAI APIs | Chat, image, audio | 429 passed on; other failures return 502; timeout returns 504 |
| MySQL | Chat memory | Application fails fast at startup if unreachable |
| wttr.in | Weather tool | Tool reports "unavailable"; request still succeeds |
| NewsAPI | News tool | "Not configured" or "unavailable"; request still succeeds |

---

## 11. Risks and mitigations

| ID | Risk | Likelihood | Impact | Mitigation | Residual |
|---|---|---|---|---|---|
| R-1 | Prompt injection through user text or tool output | Medium | High | Tool output declared as data; user text kept out of instructions; free-text parameters pattern-restricted | Medium until guardrail advisors (R3) |
| R-2 | Provider outage or rate limit | Medium | Medium | Clean 502/429/504 mapping now; retry and circuit breaker planned | Medium |
| R-3 | Runaway cost | Medium | High | Bounded memory window, input size limits; per-caller quotas and token metrics planned | Medium |
| R-4 | Single static API key per caller | Medium | Medium | Hashed storage, constant-time compare, fail-fast; rotation and JWT planned | Low to medium |
| R-5 | Declared upload content type can be spoofed | Low | Low | Validated before model call; content sniffing planned | Low |
| R-6 | Framework version drift | Medium | Medium | BOM-managed versions, documented migration, CI | Low |
| R-7 | Sensitive data sent to a third-party model | Medium | High | Out of scope for the reference; PII masking advisor planned; provider data-processing terms apply | Medium |

---

## 12. Acceptance criteria

| # | Scenario | Expected |
|---|---|---|
| AC-1 | Call any `/api/**` endpoint without a key | 401 `application/problem+json` |
| AC-2 | Call with a valid key and a blank prompt | 400 problem response; no model call made |
| AC-3 | Tell the assistant a fact, then ask for it again as the same caller | Correct recall; survives an application restart |
| AC-4 | Ask the same question as a different caller | The fact is not known |
| AC-5 | Provider returns an error | Client sees a generic 502; the cause appears only in server logs |
| AC-6 | Ask for time in one city and weather in another | One answer using both tools |
| AC-7 | Upload a text file to an audio or image endpoint | 415, no paid call |
| AC-8 | Request a non-UUID image id | 400 |
| AC-9 | Run the build with no OpenAI key | All tests pass |

Status: AC-1, 2, 4 to 9 are covered by automated tests. AC-3 recall was confirmed in a live run (owner-reported); restart persistence is covered by the MySQL integration test and awaits a manual restart demonstration.

---

## 13. Traceability

| Business requirement | Functional requirements | Primary code | Automated tests |
|---|---|---|---|
| BR-01 | all `/api` FRs | `security/ApiKeyAuthenticationFilter`, `SecurityConfig` | `ApiKeyAuthenticationFilterTests` (6), `ApiWebTests` |
| BR-02, 03 | FR-01, 02 | `config/AiConfig`, `service/ChatService` | `ChatServiceMemoryTests` (3), `MysqlChatMemoryIntegrationTests` |
| BR-04 | FR-03, 04 | `tools/*` | `DateTimeToolTests`, `WeatherToolTests`, `NewsToolTests` (10) |
| BR-05 | all | `web/error/GlobalExceptionHandler` | `ApiWebTests` |
| BR-06 | FR-05 to 10 | DTOs, `UploadValidator` | `ApiWebTests` |
| BR-07 | FR-06 to 10 | `service/ImageService`, `AudioService` | Live run; web tests for validation |
| BR-08 | all | `ChatClient` abstraction | By design |
| BR-09 | n/a | `.github/workflows/ci.yml` | CI run |

---

## 14. Roadmap

| Phase | Theme | Items | Requirements closed |
|---|---|---|---|
| **R1** | Identity | JWT/OAuth2 resource server, tenant claims, per-key rate limits, key rotation | R-4 |
| **R2** | Resilience | Resilience4j retry, circuit breaker and fallback around model calls | BR-13, R-2 |
| **R3** | Guardrails | PII-masking and prompt-injection advisors, audit ledger | BR-12, R-1, R-7, NFR-12 |
| **R4** | Cost and observability | Micrometer and OpenTelemetry, token and cost per caller, semantic cache, SSE streaming | BR-11, NFR-10, R-3 |
| **R5** | Data and scale | Flyway migrations, object storage for images, load test with published results | NFR-11 |

---

## 15. Glossary

| Term | Meaning |
|---|---|
| LLM | Large language model |
| Advisor | Spring AI interceptor that modifies a model call (memory, policy, guardrails) |
| Tool calling | The model asks the application to run a function and uses the result |
| Sliding window | Only the most recent N messages are replayed |
| Problem details (RFC 9457) | Standard JSON error format, `application/problem+json` |
| RAG | Retrieval-augmented generation |
| MoSCoW | Must, Should, Could, Won't prioritisation |
