# Traclux project

Traclux is a self-hosted GEO workbench for CSSM-HI probe generation, model execution and response analysis.

## Current release

- v0.2.0 community preview: Spring Boot backend, Nuxt workbench and Docker Compose setup.
- [Public demo](https://zanelau.github.io/traclux-probe/demo/): five saved probes with generation states and response excerpts; no login or model calls.
- [Generation walkthrough](docs/community/generation-walkthrough.md): method, example and source map.
- [Known limitations](docs/community/known-limitations.md) and [security review](docs/community/security-review.md): issues to review before deployment.

## Next priorities

- Improve topic matching and question-to-answer navigation.
- Resolve manual re-execution and call-accounting issues.
- Expand provider compatibility checks based on reproducible reports.
- Review outstanding development-tool and application dependency alerts.

New runs use the configured provider account. The public demo remains a saved-case viewer.

The public demo uses the Nuxt workbench in read-only mode, with one captured Anker run. The explanatory CSSM-HI case is available at `/demo/case-study/`.
