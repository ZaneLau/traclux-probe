# Captured real-model case

This demo uses the existing approved native HTML list/search/detail presentation, with reviewed fields from the 2026-10-20 Anker / USB-C charger run. It is a standalone read-only viewer, not the authenticated workbench or a live model service.

- Five actual generated questions and successful execution records.
- Four exact answer excerpts; one answer body is withheld because it contains unverified brand/safety claims. No replacement answers are invented.
- Public model name and capture time; private endpoint IDs, account IDs, keys and raw task payloads excluded.
- Zero citations: web search was off. API answers are not consumer-client measurements.

Edit only reviewed `case.json` fields, then run `python3 scripts/build_community_demo.py`. The generated `index.html` embeds exactly that JSON with script-termination escaping. The viewer uses DOM textContent, has no fetch or provider calls, and ships no external scripts or fonts.

The inherited presentation was approved using Promptfoo's viewer and Langfuse's demo as references; their code and assets are not copied. Current adaptations retain the list/search/detail interaction and replace selection-comparison claims with actual execution results. The v0.1.0 demo remains available in its Git tag.
