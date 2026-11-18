import type { APIRequestContext } from '@playwright/test';

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
