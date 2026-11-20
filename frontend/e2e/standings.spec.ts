import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './helpers';

/** M7 smoke: standings, statistics, settings and reports render; public standings page renders. */
test('standings and reports screens render for staff', async ({ page }) => {
  await signInAsAdmin(page);
  await page.goto('/admin/system/settings/standings');
  await expect(page.getByRole('heading', { name: 'Tie-breakers' })).toBeVisible();
  await expect(page.getByLabel('Points for a tie won')).toHaveValue(/\d+/);
  await page.goto('/admin/results/standings');
  await expect(page.getByRole('heading', { name: 'Standings', exact: true })).toBeVisible();
  await page.goto('/admin/results/statistics');
  await expect(page.getByRole('heading', { name: 'Statistics', exact: true })).toBeVisible();
  await page.goto('/admin/system/reports');
  await expect(page.getByRole('heading', { name: 'Auction workbook' })).toBeVisible();
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'CSV', exact: true }).first().click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/^ngbcl-players-.*\.csv$/);
});

test('public standings page renders', async ({ page }) => {
  const res = await page.goto('/standings');
  expect(res?.status()).toBeLessThan(400);
  await expect(page.getByRole('heading', { level: 1, name: /standings/i })).toBeVisible();
  await expect(page.locator('body')).not.toContainText('@example.com');
});
