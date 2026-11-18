import Link from 'next/link';
import { OrTbc } from '@/design-system/primitives/Text';
import { FACEBOOK_URL } from './site-nav';
import styles from './SiteFooter.module.css';

/** Footer — navigation, organiser & contact (export Main · 13). Contact details come from settings in M1. */
export interface FooterInfo {
  contactEmail?: string | null;
  contactPhone?: string | null;
  facebookUrl?: string | null;
  organiserLogoUrl?: string | null;
  organiser?: string | null;
}

export function SiteFooter({ info = {} }: { info?: FooterInfo }) {
  const fb = info.facebookUrl ?? FACEBOOK_URL;
  return (
    <footer className={styles.footer}>
      <div className={`wrap ${styles.inner}`}>
        <div className={styles.grid}>
          <div className={styles.brandCol}>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/brand/ngbcl-wordmark-on-dark.svg" alt="NGBCL — Next Gen Badminton Champions League" style={{ height: 56, width: 'auto', alignSelf: 'flex-start' }} />
            <span className={styles.tag}>Rise. Rally. Rule the Court.</span>
            <span className="muted" style={{ fontSize: 14 }}>
              Next Gen Badminton Champions League 1.0
            </span>
          </div>
          <div className={styles.col}>
            <span className="eyebrow">League</span>
            <Link href="/#about">About</Link>
            <Link href="/committee">Organising committee</Link>
            <Link href="/teams">Teams</Link>
            <Link href="/players">Players</Link>
            <Link href="/auction">Auction</Link>
          </div>
          <div className={styles.col}>
            <span className="eyebrow">Competition</span>
            <Link href="/fixtures">Fixtures</Link>
            <Link href="/live">Live scores</Link>
            <Link href="/standings">Standings</Link>
            <Link href="/news">News</Link>
          </div>
          <div className={styles.col}>
            <span className="eyebrow">Take part</span>
            <Link href="/register/player">Register as a player</Link>
            <Link href="/register/team">Enter a team</Link>
            <Link href="/privacy">Privacy notice</Link>
          </div>
          <div className={`${styles.col} ${styles.contact}`}>
            <span className="eyebrow">Organiser &amp; contact</span>
            <div className={styles.org}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={info.organiserLogoUrl ?? '/brand/mighty-sharks.webp'} alt={`${info.organiser ?? 'Mighty Sharks Battalion'} logo`} width={48} height={48} />
              <strong>{info.organiser ?? 'Mighty Sharks Battalion'}</strong>
            </div>
            <span className="muted">Jaffna, Sri Lanka</span>
            <span>
              {info.contactEmail ? <a href={`mailto:${info.contactEmail}`}>{info.contactEmail}</a> : <OrTbc value={null} label="CONTACT EMAIL" />}
            </span>
            <span>
              {info.contactPhone ? <a href={`tel:${info.contactPhone.replace(/\s/g, '')}`}>{info.contactPhone}</a> : <OrTbc value={null} label="CONTACT PHONE" />}
            </span>
            <a href={fb} className="link" style={{ alignSelf: 'flex-start' }} target="_blank" rel="noopener noreferrer">
              Facebook page
            </a>
            <span className="muted" style={{ fontSize: 13 }}>
              Also organisers of the NGCL cricket tournament.
            </span>
          </div>
        </div>
        <hr className="court" />
        <div className={`${styles.bottom} muted`}>
          <span>© 2026 Mighty Sharks Battalion. All rights reserved.</span>
          <span>NGBCL 1.0 · Inaugural edition</span>
        </div>
      </div>
    </footer>
  );
}
