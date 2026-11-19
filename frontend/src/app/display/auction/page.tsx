import type { Metadata } from 'next';
import { AuctionDisplay } from '@/features/display/AuctionDisplay';
import { getPublicTeams, getPublicTournament, getSponsors } from '@/lib/api/public';

export const metadata: Metadata = { title: 'Auction display', robots: { index: false } };

/** Projector / TV output (1920×1080, scaled). Open it full screen on the venue display. */
export default async function AuctionDisplayPage() {
  const [t, sponsors, teams] = await Promise.all([getPublicTournament(), getSponsors(), getPublicTeams()]);
  const title = sponsors.find((s) => s.tier === 'TITLE') ?? null;
  return (
    <AuctionDisplay
      presenterLine={t?.presenterLine ?? null}
      titleSponsor={title ? { name: title.name, logoUrl: title.logoUrl } : null}
      sponsors={sponsors.filter((s) => s.showOnTicker && s.tier !== 'TITLE').map((s) => ({ name: s.name, logoUrl: s.logoUrl }))}
      teams={teams.map((x) => ({ name: x.name, colorSlot: x.colorSlot, logoUrl: x.logoUrl }))}
    />
  );
}
