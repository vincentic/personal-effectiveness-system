import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  const apiTarget = env.VITE_API_PROXY_TARGET || "http://127.0.0.1:8000";

  return {
    plugins: [react()],
    build: {
      outDir: "../../python/pms-fastapi/app/web",
      emptyOutDir: true,
    },
    server: {
      port: 5174,
      strictPort: true,
      proxy: {
        "/api": apiTarget,
        "/health": apiTarget,
      },
    },
  };
})
