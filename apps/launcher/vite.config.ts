import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 1420,
    strictPort: true,
    watch: {
      ignored: ["**/src-tauri/**"],
      awaitWriteFinish: { stabilityThreshold: 120, pollInterval: 50 },
    },
  },
  clearScreen: false,
  build: {
    target: "es2022",
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("skinview3d") || id.includes("three")) return "skin";
          if (id.includes("motion")) return "motion";
        },
      },
    },
  },
});
