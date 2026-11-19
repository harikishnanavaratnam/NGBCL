import fs from 'node:fs';
import path from 'node:path';
import type { APIRequestContext, Page } from '@playwright/test';

export const API = (process.env.E2E_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? 'admin@ngbcl.local',
  password: process.env.E2E_ADMIN_PASSWORD ?? 'ChangeMe-Local-123',
};

export async function adminToken(request: APIRequestContext) {
  const res = await request.post(`${API}/api/auth/login`, {
    data: ADMIN,
    headers: { Origin: process.env.E2E_BASE_URL ?? 'http://localhost:3000' },
  });
  if (!res.ok()) throw new Error(`Admin login failed: ${res.status()} ${await res.text()}`);
  return (await res.json()).accessToken as string;
}

/** Opens both registration windows (and moves a draft tournament to UPCOMING) so the forms accept entries. */
export async function openRegistration(request: APIRequestContext) {
  const token = await adminToken(request);
  const headers = { Authorization: `Bearer ${token}` };
  let s = await (await request.get(`${API}/api/admin/settings`, { headers })).json();
  if (s.general.status === 'DRAFT') {
    await request.put(`${API}/api/admin/tournament/status`, { headers, data: { status: 'UPCOMING' } });
    s = await (await request.get(`${API}/api/admin/settings`, { headers })).json();
  }
  const day = 86_400_000;
  const reg = {
    ...s.registration,
    playerOpensAt: new Date(Date.now() - day).toISOString(),
    playerClosesAt: new Date(Date.now() + 10 * day).toISOString(),
    teamOpensAt: new Date(Date.now() - day).toISOString(),
    teamClosesAt: new Date(Date.now() + 10 * day).toISOString(),
    settingsVersion: s.settingsVersion,
  };
  const res = await request.put(`${API}/api/admin/settings/registration`, { headers, data: reg });
  if (!res.ok()) throw new Error(`Opening registration failed: ${res.status()} ${await res.text()}`);
}

export const uniq = () => Math.random().toString(36).slice(2, 8);

/** 4:5 portrait at the minimum size, so it passes the server's photo checks without cropping. */
const FACE = () => fs.readFileSync(path.join(__dirname, 'fixtures-player.jpg'));

/** Registers a player through the public API and approves it as staff (no UI) — for seeding. */
export async function approvedPlayer(request: APIRequestContext, fullName: string) {
  const token = await adminToken(request);
  const auth = { Authorization: `Bearer ${token}` };
  const id = uniq();
  const email = `seed-${id}@example.com`;
  const session = await (await request.post(`${API}/api/public/registration/sessions`, { data: { kind: 'PLAYER' } })).json();
  const reg = { 'X-Registration-Token': session.token as string };
  const photo = FACE();
  const ticket = await (await request.post(`${API}/api/public/registration/uploads`, { headers: reg, data: { contentType: 'image/jpeg', size: photo.length } })).json();
  const put = await request.fetch(ticket.uploadUrl, { method: ticket.method ?? 'PUT', headers: ticket.headers ?? { 'Content-Type': 'image/jpeg' }, data: photo });
  if (!put.ok()) throw new Error(`Photo upload failed: ${put.status()}`);
  const done = await request.post(`${API}/api/public/registration/uploads/${ticket.assetId}/complete`, { headers: reg });
  if (!done.ok()) throw new Error(`Photo check failed: ${done.status()} ${await done.text()}`);
  const res = await request.post(`${API}/api/public/registration/player`, {
    headers: reg,
    data: {
      fullName,
      email,
      phone: `+9476${Math.floor(1_000_000 + Math.random() * 8_999_999)}`,
      dateOfBirth: '1998-02-02',
      gender: 'MALE',
      homeArea: 'Kopay',
      photoAssetId: ticket.assetId,
      jaffnaDeclared: true,
      consent: true,
    },
  });
  if (!res.ok()) throw new Error(`Registration failed: ${res.status()} ${await res.text()}`);
  const list = await (await request.get(`${API}/api/admin/registrations/players?status=PENDING&q=${encodeURIComponent(email)}`, { headers: auth })).json();
  const regId = list.content[0].id as string;
  await request.post(`${API}/api/admin/registrations/players/${regId}/photo/verify`, { headers: auth, data: { approve: true } });
  const ok = await request.post(`${API}/api/admin/registrations/players/${regId}/approve`, { headers: auth, data: {} });
  if (!ok.ok()) throw new Error(`Approve failed: ${ok.status()} ${await ok.text()}`);
}

export async function signInAsAdmin(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Email').fill(ADMIN.email);
  await page.getByLabel('Password').fill(ADMIN.password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/admin/);
}
