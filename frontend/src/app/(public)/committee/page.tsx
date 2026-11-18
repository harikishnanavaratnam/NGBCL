import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import { organiserContactHref } from '@/features/public/contact';
import s from '@/features/public/Public.module.css';
import { PageBand } from '@/features/public/Sections';
import { getCommittee, getPublicTournament, type PublicCommitteeGroup } from '@/lib/api/public';
import { initials, splitName } from '@/lib/format/names';

export const metadata = {
  title: 'Organising committee',
  description: 'The people behind NGBCL 1.0, organised by Mighty Sharks Battalion, Jaffna.',
};

export default async function CommitteePage() {
  const [groups, t] = await Promise.all([getCommittee(), getPublicTournament()]);
  return (
    <>
      <PageBand
        crumb={
          <>
            <Link href="/">Home</Link> / About / Committee
          </>
        }
        title="Organising committee"
      >
        <p style={{ margin: 0, maxWidth: 640 }}>
          The people behind NGBCL 1.0. The league is organised by {t?.organiser ?? 'Mighty Sharks Battalion'}, Jaffna.
        </p>
      </PageBand>
      <section className={s.sec} style={{ borderTop: 0, paddingTop: 32 }}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 48 }}>
          <div className="card" style={{ padding: 20, display: 'flex', gap: 16, alignItems: 'center', maxWidth: 560 }}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/brand/mighty-sharks.webp" alt="" width={64} height={64} style={{ borderRadius: 8, objectFit: 'cover' }} />
            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              <span className="eyebrow">Organiser</span>
              <strong>{t?.organiser ?? 'Mighty Sharks Battalion'}</strong>
              <span className="muted" style={{ fontSize: 13 }}>
                Jaffna, Sri Lanka · also runs the NGCL cricket tournament
              </span>
            </div>
          </div>
          {groups.length === 0 ? (
            <EmptyState title="Committee announced soon" description="The organising committee will be listed here." />
          ) : (
            groups.map((g) => <Group key={g.name} g={g} />)
          )}
          <div id="contact" className="card" style={{ padding: 24, display: 'flex', flexDirection: 'column', gap: 10, maxWidth: 560 }}>
            <h2 style={{ margin: 0, fontSize: 20 }}>Contact the organisers</h2>
            <span className="muted">One channel for all questions — registrations, sponsorship and media.</span>
            <a href={organiserContactHref(t)} className="btn btn-volt" style={{ alignSelf: 'flex-start' }}>
              {t?.contactEmail ? `Email ${t.contactEmail}` : 'Message us on Facebook'}
            </a>
          </div>
        </div>
      </section>
    </>
  );
}

function Portrait({ name, url }: { name: string; url: string | null }) {
  // eslint-disable-next-line @next/next/no-img-element
  return url ? <img src={url} alt={name} className={s.photo} /> : <div className={s.initials}>{initials(name)}</div>;
}

function Group({ g }: { g: PublicCommitteeGroup }) {
  if (g.style === 'LEAD') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="eyebrow">{g.name}</div>
        <div className={s.lead}>
          {g.members.map((m) => {
            const n = splitName(m.fullName);
            return (
              <article key={m.fullName + m.role} className={clsx('card', s.member)}>
                <Portrait name={m.fullName} url={m.photoUrl} />
                <span className="eyebrow">{m.role}</span>
                <h2 style={{ margin: 0, fontSize: 20 }}>
                  {n.first} <span style={{ textTransform: 'uppercase' }}>{n.last}</span>
                </h2>
                {m.bio && (
                  <span className="muted" style={{ fontSize: 14 }}>
                    {m.bio}
                  </span>
                )}
              </article>
            );
          })}
        </div>
      </div>
    );
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <h2 className={clsx('disp', s.h2)}>{g.name}</h2>
      <hr className="court" />
      <div className={s.members}>
        {g.members.map((m) => (
          <article key={m.fullName + m.role} className={clsx('card', s.memberCompact)}>
            {m.photoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={m.photoUrl} alt={m.fullName} />
            ) : (
              <div className={s.initials}>{initials(m.fullName)}</div>
            )}
            <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <strong style={{ fontSize: 14 }}>{m.fullName}</strong>
              <span className="muted" style={{ fontSize: 13 }}>
                {m.role}
              </span>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
}
