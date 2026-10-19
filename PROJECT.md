# Traclux project

- Purpose: self-host the existing CSSM-HI probe generation → model execution → user analysis workbench.
- Outcome anchor: the owner clarified on 2026-10-19 that the user workbench is the open-source product; their internal operations console stays private.
- Current: local `codex/community-system` candidate exports the original Java backend and Nuxt user app from recorded commits. Original private repositories and dirty work are preserved. Public `main` and v0.1.0 still contain the earlier question-selection utility.
- Verified: see [validation record](docs/community/validation.md). The package can initialize isolated databases and authenticate an ordinary user. No real provider call or measured algorithm improvement has been claimed.
- Next: verify sufficient free provider quota, then accept brand creation → generated plan → execution → saved answers → populated workbench. Only then replace the public product presentation and demo.
- Scope: exclude internal admin pages/APIs/services, production secrets/data and author provider accounts. Retain user features and their backend dependencies. Research and paper experiments remain paused; personal social-media accounts are excluded.
- Stop rule: a user can reproduce that end-to-end path from the documented clean setup and cannot reach the internal console. Until then this is a candidate, not a completed full-system release.
