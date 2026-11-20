# FastAPI + React build process

The backend uses Python dependencies and Uvicorn. The React frontend is an independent pnpm project but its production bundle is emitted into FastAPI's `app/web/` directory, which the API serves from `/`.

From the repository root:

```bash
pnpm --dir versions/react/pms-react install --frozen-lockfile
pnpm --dir versions/react/pms-react run typecheck
pnpm --dir versions/react/pms-react run build
```

The build output contains `index.html` and hashed assets. Build React before packaging or starting a production deployment so the API serves the current frontend.
