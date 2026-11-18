import { revalidateTag } from 'next/cache';
import { timingSafeEqual } from 'node:crypto';

/** Cache tags the API may refresh (see backend SiteRevalidator). */
const TAGS = new Set(['tournament', 'teams', 'players', 'news', 'sponsors', 'committee']);

function sameSecret(a: string, b: string) {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

/**
 * On-demand refresh of public pages after content changes. Called by the API with a shared
 * secret (REVALIDATE_SECRET). Without the secret configured, this endpoint is disabled.
 */
export async function POST(request: Request) {
  const secret = process.env.REVALIDATE_SECRET;
  const sent = request.headers.get('x-revalidate-secret') ?? '';
  if (!secret || !sameSecret(sent, secret)) {
    return Response.json({ error: 'forbidden' }, { status: 403 });
  }
  let tags: unknown;
  try {
    tags = ((await request.json()) as { tags?: unknown }).tags;
  } catch {
    return Response.json({ error: 'bad request' }, { status: 400 });
  }
  if (!Array.isArray(tags)) return Response.json({ error: 'bad request' }, { status: 400 });
  const applied = tags.filter((t): t is string => typeof t === 'string' && TAGS.has(t));
  applied.forEach((t) => revalidateTag(t, 'max'));
  return Response.json({ revalidated: applied });
}
