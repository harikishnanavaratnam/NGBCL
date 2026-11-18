import type { PublicTournament } from '@/lib/api/public';
import { FACEBOOK_URL } from '@/features/public-shell/site-nav';

/** Where "Become a sponsor" / "Contact the organisers" point: email, then Facebook. */
export function organiserContactHref(t: PublicTournament | null) {
  if (t?.contactEmail) return `mailto:${t.contactEmail}?subject=${encodeURIComponent('NGBCL 1.0 sponsorship')}`;
  return t?.facebookUrl ?? FACEBOOK_URL;
}
