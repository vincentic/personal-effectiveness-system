# FastAPI + React deployment

Build the React bundle before deployment; `pnpm --dir versions/react/pms-react run build` places the files under FastAPI `app/web/`. Deploy the Python project with its pinned requirements and inject database credentials, `API_SECRET`, and administrator credentials through the host's secret manager or protected environment file.

FastAPI serves the React app at `/` and the API under `/api/` on port 8000. Place Nginx or an ingress in front for TLS, forward `/` and `/api/` to the FastAPI service, and use `/health` for the liveness/readiness check. Do not expose MySQL or the application port publicly.
