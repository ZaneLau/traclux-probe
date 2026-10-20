# Saved-case demo

The [public demo](https://zanelau.github.io/traclux-probe/demo/) displays an Anker / USB-C charger run recorded on 2026-10-20. Visitors can search five questions, inspect their six CSSM-HI state tags, expand generation metadata and read response excerpts.

## Data

`case.json` contains the generated questions, saved state codes and category wording, generation flags, model name, capture time and latency. English summaries explain the Chinese questions and tags. Four responses have exact excerpts; response #4 is omitted because it contains unsupported brand and safety claims. Web search was disabled, so the case has no citations.

See the [generation walkthrough](../community/generation-walkthrough.md) for the method and source code.

## Update the demo

Edit `template.html`, `case-viewer.js` or reviewed fields in `case.json`, then rebuild:

```bash
python3 scripts/build_community_demo.py
python3 scripts/release_check.py
```

The build embeds the JSON in `index.html`, escapes script-termination characters and versions the CSS/JavaScript URLs by content hash. Use a local HTTP server to preview `docs/demo/`.

The viewer uses `textContent`, loads no external scripts or fonts, and makes no API requests. Keep `connect-src 'none'` and `form-action 'none'` when editing. New model runs belong in the self-hosted application.

The earlier selection demo is available in the [v0.1.0 tag](https://github.com/ZaneLau/traclux-probe/tree/v0.1.0/docs/demo).
