# Traclux · self-hosted GEO workbench

**Generate probes with CSSM-HI, run them against a model, and inspect the results in the Traclux workbench.**

[**Explore the demo — no API key**](https://zanelau.github.io/traclux-probe/demo/) · [中文说明](README.zh-CN.md) · [Setup and feature map](docs/community/README.md) · [Source provenance](docs/community/source-manifest.json) · [Security](SECURITY.md)

**Community preview · v0.2.0.** The Spring Boot backend and Nuxt workbench are available under Apache-2.0. Deploy locally and connect your model provider.

[![Explore a captured five-probe run](docs/demo/preview.png)](https://zanelau.github.io/traclux-probe/demo/)

**Explore before installing:** open the [read-only real-model case](https://zanelau.github.io/traclux-probe/demo/), select a generated question and inspect its saved answer excerpt. No signup or API key is needed. To generate new probes, run your own instance.

## Why CSSM-HI? One brand, different questions

Someone ready to order and someone still exploring a category need different answers. Traclux starts with **six user-state dimensions**, selects combinations, then asks a model to turn them into natural questions. You can inspect the saved states alongside the resulting wording.

```text
Anker · USB-C chargers
  → verifying a choice + trying to buy + protecting devices
  → clear attention + urgent need + comparing specifications
  → “下单前想确认下，Anker的USB-C充电器和UGREEN比，直接拿哪个更稳不容易踩坑？”
  → execute against your model → save the response → inspect in the workbench
```

The example comes from a saved Anker run. In English: “Before ordering, should I choose Anker or UGREEN for a USB-C charger?” The tags describe the intended situation; compare them with the question to assess how well the wording matches.

| Dimension | Plain-language question | Saved state for this probe |
| --- | --- | --- |
| D1 · Decision stage | How far along is the buyer? | Verifying a choice |
| D2 · Goal | What do they want to do? | Buy / acquire a resource |
| D3 · Motivation | Why do they care? | Protect devices / avoid harm |
| D4 · Attention | How much can they process? | Clear and able to compare |
| D5 · Urgency | How soon do they need it? | Immediately |
| D6 · Interaction | How are they asking? | Specify requirements / compare details |

**Explore the difference:** select probes **#1, #2 and #5** in the [demo](https://zanelau.github.io/traclux-probe/demo/#explore). Compare a purchase check, a comparison method and early exploration. Each includes the saved state tags, question and response excerpt. Expand the codes or [download the public JSON](docs/demo/case.json) to inspect the trace.

Read the [generation walkthrough and source map](docs/community/generation-walkthrough.md) for the fixed states, category translation, coverage planning, rendering and checks. The five-probe example illustrates the workflow. Comparative evaluation remains open.

The demo displays saved results and makes no model calls. New runs use your configured provider account and its usage rates.

## What you get

| User task | Included implementation |
| --- | --- |
| Set up a brand | Brand recognition, brand spaces, topics, fixed questions and configuration |
| Generate a probe plan | CSSM-HI state modeling, coverage-oriented blueprints, natural-language generation and quality checks |
| Execute probes | Plan confirmation, manual execution, configured provider adapters, response storage and failure records |
| Understand results | Visibility overview, brand performance, citation sources, question analysis, generation details and response provenance |
| Follow a run | Progress, events, run history and run details |
| Run your own instance | Authentication, user access isolation, MySQL, Redis, knowledge-asset PostgreSQL and Docker Compose |

Self-hosting requires no Traclux subscription. Model usage is billed separately by your provider. See the [setup guide](docs/community/README.md) for local usage limits.

## Start locally

Requires Docker Compose and Python 3. Builds use Java 21 and the locked Nuxt dependencies.

```bash
git clone https://github.com/ZaneLau/traclux-probe.git
cd traclux-probe
python3 scripts/community_init.py
# Set MODEL_BASE_URL, MODEL_API_KEY and MODEL_NAME in .env.
docker compose up --build -d
```

Open [http://localhost:3100/login](http://localhost:3100/login). Read your generated `COMMUNITY_USERNAME` and `COMMUNITY_PASSWORD` in the local `.env`. Each instance gets fresh credentials. Configure a model endpoint before starting a run. Optional `SAMPLING_*` settings separate the model being measured from the generation/analysis model. See the [setup guide](docs/community/README.md) for adapter limitations and costs.

```bash
python3 scripts/community_smoke.py
# Stops the instance while preserving its database volumes:
docker compose stop
```

The smoke check verifies login, user APIs and rejection of management APIs. It makes no model calls. Scheduled model execution and provider web search are disabled in the community profile; manual model requests consume your own provider quota.

## Validation and limits

One actual Anker / USB-C charger run completed **5/5 probes in 173 seconds** and persisted five raw answers. The local check also covered login, overview, brand performance and run history. See the [validation record](docs/community/validation.md).

This is an early release with [known limitations](docs/community/known-limitations.md), including grouped answer presentation, topic mapping, summary wording, manual re-execution and call accounting. Default Compose is local-only. Read the [dated security review](docs/community/security-review.md) before changing that boundary.

Probes are synthetic questions. API responses can differ from consumer-app answers, and this small sample is not a brand-ranking benchmark.

![Short walkthrough: choose a probe and inspect its saved excerpt](docs/demo/walkthrough.gif)

## Earlier utility

The Python selector, invented-brand examples and [v0.1.0 selection demo](https://github.com/ZaneLau/traclux-probe/tree/v0.1.0/docs/demo) are available for studying the earlier selection workflow. See the [selector methodology](docs/methodology.md).

## License and contribution

Project code is under [Apache-2.0](LICENSE); retained third-party files carry their own notices, including [frontend template licensing](frontend/LICENSE) and bundled font licenses. Traclux names and marks follow the [trademark policy](TRADEMARK.md).

Please include the affected workbench route, expected behavior and a sanitized reproduction when [reporting a bug](https://github.com/ZaneLau/traclux-probe/issues). Never attach `.env`, cookies, keys, private brand answers or database exports. Useful first contributions include provider compatibility reports, deployment feedback and clearer question-to-answer navigation. If you find Traclux useful, consider starring the repository.
