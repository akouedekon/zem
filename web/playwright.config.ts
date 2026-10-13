import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './e2e', workers: 1, timeout: 60000,
  use: { baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000', browserName: 'chromium',
    ...(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {}), trace: 'retain-on-failure' },
  reporter: 'list',
  webServer: process.env.E2E_BASE_URL ? undefined : { command: 'npm run start', url: 'http://localhost:3000', reuseExistingServer: !process.env.CI,
    env: { API_INTERNAL_URL: 'http://127.0.0.1:18080', WEB_ORIGIN: 'http://localhost:3000' } }
});
