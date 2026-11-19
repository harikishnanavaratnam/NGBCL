import { expect, test } from '@playwright/test';
import { API, adminToken, approvedPlayer, openRegistration, signInAsAdmin, uniq } from './helpers';

/**
 * M4: rules → setup → start → console bidding (keyboard + idempotent REST) → undo → SOLD →
 * public pages. The edition has one auction, so on a database where it already ran this skips.
 */
test('run a lot from start to sale', async ({ page, request }) => {
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };
  const snap = await (await request.get(`${API}/api/admin/auction`, { headers: auth })).json();
  test.skip(snap.status !== 'NOT_STARTED', 'The auction already ran on this database.');

  // Seed: two teams and two approved players
  await openRegistration(request);
  const id = uniq();
  for (const n of ['North', 'South']) {
    const r = await request.post(`${API}/api/admin/teams`, { headers: auth, data: { name: `E2E ${n} ${id}` } });
    expect(r.ok()).toBeTruthy();
  }
  const playerA = `Auction Alpha ${id}`;
  await approvedPlayer(request, playerA);
  await approvedPlayer(request, `Auction Beta ${id}`);

  await signInAsAdmin(page);

  // Rules
  await page.goto('/admin/system/settings/auction');
  await expect(page.getByRole('heading', { name: 'Auction', exact: true })).toBeVisible();
  await page.getByLabel('Minimum bid increment (LKR)').fill('5000');
  await page.getByLabel('Default base price (LKR)').fill('10000');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByText('Auction settings saved')).toBeVisible();

  // Setup → start
  await page.goto('/admin/auction/setup');
  await expect(page.getByRole('heading', { name: 'Auction setup' })).toBeVisible();
  await expect(page.getByText(playerA)).toBeVisible();
  await page.getByRole('button', { name: 'Start auction' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Start auction' }).click();
  await expect(page.getByText('The auction is live')).toBeVisible();

  // Console
  await page.goto('/console/auction');
  await page.getByRole('button', { name: `Put ${playerA} up` }).click();
  await expect(page.getByText('No bids', { exact: true })).toBeVisible();
  await page.keyboard.press('1');
  await page.getByRole('button', { name: 'Place bid' }).click();
  await expect(page.getByText('Accepted', { exact: true })).toBeVisible();
  await page.keyboard.press('2');
  await page.getByRole('button', { name: 'Place bid' }).click();
  await expect(page.getByText('LKR 15,000').first()).toBeVisible();
  await page.getByRole('button', { name: 'Undo last bid' }).click();
  await expect(page.getByText('Undone')).toBeVisible();
  await page.getByRole('button', { name: 'Sold', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm sale' }).click();
  await expect(page.getByText(`${playerA} sold to`)).toBeVisible();

  // Public pages
  await page.goto('/auction');
  await expect(page.getByText(playerA).first()).toBeVisible();
  await page.goto('/display/auction');
  await expect(page.getByText(/next player/i).first()).toBeVisible();
});
