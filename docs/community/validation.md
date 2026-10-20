# v0.2.0 validation record

Checks performed on 2026-10-19–20 in a local Compose instance. This record covers builds, login, one model run and observed workflow issues.

## Scope and provenance

Source revisions and file hashes are recorded in [source-manifest.json](source-manifest.json). Tests used fresh local databases and separate instance credentials.

## Reproduced path

| Check | Result | Limit |
| --- | --- | --- |
| Backend `./mvnw -q clean test package`, Java 21 | 620 tests: 618 executed, 2 skipped; no failures/errors | The two existing environment-dependent reward-budget snapshot cases require private quota input and did not execute |
| Frontend typecheck, tests, production build | Passed; 13 tests | Existing Nuxt application |
| Python tests | 10 passed | Includes initializer credential isolation/preservation and export-boundary check |
| Docker Compose images and startup | Backend and frontend images built; all five services healthy | Isolated local validation project, not the hosted production site |
| Empty databases | MySQL V1–V20 and knowledge PostgreSQL V1–V5 applied | New instance data, no production database copied |
| Runtime prompts | All four packaged documents loaded | Local prompt availability does not establish model output quality |
| Ordinary-user API through frontend origin | Health, login, session, brand list, service access, balance, daily usage, logout passed | Brand creation and initial real-model run completed; 5/5 answers persisted |
| Internal admin API | `/api/v1/admin/overview` rejected with HTTP 403 | Admin Controller, frontend pages and exclusive services excluded; management routes denied |
| User browser | Password-form login, completed run detail, populated overview and brand-performance pages verified | Question-analysis API has 5 answers, but its UI cannot yet organize them into question groups |
| Source boundary / sensitive values | Automated boundary check passed; source scan found no configured private env values, key signatures, local filesystem paths or provider endpoint identifiers | A scan is evidence, not a guarantee against every possible secret |

The community profile disables scheduled execution and provider web search. Model requests use the configured generation and sampling endpoints.

## Real-provider case: Anker / USB-C chargers (2026-10-20)

The test covered brand setup → CSSM-HI generation → model execution → saved answers → workbench results. Scheduled execution remained disabled. Provider billing was not measured.

| Stage | Observed result |
| --- | --- |
| Provider | Doubao was used for generation and sampling; DeepSeek was not validated |
| Generation and sampling model | The API response identified `doubao-seed-2-0-lite-260428` |
| Brand creation | Anker, USB-C chargers, Chinese/CN; real model recognition/context and initial one-time plan |
| CSSM-HI and rendering | State modeling completed, 5 generated probes entered actual sampling; 5 of 10 topics selected for cold start |
| Execution | Completed, 5 planned / 5 completed / 0 failed, 173 seconds |
| Persistence | PostgreSQL knowledge store contains 5 nonempty raw answers, 1,000–1,813 characters each; ordinary-user analysis API also returns them |
| Workbench results | Password login, run history/detail, populated overview and brand performance checked with the saved run |
| Citations | 0; sampling web search was disabled. This does not establish live-web or consumer-client measurement capability |

The [public demo](https://zanelau.github.io/traclux-probe/demo/) contains five saved questions, their generation states and reviewed answer excerpts. This sample is too small for a brand-performance study.

### Database routing fix

The initial package omitted `backend/lombok.config`; the container build also did not copy it. Lombok therefore omitted constructor parameter qualifiers, and knowledge repositories received the primary MySQL JDBC template instead of the PostgreSQL template. Brand creation failed before execution. Restored the original configuration, copied it in the Docker build and added the file to the export-boundary requirements. A repository-level two-data-source regression failed before the correction and passed after it. Clean Java build and the real populated case then passed.

### Known workflow issues

1. **Question-to-answer presentation:** the question-analysis API contains 5 raw answers, but their topic/prompt associations are null in its sample-answer projection; the browser says grouping is incomplete and does not expose the promised grouped results. Run detail lists the actual questions; grouped answer display remains incomplete.
2. **Probe/topic and summary meaning:** actual questions do not consistently match assigned topic labels. For example, the “official website/trust” topic received a broad competitor/value question. The overview reports tied #1 rankings with all recommendation rates zero and a positive headline despite only 20% target mention. These summaries should not be used to compare brand performance until topic associations and summary calculations have been checked.
3. **Manual execution:** confirming a one-time plan on a paused brand leaves it unschedulable for execute-once. A separate attempted manual task also reached execution before its transactional task record was visible (`diagnosis does not exist`). The accepted run used brand creation's initial-run path; manual re-execution remains affected.
4. **Call accounting:** `llm_call_logs` writes hit a legacy-diagnosis foreign key while task-native execution proceeds. Five raw answers are durable in the knowledge database, but accurate token/cost audit persistence was not established.
5. **Provider portability:** brand recognition first attempted a closed provider-specific realtime endpoint before the working generation fallback. Current generic configuration does not prove universal model or web-search support.

## Published assets

The v0.2.0 preview, workbench source and saved-case demo are published. The demo includes all five questions and generation tags; four answers have excerpts and one is omitted because of unsupported brand and safety claims.

The documentation/demo update passed 14 Python tests and the source publication checks. Pull-request and main-branch backend/frontend checks passed, and Pages was verified from the public URL. The downloaded case JSON matched the reviewed source file.

See [known limitations](known-limitations.md), [security review](security-review.md) and [release notes](release-v0.2.0.md) before deploying.

## Static workbench demo

The public demo reuses the Nuxt workbench pages. Its snapshot contains one captured brand and completed run, remapped identifiers and reviewed answer excerpts. No account credentials, provider configuration, billing snapshots or raw task payloads are included. Provider tests reject all mutation verbs and unknown reads, enforce the captured brand/window and verify snapshot isolation. The normal application build retains live authentication.
