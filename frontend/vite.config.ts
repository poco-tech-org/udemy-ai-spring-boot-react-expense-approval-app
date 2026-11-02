import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: Object.fromEntries(
      ["/api", "/oauth2", "/login", "/logout"].map((p) => [
        p,
        { target: "http://localhost:8080", changeOrigin: false },
      ]),
    ),
  },
  test: {
    include: ["src/**/*.test.{ts,tsx}"],
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
  },
});
