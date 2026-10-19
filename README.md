# Traclux · self-hosted GEO workbench

**Generate probes with CSSM-HI, run them against a model, and inspect the results in the existing Traclux user workbench.**

[中文说明](README.zh-CN.md) · [Setup and feature map](docs/community/README.md) · [Source provenance](docs/community/source-manifest.json) · [Security](SECURITY.md)

This branch is an **unreleased full-system candidate**. It reuses the Java/Spring Boot backend and Nuxt user application. The internal operations console is excluded. The published v0.1.0 and its static question-selection demo still describe the previous, narrower utility; they are not a demo of this workbench.

## What you get

| User task | Included implementation |
| --- | --- |
| Set up a brand | Brand recognition, brand spaces, topics, fixed questions and configuration |
| Generate a probe plan | CSSM-HI state modeling, coverage-oriented blueprints, natural-language generation and quality checks |
| Execute probes | Plan confirmation, manual execution, configured provider adapters, response storage and failure records |
| Understand results | Visibility overview, brand performance, citation sources, question analysis and existing explanation/provenance interfaces |
| Follow a run | Progress, events, run history and run details |
| Run your own instance | Authentication, user access isolation, MySQL, Redis, knowledge-asset PostgreSQL and Docker Compose |

Your internal `/admin` console, user operations, management reruns, billing configuration, provider-quota management, author credentials and production data are outside this package. Local usage accounting remains where the user execution path depends on it; the seeded local allowance does not require a Traclux subscription.

## Start locally

Requires Docker Compose and Python 3. Builds use Java 21 and the locked Nuxt dependencies.

```bash
python3 scripts/community_init.py
# Privately configure MODEL_BASE_URL, MODEL_API_KEY and MODEL_NAME in .env.
docker compose up --build -d
```

Open **http://localhost:3100/login**. Read your generated `COMMUNITY_USERNAME` and `COMMUNITY_PASSWORD` in the local `.env`. Each instance gets fresh credentials. An unconfigured model points to a closed local port; no author model account is used. Optional `SAMPLING_*` settings separate the model being measured from the generation/analysis model. See the [setup guide](docs/community/README.md) for adapter limitations and costs.

```bash
python3 scripts/community_smoke.py
# Stops the instance while preserving its database volumes:
docker compose stop
```

The smoke check verifies login, user APIs and rejection of management APIs. It makes no model calls. Scheduled model execution and provider web search are disabled in the community profile; manual model requests consume your own provider quota.

## Validation and limits

The candidate has passed backend/frontend builds and tests, fresh-database migrations, packaged-prompt loading and ordinary-user API checks. Check the [validation record](docs/community/validation.md) for the exact tested path and remaining gap.

**Real-provider generation → execution → populated analysis has not yet been accepted.** There is no verified free-quota account for that run in this development session. Provider compatibility must be checked for the selected endpoint and model. API sampling does not establish the answers shown in consumer apps. Generated probes are not purchased real-user search logs, and this release makes no measured claim of algorithm superiority.

## Earlier utility

The Python selector, invented-brand examples and [static v0.1.0 demo](https://zanelau.github.io/traclux-probe/demo/) remain historical auxiliary artifacts. They illustrate question selection and imported evidence, not the full generation/execution product. See [legacy usage](docs/demo/README.md).

## License and contribution

Project code is under [Apache-2.0](LICENSE); retained third-party files carry their own notices, including [frontend template licensing](frontend/LICENSE) and bundled font licenses. Traclux names and marks follow the [trademark policy](TRADEMARK.md).

Please include the affected workbench route, expected behavior and a sanitized reproduction when [reporting a bug](https://github.com/ZaneLau/traclux-probe/issues). Never attach `.env`, cookies, keys, private brand answers or database exports. The full-system candidate must finish provider acceptance before being presented as a completed release.
