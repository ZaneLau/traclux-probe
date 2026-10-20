# Traclux · self-hosted GEO workbench

**Generate probes with CSSM-HI, run them against a model, and inspect the results in the existing Traclux user workbench.**

[**Explore the demo — no API key**](https://zanelau.github.io/traclux-probe/demo/) · [中文说明](README.zh-CN.md) · [Setup and feature map](docs/community/README.md) · [Source provenance](docs/community/source-manifest.json) · [Security](SECURITY.md)

**Community preview · v0.2.0.** The complete user workbench and its supporting backend are open source under Apache-2.0. Connect your own model account; the author's operations console stays private.

[![Explore a captured five-probe run](docs/demo/preview.png)](https://zanelau.github.io/traclux-probe/demo/)

**Try before installing:** open the [read-only real-model case](https://zanelau.github.io/traclux-probe/demo/), select a generated question and inspect its saved answer excerpt. No signup, model key or live request is needed. This public case viewer is separate from the authenticated application.

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
git clone https://github.com/ZaneLau/traclux-probe.git
cd traclux-probe
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

One actual Anker / USB-C charger run completed **5/5 probes in 173 seconds** and persisted five raw answers. Password login, populated overview, brand performance and run history were exercised locally. See the [validation record](docs/community/validation.md).

This is an early release with [known limitations](docs/community/known-limitations.md), including grouped answer presentation, topic mapping, summary wording, manual re-execution and call accounting. Default Compose is local-only. Read the [dated security review](docs/community/security-review.md) before changing that boundary.

Generated probes are not purchased search logs; API responses do not establish consumer-client visibility. Five responses do not establish algorithm superiority or meaningful brand ranking. Your provider account supplies model quota and billing.

![Short walkthrough: choose a probe and inspect its saved excerpt](docs/demo/walkthrough.gif)

## Earlier utility

The Python selector, invented-brand examples and [v0.1.0 selection demo](https://github.com/ZaneLau/traclux-probe/tree/v0.1.0/docs/demo) remain historical auxiliary artifacts. They illustrate question selection and imported evidence, not the full generation/execution product. See [legacy usage](docs/demo/README.md).

## License and contribution

Project code is under [Apache-2.0](LICENSE); retained third-party files carry their own notices, including [frontend template licensing](frontend/LICENSE) and bundled font licenses. Traclux names and marks follow the [trademark policy](TRADEMARK.md).

Please include the affected workbench route, expected behavior and a sanitized reproduction when [reporting a bug](https://github.com/ZaneLau/traclux-probe/issues). Never attach `.env`, cookies, keys, private brand answers or database exports. Useful first contributions include provider compatibility reports, deployment feedback and clearer question-to-answer navigation. If this helps your work, a GitHub Star makes the project easier to discover.
