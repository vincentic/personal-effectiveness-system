# PMS FastAPI + React

FastAPI provides the REST API and serves the React single-page application from the same origin. The React project is in `versions/react/pms-react/`; its Vite production output is written to this project's `app/web/`.

## Local setup

1. Install the Python requirements and configure `.env` using `.env.example`.
2. From the repository root, run `pnpm --dir versions/react/pms-react install --frozen-lockfile` and `pnpm --dir versions/react/pms-react run build`.
3. Start the API with `start.sh`; open `http://127.0.0.1:8000/`. Interactive API documentation is at `/docs`.

See the focused project docs for setup, build, and deployment details.
