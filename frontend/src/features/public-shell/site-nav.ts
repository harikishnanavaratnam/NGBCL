export const PUBLIC_NAV = [
  { href: '/', label: 'Home' },
  { href: '/teams', label: 'Teams' },
  { href: '/players', label: 'Players' },
  { href: '/auction', label: 'Auction' },
  { href: '/fixtures', label: 'Fixtures' },
  { href: '/live', label: 'Live' },
  { href: '/standings', label: 'Standings' },
  { href: '/news', label: 'News' },
] as const;

/** Configured by admins in Branding settings (M1); this is the launch default. */
export const FACEBOOK_URL = 'https://www.facebook.com/share/1Ea6AVLhXd/?mibextid=wwXIfr';
