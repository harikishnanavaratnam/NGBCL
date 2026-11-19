import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './helpers';

/** M5 smoke: competition admin screens and the public fixtures page render. */
test('competition screens render for staff', async ({ page }) => {
  await signInAsAdmin(page);
  await page.goto('/admin/system/settings/match-categories');
  await expect(page.getByRole('heading', { name: 'Match categories' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'MS', exact: true })).toBeVisible();
  await page.goto('/admin/competition/format');
  await expect(page.getByRole('heading', { name: 'Format and draw' })).toBeVisible();
  await page.goto('/admin/competition/fixtures');
  await expect(page.getByRole('heading', { name: 'Fixtures', exact: true })).toBeVisible();
  await page.goto('/admin/competition/schedule');
  await expect(page.locator('body')).toContainText(/Schedule board|No fixtures yet/);
});

test('public fixtures page renders', async ({ page }) => {
  const res = await page.goto('/fixtures');
  expect(res?.status()).toBeLessThan(400);
  await expect(page.getByRole('heading', { level: 1, name: /fixtures/i })).toBeVisible();
  await expect(page.locator('body')).not.toContainText('@example.com');
});
