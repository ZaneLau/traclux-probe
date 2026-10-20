# Self-hosted Traclux user workbench

Traclux generates probes with CSSM-HI, runs them against a configured model, and stores answers for analysis in the brand workbench.

The Compose stack includes the Nuxt frontend, Spring Boot backend, MySQL, PostgreSQL and Redis. Data is stored in local Docker volumes. Self-hosting requires no Traclux subscription; model calls use your configured provider account.

Start with the [actual generation walkthrough](generation-walkthrough.md): brand → six saved user states → generated question → saved answer, with links to each implementation stage. The [public saved-case viewer](https://zanelau.github.io/traclux-probe/demo/) needs no login or key and makes no model calls. Generating new probes uses the deploying user's own provider quota.

## User features

| Area | Included capability | Application route |
| --- | --- | --- |
| Account | Login, session, logout, access isolation | `/login` |
| Brand spaces | Create, configure, archive/restore, manage topics and fixed questions | `/brands`, `/brands/new`, `/brands/:id/settings` |
| Probe plans | Review the plan, confirm, execute once or configure continued tracking | Brand setup/settings |
| CSSM-HI engine | Intent-state modeling, coverage-oriented blueprints, natural-language probe generation and quality control | Backend execution chain |
| Model execution | Configured model requests, concurrent sampling, retries, stored answers and failures | Run execution |
| Visibility | Visibility summary and brand-performance analysis | `/dashboard`, `/engine` |
| Evidence | Citation sources, question analysis, generation details and response provenance | `/sources`, `/probe-analysis` |
| History | Run batches, per-run details and events | `/brands/:id/runs`, `/brands/:id/runs/:runId` |

Some result drill-downs are incomplete; see [known limitations](known-limitations.md). Probes are synthetic questions, and API results can differ from consumer-app answers.

## Configure and start

Requires Docker Compose and Python 3 for local credential initialization. Builds use the existing locked Nuxt dependencies and Java 21 backend.

```bash
python3 scripts/community_init.py
# Set MODEL_BASE_URL, MODEL_API_KEY and MODEL_NAME in .env.
docker compose up --build -d
```

Open `http://localhost:3100/login`. The username and randomly generated password are in your local `.env` (`COMMUNITY_USERNAME` / `COMMUNITY_PASSWORD`). The initializer preserves an existing file. Never commit it or send it in an issue.

The default model endpoint is a closed local port. Configure an endpoint before running probes. Set the base URL of your OpenAI-compatible endpoint, including its API prefix, and an available model identifier. If the model being measured differs from the generation/analysis model, set `SAMPLING_BASE_URL`, `SAMPLING_API_KEY`, and `SAMPLING_MODEL_NAME` separately.

Verify compatibility with your chosen provider. The community profile disables provider web search; support for an OpenAI-compatible API does not imply support for that provider's search tools.

The initializer creates a user account with a local usage allowance. Registration and scheduled execution are disabled by default. Both manual and scheduled runs consume your provider quota. Local credits track application access; consult your provider for actual billing.

```bash
docker compose logs --tail=80 backend
docker compose stop
```

MySQL and PostgreSQL retain data in instance-owned volumes. `stop` preserves them. Do not share logs before reviewing them for private brand input, answers or credentials.

## Tested workflow

Builds, fresh-database migrations, password login and one bounded real-model case have passed locally: CSSM-HI generation, 5/5 actual responses, persisted answers, populated overview and brand performance. See the [validation record](validation.md).

The preview has known issues with grouped answer display, topic matching, summary wording, manual re-execution and call accounting. The successful initial run covers one provider and one workflow; see [known limitations](known-limitations.md) for details.

See [known limitations](known-limitations.md) and [security review](security-review.md) before deployment. The public captured-case viewer is separate from this authenticated app.

The earlier Python selector is documented in [selector methodology](../methodology.md).
