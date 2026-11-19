import { expect, test } from '@playwright/test';
import { signInAsAdmin } from './helpers';

/** M6 smoke: scoring rules, the scorer app, result confirmations and the public live page render. */
test('scoring screens render for staff', async ({ page }) => {
  await signInAsAdmin(page);
  await page.goto('/admin/system/settings/scoring');
  await expect(page.getByRole('heading', { name: 'Scoring rules' })).toBeVisible();
  await expect(page.getByLabel('Points to win a game')).toHaveValue(/\d+/);
  await page.goto('/admin/results/confirmations');
  await expect(page.getByRole('heading', { name: 'Result confirmations' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Waiting for confirmation' })).toBeVisible();
  await page.goto('/score');
  await expect(page.getByRole('navigation', { name: 'Scorer' })).toBeVisible();
  await expect(page.locator('main')).toContainText(/No matches to score|Line-up|Not started|Live/);
});

test('scoring rules validate the cap', async ({ page }) => {
  await signInAsAdmin(page);
  await page.goto('/admin/system/settings/scoring');
  const cap = page.getByLabel('Point cap');
  await cap.fill('15');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Must be at least the points to win')).toBeVisible();
  await page.getByRole('button', { name: 'Discard' }).click();
});

test('public live page renders', async ({ page }) => {
  const res = await page.goto('/live');
  expect(res?.status()).toBeLessThan(400);
  await expect(page.getByRole('heading', { level: 1, name: /live/i })).toBeVisible();
  await expect(page.getByRole('heading', { name: /on court now/i })).toBeVisible();
});
