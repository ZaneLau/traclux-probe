# Read-only workbench demo

The public demo uses the same pages and components as the self-hosted app. Build with `pnpm generate:demo`; normal builds keep authentication and live APIs.

`workbench.json` contains presentation projections from the saved Anker / USB-C charger run on 2026-10-20. Only that brand and completed run are included. Metrics and questions retain their captured values. Answers use the reviewed excerpts in `docs/demo/case.json`; one answer is withheld. Model endpoints are replaced with the public model name and identifiers with `demo-*` aliases. Account, provider configuration, billing, raw task orders and raw diagnostic payloads are excluded. The snapshot is public data, not a database export.

`provider.ts` serves only the listed GET projections, returns fresh copies, rejects other reads and rejects all mutations. It has no network fallback. The demo session is a local display identity with no token. It does not authenticate to a server. Date/platform/topic controls are fixed to the captured sample. Generate, execute and save actions require a self-hosted instance.

The Pages workflow generates a static Nuxt app with hash routes and packages it at `/traclux-probe/demo/`. No Nuxt server, API proxy or model key is deployed. The HTML policy restricts scripts and connections to the same origin. `scripts/package_workbench_demo.py` refuses a normal application build and preserves the explanatory case at `/demo/case-study/`.

To preview locally:

```bash
NUXT_APP_BASE_URL=/traclux-probe/demo/ pnpm -C frontend generate:demo
python3 scripts/package_workbench_demo.py --output /tmp/traclux-demo-pages
```

Serve the output under a `/traclux-probe/` directory so the production asset paths resolve. The primary entry redirects to `#/dashboard`; route refreshes do not need server rewrites.

Changes to the snapshot require review against the captured source and the approved answer excerpts. Do not add credentials or raw responses. Existing sample and summary limitations remain documented in [known limitations](../../../docs/community/known-limitations.md).
