import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end tests against a running stack: API on E2E_API_URL (default :8080, local profile with
 * local-disk storage) and the built frontend on E2E_BASE_URL (default :3000).
 * Locally: start the backend (`./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), then
 * `npm run build && npm start`, then `npm run e2e`.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // Optional: reuse an already-installed Chromium instead of `npx playwright install`
    launchOptions: process.env.PW_CHROMIUM_PATH ? { executablePath: process.env.PW_CHROMIUM_PATH } : {},
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] }, testMatch: /(register|public)\.spec\.ts/ },
  ],
});
