import { expect, test } from '@playwright/test';

/** M3: every public page renders (with data or its empty state) and never shows contact details. */
const PAGES: { path: string; heading: RegExp }[] = [
  { path: '/', heading: /next gen badminton champions league/i },
  { path: '/teams', heading: /^teams$/i },
  { path: '/players', heading: /^players$/i },
  { path: '/news', heading: /^news$/i },
  { path: '/committee', heading: /organising committee/i },
];

for (const { path, heading } of PAGES) {
  test(`public page ${path} renders`, async ({ page }) => {
    const res = await page.goto(path);
    expect(res?.status()).toBeLessThan(400);
    await expect(page.getByRole('heading', { level: 1, name: heading })).toBeVisible();
    await expect(page.locator('body')).not.toContainText('@example.com');
  });
}

test('unknown team and player slugs show a friendly not-found state', async ({ page }) => {
  await page.goto('/teams/no-such-team');
  await expect(page.getByText('Team not found')).toBeVisible();
  await page.goto('/players/no-such-player');
  await expect(page.getByText('Player not found')).toBeVisible();
});
