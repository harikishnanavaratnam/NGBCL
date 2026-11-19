import { SiteFooter } from '@/features/public-shell/SiteFooter';
import { LiveSiteHeader } from '@/features/public-shell/LiveSiteHeader';
import { getPublicTournament } from '@/lib/api/public';

export default async function PublicLayout({ children }: LayoutProps<'/'>) {
  const t = await getPublicTournament();
  return (
    <>
      {/* Live strip comes from /api/public/live; hidden when nothing is on court. */}
      <LiveSiteHeader facebookUrl={t?.facebookUrl} />
      <main id="main">{children}</main>
      <SiteFooter
        info={{
          contactEmail: t?.contactEmail,
          contactPhone: t?.contactPhone,
          facebookUrl: t?.facebookUrl,
          organiserLogoUrl: t?.branding.organiserLogoUrl,
          organiser: t?.organiser,
        }}
      />
    </>
  );
}
