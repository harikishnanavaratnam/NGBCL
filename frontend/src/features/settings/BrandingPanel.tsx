'use client';

import { useMemo, useState } from 'react';
import { ImageUploader } from '@/design-system/sport/ImageUploader';
import { useToast } from '@/design-system/primitives/Toast';
import { TEAM_COLORS } from '@/design-system/tokens';
import type { FileAssetView, FilePurpose } from '@/lib/api/files';
import { useSaveBranding, useSettings, type Settings } from '@/lib/api/settings';
import { useAuth } from '@/lib/auth/session';
import { PanelError, PanelLoading } from './PanelStates';
import { SaveBar } from './SaveBar';
import styles from './Settings.module.css';
import { useSaveErrors } from './useSaveErrors';

type Slot = 'logo' | 'favicon' | 'organiserLogo' | 'poster';

const SLOTS: { slot: Slot; purpose: FilePurpose; label: string; hint?: string; fallback: string; previewAspect?: number }[] = [
  { slot: 'logo', purpose: 'TOURNAMENT_LOGO', label: 'Tournament logo', hint: 'Transparent PNG on dark works best · min 256 × 128 px', fallback: '/brand/ngbcl-logo-on-dark.svg', previewAspect: 1.55 },
  { slot: 'favicon', purpose: 'FAVICON', label: 'Favicon / app icon', fallback: '/brand/favicon.svg' },
  { slot: 'organiserLogo', purpose: 'ORGANISER_LOGO', label: 'Organiser logo', fallback: '/brand/mighty-sharks.webp' },
  { slot: 'poster', purpose: 'POSTER', label: 'Poster frame', hint: 'Still image for first paint and slow connections · also the share image · 16 : 9', fallback: '/brand/hero-poster.jpg', previewAspect: 16 / 9 },
];

export function BrandingPanel() {
  const { data, isLoading, error, refetch } = useSettings();
  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;
  return <BrandingForm key={data.settingsVersion} settings={data} />;
}

function BrandingForm({ settings }: { settings: Settings }) {
  const toast = useToast();
  const { can } = useAuth();
  const canEdit = can('SETTINGS_MANAGE') && can('FILE_UPLOAD');
  const save = useSaveBranding();
  const onError = useSaveErrors();
  const initial = useMemo(
    () => ({
      logo: settings.branding.logo?.id ?? null,
      favicon: settings.branding.favicon?.id ?? null,
      organiserLogo: settings.branding.organiserLogo?.id ?? null,
      poster: settings.branding.poster?.id ?? null,
    }),
    [settings],
  );
  const [ids, setIds] = useState<Record<Slot, string | null>>(initial);
  const [uploaded, setUploaded] = useState<Partial<Record<Slot, FileAssetView>>>({});
  const dirty = (Object.keys(ids) as Slot[]).some((k) => ids[k] !== initial[k]);

  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    save.mutate(
      { logoAssetId: ids.logo, faviconAssetId: ids.favicon, organiserLogoAssetId: ids.organiserLogo, posterAssetId: ids.poster, settingsVersion: settings.settingsVersion },
      { onSuccess: () => toast.success('Branding saved and published'), onError },
    );
  };

  return (
    <form className={styles.panel} onSubmit={submit} noValidate>
      <section className={styles.section}>
        <h2 className="t-h2">Branding</h2>
        <p className="t-body-s muted">Uploads are checked on the server (real file type, size and dimensions). Saving publishes them to the public site; until then the bundled artwork is used.</p>
        <div className={styles.grid2}>
          {SLOTS.map((s) => {
            const current = settings.branding[s.slot];
            return (
              <ImageUploader
                key={s.slot}
                purpose={s.purpose}
                label={s.label}
                hint={s.hint}
                previewAspect={s.previewAspect}
                currentUrl={uploaded[s.slot]?.previewUrl ?? current?.publicUrl ?? current?.previewUrl ?? s.fallback}
                onUploaded={(asset) => {
                  if (!canEdit) return;
                  setUploaded((u) => ({ ...u, [s.slot]: asset }));
                  setIds((v) => ({ ...v, [s.slot]: asset.id }));
                }}
              />
            );
          })}
        </div>
      </section>

      <section className={styles.section}>
        <h3 className="t-h3">Hero background video</h3>
        <div className="panel-flat" style={{ padding: 14, display: 'flex', gap: 14, alignItems: 'center', flexWrap: 'wrap' }}>
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/hero-poster.jpg" alt="" width={160} height={74} style={{ borderRadius: 6, objectFit: 'cover' }} />
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <strong style={{ fontSize: 14 }}>Bundled with the site</strong>
            <span className="help">WebM 170 KB + MP4 478 KB · 7.5 s muted loop · sponsor marks removed · pauses off-screen and with reduced motion. Replacing it is a code change.</span>
          </div>
        </div>
      </section>

      <section className={styles.section}>
        <h3 className="t-h3">Team palette</h3>
        <p className="help">One colour per team — used only as a 4 px band, crest ring and chart colour. Assigned when teams are approved (Milestone 2).</p>
        <div className="scroll">
          <table className="tbl">
            <thead>
              <tr>
                <th>Slot</th>
                <th>Colour</th>
                <th>Assigned team</th>
              </tr>
            </thead>
            <tbody>
              {TEAM_COLORS.map((c) => (
                <tr key={c.slot}>
                  <td>
                    T{c.slot} {c.name}
                  </td>
                  <td>
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                      <span style={{ width: 16, height: 16, borderRadius: 3, background: c.hex, display: 'inline-block' }} aria-hidden="true" />
                      <span className="mono">{c.hex}</span>
                    </span>
                  </td>
                  <td className="muted">Unassigned</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <SaveBar dirty={dirty} saving={save.isPending} updatedAt={settings.updatedAt} onDiscard={() => { setIds(initial); setUploaded({}); }} />
    </form>
  );
}
