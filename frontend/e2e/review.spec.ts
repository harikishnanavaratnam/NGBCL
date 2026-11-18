import path from 'node:path';
import { expect, test } from '@playwright/test';
import { adminToken, openRegistration, uniq } from './helpers';

/** Registers through the public form, then reviews and approves it in the admin UI. */
test('staff verify the photo and approve a registration', async ({ page, request }) => {
  await openRegistration(request);
  const id = uniq();
  const name = `E2E Review ${id}`;

  // Applicant side (UI) — then the reviewer picks it up
  await page.goto('/register/player');
  await page.getByLabel('Full name').fill(name);
  const dob = page.getByLabel('Date of birth');
  if (await dob.isVisible()) await dob.fill('1998-02-02');
  const male = page.getByText('Male', { exact: true });
  if (await male.isVisible()) await male.click();
  await page.getByLabel('Email address').fill(`review-${id}@example.com`);
  await page.getByLabel('Contact number').fill(`76${Math.floor(1_000_000 + Math.random() * 8_999_999)}`);
  const area = page.getByLabel('Home area');
  if (await area.isVisible()) await area.fill('Kopay');
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await page.locator('input[type=file]').first().setInputFiles(path.join(__dirname, 'fixtures-face.jpg'));
  await page.getByRole('button', { name: 'Use this photo' }).click();
  await expect(page.getByText('Uploaded')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await page.getByRole('button', { name: 'Save and continue' }).click();
  await page.locator('#decl-jaffna input').check();
  await page.locator('#decl-consent input').check();
  await page.getByRole('button', { name: 'Submit registration' }).click();
  await expect(page.getByTestId('reference')).toBeVisible({ timeout: 20_000 });

  // Sign in as staff
  await adminToken(request); // fail fast with a clear message if credentials are wrong
  await page.goto('/login');
  await page.getByLabel('Email').fill(process.env.E2E_ADMIN_EMAIL ?? 'admin@ngbcl.local');
  await page.getByLabel('Password').fill(process.env.E2E_ADMIN_PASSWORD ?? 'ChangeMe-Local-123');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/admin/);

  await page.goto('/admin/registrations/players');
  await page
    .getByPlaceholder(/search/i)
    .first()
    .fill(name);
  await page.getByText(name).first().click();

  const drawer = page.getByRole('dialog');
  await expect(drawer.getByRole('button', { name: 'Approve (A)' })).toBeDisabled();
  await drawer.getByRole('button', { name: 'Verify photo' }).click();
  await expect(drawer.getByText('Verified', { exact: true })).toBeVisible();
  await drawer.getByRole('button', { name: 'Approve (A)' }).click();
  await page.getByRole('button', { name: 'Approve player' }).click();
  await expect(page.getByText(`${name} approved`)).toBeVisible();

  await page.goto('/admin/directory/players');
  await page
    .getByPlaceholder(/search/i)
    .first()
    .fill(name);
  await expect(page.getByText('Approved').first()).toBeVisible();
});
