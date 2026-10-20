# AeoProbe Frontend Nuxt App

This is the canonical frontend application for the standalone GeoProbe frontend repo.

## Setup

```bash
cp .env.example .env
pnpm install
```

## Local Development

```bash
pnpm dev
```

Default URL:

- `http://127.0.0.1:3000`

Recommended collaboration split:

- `3000`: Codex/frontend implementation lane
- `3001`: human acceptance / integration lane
- both lanes should keep `NUXT_PUBLIC_API_BASE=/api/v1`
- both lanes should proxy by default to `http://127.0.0.1:8080/api/v1`

If you want the full local runtime to stay up across Codex session ends, use the repo-level detached runtime supervisor:

```bash
../scripts/start-local-runtime.sh
../scripts/status-local-runtime.sh
../scripts/stop-local-runtime.sh
```

If you want the local frontend to stay up across Codex session ends, use the repo-level detached supervisor:

```bash
../scripts/start-frontend-dev-service.sh
../scripts/status-frontend-dev-service.sh
../scripts/stop-frontend-dev-service.sh
```

When running against the local backend, the expected runtime chain is:

- GeoProbe project MySQL on `127.0.0.1:3307`
- backend on `127.0.0.1:8080`
- frontend implementation lane on `127.0.0.1:3000`
- frontend acceptance lane on `127.0.0.1:3001`

## Local Collaboration Rules

- Port only separates running instances.
- Worktree separates code under modification.
- Frontend default split:
  - Codex/frontend changes on `3000`
  - human acceptance and integration checks on `3001`
- Backend default truth source:
  - same-origin `/api/v1`
  - local proxy target `http://127.0.0.1:8080/api/v1`
- Backend isolation rule:
  - do not keep a second backend port as a long-term default
  - use separate backend worktrees and branches for code isolation
  - open a second backend port only for explicit parallel backend integration, then recycle it
- Do not treat `3000/3001` alone as real code isolation.

Repo-level local runtime self-check:

```bash
bash scripts/local-runtime-check.sh
```

## Validation

```bash
pnpm -s typecheck
pnpm test
pnpm build
```

## Runtime Env

- `NUXT_PUBLIC_API_BASE`: backend API base, default `/api/v1`
- `NUXT_DEV_PROXY_API_TARGET`: dev-only same-origin proxy target, default `http://127.0.0.1:8080/api/v1`
- `NUXT_PUBLIC_ENGINE_DATA_PROVIDER`: `http` only
- `NUXT_PUBLIC_ENABLE_ENGINE_THEATER`: whether the experimental `引擎观察窗` surface is exposed; defaults to `true` in local development and `false` in production builds

Recommended deploy default:

```bash
NUXT_PUBLIC_API_BASE=/api/v1
NUXT_PUBLIC_ENGINE_DATA_PROVIDER=http
NUXT_PUBLIC_ENABLE_ENGINE_THEATER=false
```

Recommended local default:

```bash
NUXT_PUBLIC_API_BASE=/api/v1
NUXT_DEV_PROXY_API_TARGET=http://127.0.0.1:8080/api/v1
```

If you explicitly need a temporary parallel backend lane, change only `NUXT_DEV_PROXY_API_TARGET` for that temporary frontend instance, then switch back to `8080` after the task ends.

## Docker

Build the frontend image independently:

```bash
docker build -t aeoprobe-frontend .
```
