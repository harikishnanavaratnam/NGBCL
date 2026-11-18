import clsx from 'clsx';
import { EmptyState } from '@/design-system/data/States';
import { PlayersBrowser } from '@/features/public/PlayersBrowser';
import s from '@/features/public/Public.module.css';
import { PageBand } from '@/features/public/Sections';
import { getPublicPlayers, getPublicTournament } from '@/lib/api/public';

export const metadata = { title: 'Players', description: 'Approved NGBCL 1.0 players — the auction pool.' };

export default async function PlayersPage() {
  const [players, t] = await Promise.all([getPublicPlayers(), getPublicTournament()]);
  return (
    <>
      <PageBand eyebrow="Approved players only" title="Players" />
      <section className={clsx(s.sec)} style={{ borderTop: 0, paddingTop: 32 }}>
        <div className="wrap">
          {players.length ? (
            <PlayersBrowser players={players} />
          ) : (
            <EmptyState
              title="No approved players yet"
              description="Players appear here once the organisers approve their registration."
              action={
                t?.playerRegistration.state === 'OPEN'
                  ? { label: 'Register as a player', href: '/register/player' }
                  : { label: 'Back to home', href: '/' }
              }
            />
          )}
        </div>
      </section>
    </>
  );
}
