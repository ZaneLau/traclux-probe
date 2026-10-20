# Traclux project

- Purpose: self-host the existing CSSM-HI probe generation → model execution → user analysis workbench.
- Outcome anchor: the owner clarified on 2026-10-19 that the user workbench is the open-source product; their internal operations console stays private.
- Current: v0.2.0 community preview is published on public main, with the full user system, saved-case demo and passing release CI. This remains a preview with documented functional/security limitations.
- Verified: real initial generation/execution, five persisted answers, populated overview/brand performance, password login. Scoped frontend security upgrades built and passed tests; known dependency and correctness warnings are documented.
- Current outcome anchor (owner, 2026-10-20): strengthen README and the no-login demo so visitors understand CSSM-HI from an actual generation example, see saved answers and can find the open implementation. No author-funded visitor model calls.
- Next: publish source-linked generation explanations and allowlisted saved state tags, then verify the anonymous Pages path and static-only cost boundary. Do not reopen question/answer mapping, research or paper work in this package.
- Scope: exclude internal admin pages/APIs/services, production secrets/data and author provider accounts. Retain user features and their backend dependencies. Research and paper experiments remain paused; personal social-media accounts are excluded.
- Stop rule: a visitor can follow brand → six saved states → actual question → saved answer, access source/setup without logging in, and make no live model request. README, demo and public JSON agree, remote CI/Pages pass, private credentials remain excluded. No further polish without a new outcome.
