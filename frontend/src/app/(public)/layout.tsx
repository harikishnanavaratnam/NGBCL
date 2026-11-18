import { SiteFooter } from '@/features/public-shell/SiteFooter';
import { SiteHeader } from '@/features/public-shell/SiteHeader';
import { getPublicTournament } from '@/lib/api/public';

export default async function PublicLayout({ children }: LayoutProps<'/'>) {
  const t = await getPublicTournament();
  return (
    <>
      {/* Live summary comes from /api/public/live (M6); null hides the strip. */}
      <SiteHeader live={null} facebookUrl={t?.facebookUrl} />
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
