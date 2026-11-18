import path from 'node:path';
import { expect, test } from '@playwright/test';
import { openRegistration, uniq } from './helpers';

test.beforeAll(async ({ request }) => {
  await openRegistration(request);
});

test('a player registers with a photo and gets a reference number', async ({ page }) => {
  const id = uniq();
  await page.goto('/register/player');
  await expect(page.getByRole('heading', { name: /register as a player/i })).toBeVisible();

  // Required fields are enforced before moving on
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await expect(page.getByText('Enter your full name.')).toBeVisible();

  await page.getByLabel('Full name').fill(`E2E Player ${id}`);
  const dob = page.getByLabel('Date of birth');
  if (await dob.isVisible()) await dob.fill('2000-05-14');
  const male = page.getByText('Male', { exact: true });
  if (await male.isVisible()) await male.click();
  await page.getByLabel('Email address').fill(`e2e-${id}@example.com`);
  await page.getByLabel('Contact number').fill(`77${Math.floor(1_000_000 + Math.random() * 8_999_999)}`);
  const area = page.getByLabel('Home area');
  if (await area.isVisible()) await area.fill('Nallur');
  await page.getByRole('button', { name: 'Save and continue' }).click();

  await page.locator('input[type=file]').first().setInputFiles(path.join(__dirname, 'fixtures-face.jpg'));
  await page.getByRole('button', { name: 'Use this photo' }).click();
  await expect(page.getByText('Uploaded')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Save and continue' }).click();

  await page.getByRole('button', { name: 'Save and continue' }).click();
  await expect(page.getByRole('heading', { name: 'Review and submit' })).toBeVisible();

  await page.getByRole('button', { name: 'Submit registration' }).click();
  await expect(page.getByText(/things? needs? your attention/)).toBeVisible();
  await page.locator('#decl-jaffna input').check();
  await page.locator('#decl-consent input').check();
  await page.getByRole('button', { name: 'Submit registration' }).click();

  await expect(page.getByTestId('reference')).toHaveText(/^PLY-\d{4}-\d{5}$/, { timeout: 20_000 });
});

test('a team checks its name live and submits an application', async ({ page }) => {
  const id = uniq();
  await page.goto('/register/team');
  await page.getByLabel('Team name').fill(`E2E Smashers ${id}`);
  await expect(page.getByText('Name available')).toBeVisible();
  await page.locator('input[type=file]').first().setInputFiles(path.join(__dirname, 'fixtures-logo.png'));
  await page.getByRole('button', { name: 'Use this logo' }).click();
  await expect(page.getByText('Uploaded')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await page.getByLabel('Full name').fill('E2E Owner');
  await page.getByLabel('Email address').fill(`owner-${id}@example.com`);
  await page.getByLabel('Contact number').fill('771234567');
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await page.locator('#decl-auth input').check();
  await page.locator('#decl-fee input').check();
  await page.locator('#decl-consent input').check();
  await page.getByRole('button', { name: 'Submit application' }).click();
  await expect(page.getByTestId('reference')).toHaveText(/^TEM-\d{4}-\d{4}$/, { timeout: 20_000 });
});
