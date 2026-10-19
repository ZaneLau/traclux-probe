# Full-system candidate validation

Checked locally on 2026-10-19–20. This is not a release approval or a real-provider effectiveness result.

## Scope and provenance

The owner confirmed that the user workbench and supporting backend are the open-source product. The internal operations console remains private. Source commits and per-file hashes are recorded in [source-manifest.json](source-manifest.json); the private Git history, dirty changes, environment files, production data and account sessions were not exported.

## Reproduced path

| Check | Result | Limit |
| --- | --- | --- |
| Backend `./mvnw -q clean test package`, Java 21 | 619 tests: 617 executed, 2 skipped; no failures/errors | The two existing environment-dependent reward-budget snapshot cases require private quota input and did not execute |
| Frontend typecheck, tests, production build | Passed; 13 tests | Original interface reused; no new visual redesign |
| Python tests | 10 passed | Includes initializer credential isolation/preservation and export-boundary check |
| Docker Compose images and startup | Backend and frontend images built; all five services healthy | Isolated local validation project, not the hosted production site |
| Empty databases | MySQL V1–V20 and knowledge PostgreSQL V1–V5 applied | New instance data, no production database copied |
| Runtime prompts | All four packaged documents loaded | Local prompt availability does not establish model output quality |
| Ordinary-user API through frontend origin | Health, login, session, brand list, service access, balance, daily usage, logout passed | No provider call or populated run |
| Internal admin API | `/api/v1/admin/overview` rejected with HTTP 403 | Admin Controller, frontend pages and exclusive services excluded; management routes denied |
| User browser | Authenticated overview, brand performance, citations, question analysis and brand management rendered empty states; add-brand wizard reached step 1 | Browser session came from ordinary-user API login; password form submission was not exercised |
| Source boundary / sensitive values | Automated boundary check passed; source scan found no configured private env values, key signatures, author home paths or author endpoint identifiers | A scan is evidence, not a guarantee against every possible secret |

The browser uses the existing Nuxt user app. No sample answers were inserted into the instance. The community profile blocks inherited task requests for provider-specific web search, uses the instance sampling model identifier and disables author quota collection/enforcement. The private quota collection/refresh services and their sync-record code were excluded; shared execution admission can still read an explicitly configured snapshot if an operator separately enables that gate.

The legacy scheduler tests used “yesterday at the current time + 2 minutes”, which becomes today near midnight. The candidate uses yesterday noon for those fixtures; scheduling behavior and assertions are unchanged.

## Remaining acceptance

The complete real-provider path remains **unverified**: brand creation/recognition → generated probe plan → confirmation → model execution → persisted answers → populated analysis and run history. No provider call was made because sufficient free quota was not verified in this development session. A local mock or a successful build would not close this gap.

Before advertising the full system, run that path using a verified provider account, confirm actual requested model IDs and inspect the resulting saved answers and analyses. Existing provider labels and adapter restrictions come from the original system; configuring an arbitrary compatible endpoint has not been accepted as universal provider support.

The candidate is local. Public `main`, the v0.1.0 release and GitHub Pages still show the earlier utility. The full application has not been pushed or deployed to the public site. Promotion should resume after acceptance and replacement of the stale public presentation.
