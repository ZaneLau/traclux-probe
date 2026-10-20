# Self-hosted Traclux user workbench

This package reuses the Traclux application. It generates probes with CSSM-HI, sends them to a configured model, stores answers, and exposes the existing brand workbench. It includes the backend required by those user features.

The private operations console is excluded: no `/admin` pages, admin Controller, management DTOs or exclusive admin services. Provider quota collectors and refresh scheduling are excluded as well; shared runtime accounting remains where user execution depends on it. Instance operators keep their own database, account credentials and model keys. No Traclux hosted subscription is required.

Start with the [actual generation walkthrough](generation-walkthrough.md): brand → six saved user states → generated question → saved answer, with links to each implementation stage. The [public saved-case viewer](https://zanelau.github.io/traclux-probe/demo/) needs no login or key and makes no model calls. Generating new probes uses the deploying user's own provider quota.

## User features

| Area | Included capability | Application route |
| --- | --- | --- |
| Account | Login, session, logout, access isolation | `/login` |
| Brand spaces | Create, configure, archive/restore, manage topics and fixed questions | `/brands`, `/brands/new`, `/brands/:id/settings` |
| Probe plans | Review the plan, confirm, execute once or configure continued tracking | Brand setup/settings |
| CSSM-HI engine | Intent-state modeling, coverage-oriented blueprints, natural-language probe generation and quality control | Backend execution chain |
| Model execution | Configured model requests, concurrent sampling, retries, stored answers and failures | Run execution |
| Visibility | Existing summary and brand-performance analysis | `/dashboard`, `/engine` |
| Evidence | Citation sources, question analysis, existing provenance and explanation UI | `/sources`, `/probe-analysis` |
| History | Run batches, per-run details and events | `/brands/:id/runs`, `/brands/:id/runs/:runId` |

Existing provenance UI does not mean that every metric has complete drill-down coverage. Synthesized probes do not represent purchased real-user search logs. API results do not establish consumer-client visibility.

## Configure and start

Requires Docker Compose and Python 3 for local credential initialization. Builds use the existing locked Nuxt dependencies and Java 21 backend.

```bash
python3 scripts/community_init.py
# Privately edit .env: MODEL_BASE_URL, MODEL_API_KEY, MODEL_NAME.
docker compose up --build -d
```

Open `http://localhost:3100/login`. The username and randomly generated password are in your local `.env` (`COMMUNITY_USERNAME` / `COMMUNITY_PASSWORD`). The initializer preserves an existing file. Never commit it or send it in an issue.

The default model endpoint is a closed local port, so an unconfigured instance cannot silently call the author's account. Set the base URL of your OpenAI-compatible endpoint, including its API prefix, and an available model identifier. If the model being measured differs from the generation/analysis model, set `SAMPLING_BASE_URL`, `SAMPLING_API_KEY`, and `SAMPLING_MODEL_NAME` separately.

Compatibility must be verified with the chosen provider. The existing provider-specific web-search implementation is not a promise that every compatible API supports web search. Generic community sampling starts without provider web-search or author reward-quota routing.

The seeded account is a normal user. Existing internal usage accounting receives an initial local allowance, not a paid entitlement from Traclux. Registration is disabled by default. Scheduled background execution is disabled by default; enabling it can incur your provider's charges. Manual execution also uses your provider quota and billing.

```bash
docker compose logs --tail=80 backend
docker compose stop
```

MySQL and PostgreSQL retain data in instance-owned volumes. `stop` preserves them. Do not share logs before reviewing them for private brand input, answers or credentials.

## Release acceptance

Builds, fresh-database migrations, password login and one bounded real-model case have passed locally: CSSM-HI generation, 5/5 actual responses, persisted answers, populated overview and brand performance. See the [validation record](validation.md).

This is a published community preview with incomplete stable-release acceptance. Question analysis does not yet expose grouped answers correctly; generated questions and summary labels need a meaning/association check; paused-brand manual execution and call-accounting persistence have known defects. The initial-run result does not establish every execution path, provider compatibility, algorithm superiority or consumer-client visibility. Provider calls consume the instance owner's quota and may incur charges. No author keys are included.

See [known limitations](known-limitations.md) and [security review](security-review.md) before deployment. The public captured-case viewer is separate from this authenticated app.

The original selection CLI and v0.1.0 static demo belong to the earlier scope. They remain historical auxiliary artifacts and are not the full user workbench demo.
