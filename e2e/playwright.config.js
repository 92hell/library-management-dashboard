import { defineConfig, devices } from '@playwright/test';

// The app under test: the jar built by `mvn -Pfullstack package`, on an in-memory H2 database
// (fresh every run) with a known first-librarian password. Reminder jobs are off.
const port = process.env.E2E_PORT ?? '8099';
export const LIBRARIAN = { username: 'librarian', password: 'e2e-librarian-password' };

export default defineConfig({
  testDir: './tests',
  // One app instance; tests create their own uniquely named data, so they never share state.
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://localhost:${port}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Lets a machine with a preinstalled Chromium skip `playwright install`.
        ...(process.env.PW_CHROMIUM_PATH ? { launchOptions: { executablePath: process.env.PW_CHROMIUM_PATH } } : {}),
      },
    },
  ],
  webServer: {
    command: 'java -jar ../target/library-dashboard-*.jar',
    url: `http://localhost:${port}/actuator/health`,
    timeout: 120_000,
    reuseExistingServer: !process.env.CI,
    stdout: 'ignore',
    stderr: 'pipe',
    env: {
      SERVER_PORT: port,
      SPRING_PROFILES_ACTIVE: 'h2',
      SPRING_DATASOURCE_URL: 'jdbc:h2:mem:e2e;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1',
      LIBRARIAN_USERNAME: LIBRARIAN.username,
      LIBRARIAN_PASSWORD: LIBRARIAN.password,
      LOAN_REMINDER_CRON: '-',
      ISBN_LOOKUP_URL: '',
      BOOK_COVERS: 'false',
      LOGGING_FILE_PATH: 'logs',
    },
  },
});
