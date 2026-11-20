# FastAPI + React setup

FastAPI runs the REST API and hosts the built React application at the same origin. Requirements: Python 3.10+, Node.js 24+, pnpm 12+, and MySQL 8+.

1. Create a Python virtual environment in `versions/python/pms-fastapi/` and install `requirements.txt`.
2. Copy `.env.example` to `.env`; configure the MySQL connection, `API_SECRET`, and administrator credentials.
3. From the repository root, run `pnpm --dir versions/react/pms-react install --frozen-lockfile` followed by `pnpm --dir versions/react/pms-react run build`. The React bundle is generated in `versions/python/pms-fastapi/app/web/`.
4. Start FastAPI with `versions/python/pms-fastapi/start.sh`. Visit `http://127.0.0.1:8000/`; `/docs` provides the interactive API reference.

The development proxy defaults to the FastAPI API on port 8000. See the [React setup guide](../../react/pms-react/setup.md) for independent frontend development.
