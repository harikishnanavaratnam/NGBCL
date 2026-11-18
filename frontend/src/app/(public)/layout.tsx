import { SiteFooter } from '@/features/public-shell/SiteFooter';
import { SiteHeader } from '@/features/public-shell/SiteHeader';

export default function PublicLayout({ children }: LayoutProps<'/'>) {
  return (
    <>
      {/* Live summary comes from /api/public/live (M6); null hides the strip. */}
      <SiteHeader live={null} />
      <main id="main">{children}</main>
      <SiteFooter />
    </>
  );
}
