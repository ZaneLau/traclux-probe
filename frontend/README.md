# Traclux frontend

Nuxt application for the Traclux workbench. For a complete local instance with databases and backend, follow the [Compose setup guide](../docs/community/README.md).

## Frontend development

Requires Node.js 22 and pnpm 10.30.3. Run from this directory:

```bash
pnpm install --frozen-lockfile
pnpm dev
```

The default development URL is `http://localhost:3000`. API requests to `/api/v1` are proxied to `http://127.0.0.1:8080/api/v1`; a running backend is required for login and workbench data.

| Variable | Purpose | Default |
| --- | --- | --- |
| `NUXT_PUBLIC_API_BASE` | Browser API base | `/api/v1` |
| `NUXT_DEV_PROXY_API_TARGET` | Development API proxy | `http://127.0.0.1:8080/api/v1` |
| `NUXT_PUBLIC_ENABLE_ENGINE_THEATER` | Experimental engine view | Enabled in development, disabled in production |

## Checks

```bash
pnpm -s typecheck
pnpm test
pnpm build
```

## Docker

Run `docker compose up --build -d` from the repository root to start the full stack. The workbench is served at `http://localhost:3100/login`.

To build only the frontend image:

```bash
docker build -t traclux-frontend .
```
