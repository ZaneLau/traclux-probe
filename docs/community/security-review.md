# Community preview security review — 2026-10-20

This review covers source scanning, default configuration, authentication checks and dependency findings for v0.2.0. It is not a penetration test. The default deployment is local-only; outstanding findings are listed below.

| Check | Evidence / result |
| --- | --- |
| Secrets and publication boundary | Source signature scan, exact comparison against private model/instance secret values, and reachable Git-history comparison; no matches. Environment files, runtime logs, databases and sessions excluded |
| Internal console | Admin pages/controllers/exclusive services excluded by source check; ordinary-user request to `/api/v1/admin/overview` rejected. Dev, tracking and Swagger routes default denied |
| Local default | Only frontend `127.0.0.1:3100` is published by default Compose; backend, MySQL, PostgreSQL and Redis have no host ports |
| Credentials | Initializer creates exclusive mode-0600 `.env` with random login, database passwords and JWT secret; preserves existing files. Model endpoint configuration required before use |
| Authentication | Password login, authenticated user APIs, session and logout exercised. Existing backend authentication, cookie-origin and access tests retained; frontend does not store auth tokens in localStorage |
| Public demo | Allowlisted case fields only, no fetch/provider calls, no private endpoint or account IDs. Text is rendered through `textContent`; JSON embedding escapes `<`. Static CSP disallows external scripts, connections and forms |
| Frontend dependencies | Nuxt 4.4.6 → 4.5.1, Vue 3.5.34 → 3.5.42, ECharts 6.0.0 → 6.1.0; h3 1.15.11 explicitly aligned to avoid duplicate-event-type errors. Docker build/runtime moves from Node 20 to Node 22, matching upgraded Nuxt requirements and CI. Typecheck, 13 tests and local production build passed |
| Frontend production-dependency audit | Before: 6 critical / 48 high / 23 moderate / 7 low advisory entries. After the scoped upgrade, `pnpm audit --prod`: 0 critical / 16 high / 5 moderate / 2 low. This scope excludes development-only dependencies; see the full GitHub alert scope below. These are advisory entries, not a count of confirmed exploitable application paths |
| Java SCA | Dependency-Check using the already-local NVD data cache passed CVSS >= 7 after the package-specific false-positive reviews below. Cache freshness is not established; this is not a fresh comprehensive advisory clearance |
| Java static analysis | SpotBugs high-priority check reported 13 correctness warnings: nullability, redundant checks and a useless condition. It did not pass; those findings remain open. High priority here is not a vulnerability severity rating |

## Reviewed SCA mismatches

- **CVE-2026-34070:** upstream identifies the Python pip `langchain-core` prompt loader, not the Maven `dev.langchain4j` artifacts. Exact package/version/CVE suppressions were reviewed and renewed until 2027-01-20. [Upstream advisory](https://github.com/langchain-ai/langchain/security/advisories/GHSA-qh6h-p6c9-ff54).
- **CVE-2025-7962:** upstream identifies the SMTP implementation; the matched artifact here is `angus-activation:2.0.3`, which contains no SMTP implementation. The suppression is restricted to that exact package/version/CVE, until 2027-01-20. [Advisory](https://github.com/advisories/GHSA-9342-92gg-6v29), [Dependency-Check false-positive report](https://github.com/dependency-check/DependencyCheck/issues/8144).

No general CVSS threshold was lowered. No library was globally excluded. Medium/low cached SCA findings remain in the local report and were not declared fixed.

## Remaining findings and deployment boundary

**SEC-01 — High, dependency review remains open.** The frontend audit still includes high advisories in build/development tooling and UI/editor dependencies. Paths include ESLint glob/YAML tools, PostCSS/source maps, Nuxt CLI TLS tooling, and Tiptap/ProseMirror. A framework dependency manifest can include build tools even with `audit --prod`; this review has not proved every advisory unreachable. The shipped pages do not currently use an editor/paste surface. Do not ingest untrusted projects/configurations through development tooling or expose development servers. Review these remaining dependencies before operating an internet-facing multi-user service.

Locations: `frontend/package.json`, `frontend/pnpm-lock.yaml`; audit results refer to the dependency versions checked on the date above.

**SEC-04 — Critical alerts in development/test dependencies (checked 2026-10-20).** GitHub's complete dependency graph reports **3 critical / 24 high / 14 moderate / 2 low** open alerts. The three critical entries concern development-only Vitest and its Tinypool dependency: [Vitest UI server](https://github.com/advisories/GHSA-5xrq-8626-4rwp), [Tinypool worker options](https://github.com/advisories/GHSA-5gmw-xhrv-c9v3), [Tinypool run options](https://github.com/advisories/GHSA-85c8-ppgw-ccpr). The existing test command uses `vitest run`, not a public UI server. These alerts remain unresolved; no claim of zero critical findings across the entire repository is made. They are not shipped to the static Pages demo. Review/update test tooling before exposing any test/development service or executing untrusted input through it.

**SEC-02 — Medium, cached Java dependency coverage.** The cached NVD database may miss recent advisories. A successful cached scan is bounded evidence. Obtain fresh advisory coverage for a public-hosting release; do not infer absence of vulnerabilities from this scan.

**SEC-03 — Preview deployment limit.** Authentication and tenant access remain enabled, but this review did not test all authorization, recovery, SSRF, rate-limit or public-hosting combinations. Do not change Compose to bind publicly and treat that as a supported hardened deployment. Default loopback binding is retained.

Backend identity/authorization references: `backend/src/main/java/com/geoprobe/config/SecurityConfig.java`, `filter/JwtAuthFilter.java`, `filter/AuthCookieOriginGuardFilter.java`, and existing authentication/access tests. New static-viewer input handling is in `docs/demo/case-viewer.js`.

## Deployment guidance

Keep the default loopback binding for local use. Public multi-user hosting requires a separate review of access control, rate limits, provider access and the outstanding dependencies above.

Local and CI source checks use `python3 scripts/release_check.py`; CI also builds/tests Java and Nuxt and rejects critical frontend **production-dependency** advisories. Development/test dependency alerts, other remaining advisories and SpotBugs warnings are documented, not silently counted as passed.
