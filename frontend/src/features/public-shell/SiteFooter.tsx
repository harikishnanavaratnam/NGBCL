import Link from 'next/link';
import { Tbc } from '@/design-system/primitives/Text';
import { FACEBOOK_URL } from './site-nav';
import styles from './SiteFooter.module.css';

/** Footer — navigation, organiser & contact (export Main · 13). Contact details come from settings in M1. */
export function SiteFooter() {
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
              <img src="/brand/mighty-sharks.webp" alt="Mighty Sharks Battalion logo" width={48} height={48} />
              <strong>Mighty Sharks Battalion</strong>
            </div>
            <span className="muted">Jaffna, Sri Lanka</span>
            <span>
              <Tbc>[CONTACT EMAIL TBC]</Tbc>
            </span>
            <span>
              <Tbc>[CONTACT PHONE TBC]</Tbc>
            </span>
            <a href={FACEBOOK_URL} className="link" style={{ alignSelf: 'flex-start' }} target="_blank" rel="noopener noreferrer">
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
