# Full-system candidate validation

Checked locally on 2026-10-19–20. This records a real-provider smoke test, not a release approval or evidence of algorithm superiority.

## Scope and provenance

The owner confirmed that the user workbench and supporting backend are the open-source product. The internal operations console remains private. Source commits and per-file hashes are recorded in [source-manifest.json](source-manifest.json); the private Git history, dirty changes, environment files, production data and account sessions were not exported.

## Reproduced path

| Check | Result | Limit |
| --- | --- | --- |
| Backend `./mvnw -q clean test package`, Java 21 | 620 tests: 618 executed, 2 skipped; no failures/errors | The two existing environment-dependent reward-budget snapshot cases require private quota input and did not execute |
| Frontend typecheck, tests, production build | Passed; 13 tests | Original interface reused; no new visual redesign |
| Python tests | 10 passed | Includes initializer credential isolation/preservation and export-boundary check |
| Docker Compose images and startup | Backend and frontend images built; all five services healthy | Isolated local validation project, not the hosted production site |
| Empty databases | MySQL V1–V20 and knowledge PostgreSQL V1–V5 applied | New instance data, no production database copied |
| Runtime prompts | All four packaged documents loaded | Local prompt availability does not establish model output quality |
| Ordinary-user API through frontend origin | Health, login, session, brand list, service access, balance, daily usage, logout passed | Brand creation and initial real-model run completed; 5/5 answers persisted |
| Internal admin API | `/api/v1/admin/overview` rejected with HTTP 403 | Admin Controller, frontend pages and exclusive services excluded; management routes denied |
| User browser | Password-form login, completed run detail, populated overview and brand-performance pages verified | Question-analysis API has 5 answers, but its UI cannot yet organize them into question groups |
| Source boundary / sensitive values | Automated boundary check passed; source scan found no configured private env values, key signatures, author home paths or author endpoint identifiers | A scan is evidence, not a guarantee against every possible secret |

The browser uses the existing Nuxt user app. No sample answers were inserted into the instance. The community profile blocks inherited task requests for provider-specific web search, uses the instance sampling model identifier and disables author quota collection/enforcement. The private quota collection/refresh services and their sync-record code were excluded; shared execution admission can still read an explicitly configured snapshot if an operator separately enables that gate.

The legacy scheduler tests used “yesterday at the current time + 2 minutes”, which becomes today near midnight. The candidate uses yesterday noon for those fixtures; scheduling behavior and assertions are unchanged.

## Real-provider case: Anker / USB-C chargers (2026-10-20)

**Owner outcome:** demonstrate the existing CSSM-HI generation → model execution → saved answers → user workbench path in an isolated self-hosted instance. The owner explicitly authorized skipping free-quota verification for this bounded test; quota and actual provider billing remain unknown. Scheduling stayed disabled. Private provider configuration is outside Git and outside this repository.

| Stage | Observed result |
| --- | --- |
| Existing credentials | Historical Doubao sampling endpoint worked; historical DeepSeek endpoint was closed and a catalog-listed alternative was not available to this account |
| Generation and sampling model | Working Doubao was used for both roles; direct response identified `doubao-seed-2-0-lite-260428`. DeepSeek execution was not validated |
| Brand creation | Anker, USB-C chargers, Chinese/CN; real model recognition/context and initial one-time plan |
| CSSM-HI and rendering | State modeling completed, 5 generated probes entered actual sampling; 5 of 10 topics selected for cold start |
| Execution | Run `034f6fa4-f321-4db0-a479-87845210b748`: completed, 5 planned / 5 completed / 0 failed, 173 seconds |
| Persistence | PostgreSQL knowledge store contains 5 nonempty raw answers, 1,000–1,813 characters each; ordinary-user analysis API also returns them |
| Visible consumption | Password login, run history/detail, populated overview and brand performance exercised in the existing browser; no mock answers inserted |
| Citations | 0; sampling web search was disabled. This does not establish live-web or consumer-client measurement capability |

Local entry: `http://127.0.0.1:3100/dashboard?brandSpaceId=9f77bae9-91f7-4d8d-8f7a-7c65e5116f6a`. The ordinary-user session remains available in the browser. Instance credentials are local; never put them in a report or issue. This case is not a statistically meaningful brand-performance study.

### Blocking export defect fixed

The source export had omitted `backend/lombok.config`; the container build also did not copy it. Lombok therefore omitted constructor parameter qualifiers, and knowledge repositories received the primary MySQL JDBC template instead of the PostgreSQL template. Brand creation failed before execution. Restored the original configuration, copied it in the Docker build and added the file to the export-boundary requirements. A repository-level two-data-source regression failed before the correction and passed after it. Clean Java build and the real populated case then passed.

### Remaining release gaps (do not claim full acceptance)

1. **Question-to-answer presentation:** the question-analysis API contains 5 raw answers, but their topic/prompt associations are null in its sample-answer projection; the browser says grouping is incomplete and does not expose the promised grouped results. Run detail lists the actual questions; the complete answer-consumption UI is not accepted.
2. **Probe/topic and summary meaning:** actual questions do not consistently match assigned topic labels. For example, the “official website/trust” topic received a broad competitor/value question. The overview reports tied #1 rankings with all recommendation rates zero and a positive headline despite only 20% target mention. Trace generation, association and summary rules before using these metrics or screenshots as a quality claim. These observations do not by themselves identify the underlying algorithm as the cause.
3. **Manual execution:** confirming a one-time plan on a paused brand leaves it unschedulable for execute-once. A separate attempted manual task also reached execution before its transactional task record was visible (`diagnosis does not exist`). The accepted run used brand creation's initial-run path; manual re-execution is not accepted.
4. **Call accounting:** `llm_call_logs` writes hit a legacy-diagnosis foreign key while task-native execution proceeds. Five raw answers are durable in the knowledge database, but accurate token/cost audit persistence was not established.
5. **Provider portability:** brand recognition first attempted a closed provider-specific realtime endpoint before the working generation fallback. Current generic configuration does not prove universal model or web-search support.

The first interrupted creation and a later failed manual task remain isolated validation artifacts. No production records were copied or removed. No additional model batches are needed to establish these gaps; address the consumption and mapping defects before expanding volume.

## Delivery state and stop rule

The small real-provider test is complete. The full product remains a **local candidate with known acceptance gaps**. Stop at this evidence-backed result; do not restart research, expand features or run more paid batches in this package. Next, repair the smallest set of question/answer consumption and manual execution defects, then replay those exact paths.

Public `main`, the v0.1.0 release and GitHub Pages still show the earlier utility. The full application and this test result have not been pushed or deployed to the public site. Public promotion should resume after acceptance and replacement of that stale presentation.
