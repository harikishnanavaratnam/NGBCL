import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import s from '@/features/public/Public.module.css';
import { PageBand, TeamGrid } from '@/features/public/Sections';
import { getPublicTeams, getPublicTournament } from '@/lib/api/public';

export const metadata = { title: 'Teams', description: 'The NGBCL 1.0 franchises — squads built at the player auction.' };

export default async function TeamsPage() {
  const [teams, t] = await Promise.all([getPublicTeams(), getPublicTournament()]);
  return (
    <>
      <PageBand eyebrow="NGBCL 1.0" title="Teams">
        <span className="muted">
          {teams.length ? `${teams.length} franchises · squads built at the auction` : 'Franchises are announced after review'}
        </span>
      </PageBand>
      <section className={clsx(s.sec)} style={{ borderTop: 0 }}>
        <div className="wrap">
          {teams.length ? (
            <TeamGrid teams={teams} />
          ) : (
            <EmptyState
              title="Teams are announced after review"
              description="Approved franchises appear here with their crests. Squads are built at the player auction."
              action={
                t?.teamRegistration.state === 'OPEN'
                  ? { label: 'Enter a team', href: '/register/team' }
                  : { label: 'Back to home', href: '/' }
              }
            />
          )}
          {teams.length > 0 && t?.teamRegistration.state === 'OPEN' && (
            <p className="muted" style={{ marginTop: 24 }}>
              Want your own franchise?{' '}
              <Link href="/register/team" className="link">
                Enter a team
              </Link>
            </p>
          )}
        </div>
      </section>
    </>
  );
}
