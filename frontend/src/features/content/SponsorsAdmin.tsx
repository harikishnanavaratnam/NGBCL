'use client';

import { useState } from 'react';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Checkbox, Input, Select, Toggle } from '@/design-system/primitives/Inputs';
import { Drawer } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { ImageUploader } from '@/design-system/sport/ImageUploader';
import s from '@/features/registrations/Registrations.module.css';
import { SPONSOR_TIERS, useReorderSponsors, useSaveSponsor, useSponsors, type Sponsor } from '@/lib/api/content';
import { ApiError, errorMessage } from '@/lib/api/errors';
import type { SponsorTier } from '@/lib/api/public';
import { useAuth } from '@/lib/auth/session';

const tierLabel = (t: SponsorTier) => SPONSOR_TIERS.find((x) => x.value === t)?.label ?? t;

export function SponsorsAdmin() {
  const { can } = useAuth();
  const canEdit = can('CONTENT_MANAGE');
  const toast = useToast();
  const { data, isLoading, error, refetch } = useSponsors();
  const reorder = useReorderSponsors();
  const [editing, setEditing] = useState<Sponsor | 'new' | null>(null);

  const move = (sp: Sponsor, delta: -1 | 1) => {
    if (!data) return;
    const sameTier = data.filter((x) => x.tier === sp.tier);
    const i = sameTier.findIndex((x) => x.id === sp.id);
    const j = i + delta;
    if (j < 0 || j >= sameTier.length) return;
    const ids = data.map((x) => x.id);
    const a = ids.indexOf(sameTier[i].id);
    const b = ids.indexOf(sameTier[j].id);
    [ids[a], ids[b]] = [ids[b], ids[a]];
    reorder.mutateAsync(ids).catch((e) => toast.error(errorMessage(e)));
  };

  const columns: Column<Sponsor>[] = [
    {
      key: 'order',
      header: <span className="sr-only">Order</span>,
      width: 72,
      cell: (sp) =>
        canEdit ? (
          <span style={{ display: 'inline-flex', gap: 4 }} onClick={(e) => e.stopPropagation()}>
            <Button size="sm" variant="ghost" aria-label={`Move ${sp.name} up`} onClick={() => move(sp, -1)} disabled={reorder.isPending}>
              ↑
            </Button>
            <Button size="sm" variant="ghost" aria-label={`Move ${sp.name} down`} onClick={() => move(sp, 1)} disabled={reorder.isPending}>
              ↓
            </Button>
          </span>
        ) : null,
    },
    {
      key: 'logo',
      header: 'Logo',
      cell: (sp) =>
        sp.logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={sp.logoUrl}
            alt=""
            style={{ height: 32, maxWidth: 96, objectFit: 'contain', background: '#F5F7FA', borderRadius: 4, padding: 4 }}
          />
        ) : (
          <span className="muted">—</span>
        ),
    },
    { key: 'name', header: 'Sponsor', cell: (sp) => <strong>{sp.name}</strong> },
    {
      key: 'tier',
      header: 'Tier',
      cell: (sp) => <StatusChip tone={sp.tier === 'TITLE' ? 'gold' : 'neutral'}>{tierLabel(sp.tier)}</StatusChip>,
    },
    {
      key: 'link',
      header: 'Link',
      cell: (sp) =>
        sp.websiteUrl ? (
          <span className="muted" style={{ fontSize: 13 }}>
            {sp.websiteUrl.replace(/^https?:\/\//, '')}
          </span>
        ) : (
          <span className="muted">—</span>
        ),
    },
    {
      key: 'active',
      header: 'Status',
      cell: (sp) => <StatusChip tone={sp.active ? 'ok' : 'neutral'}>{sp.active ? 'Active' : 'Hidden'}</StatusChip>,
    },
  ];

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Content</span>
          <h1 className="t-h1">Sponsors</h1>
          <span className="muted">Drives the title lockup, homepage sponsor wall and auction display ticker.</span>
        </div>
        {canEdit && (
          <Button variant="volt" onClick={() => setEditing('new')}>
            Add sponsor
          </Button>
        )}
      </div>
      <section className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}>
        <DataTable
          caption="Sponsors"
          columns={columns}
          rows={data}
          rowKey={(sp) => sp.id}
          loading={isLoading}
          error={error}
          onRetry={() => void refetch()}
          onRowClick={(sp) => setEditing(sp)}
          empty={{
            title: 'No sponsors yet',
            description: 'Until a sponsor is added, the public site shows a “Become a sponsor” card.',
            action: canEdit ? { label: 'Add sponsor', onClick: () => setEditing('new') } : undefined,
          }}
        />
        <span className="help">Sponsors are grouped by tier; the arrows set the order within a tier.</span>
      </section>
      <SponsorDrawer
        key={editing === 'new' ? 'new' : (editing?.id ?? 'none')}
        item={editing}
        all={data ?? []}
        canEdit={canEdit}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function SponsorDrawer({
  item,
  all,
  canEdit,
  onClose,
}: {
  item: Sponsor | 'new' | null;
  all: Sponsor[];
  canEdit: boolean;
  onClose: () => void;
}) {
  const toast = useToast();
  const save = useSaveSponsor();
  const existing = item && item !== 'new' ? item : null;
  const titleTaken = all.some((x) => x.tier === 'TITLE' && x.active && x.id !== existing?.id);
  const [v, setV] = useState({
    name: existing?.name ?? '',
    tier: existing?.tier ?? ((titleTaken ? 'PLATINUM' : 'TITLE') as SponsorTier),
    logoAssetId: existing?.logoAssetId ?? null,
    logoUrl: existing?.logoUrl ?? null,
    websiteUrl: existing?.websiteUrl ?? '',
    awardName: existing?.awardName ?? '',
    showOnWall: existing?.showOnWall ?? true,
    showOnTicker: existing?.showOnTicker ?? true,
    active: existing?.active ?? true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const set = <K extends keyof typeof v>(k: K, val: (typeof v)[K]) => setV((x) => ({ ...x, [k]: val }));

  const submit = async () => {
    setErrors({});
    try {
      await save.mutateAsync({
        id: existing?.id,
        version: existing?.version,
        name: v.name,
        tier: v.tier,
        logoAssetId: v.logoAssetId,
        websiteUrl: v.websiteUrl.trim() || null,
        awardName: v.awardName.trim() || null,
        showOnWall: v.showOnWall,
        showOnTicker: v.showOnTicker,
        active: v.active,
      });
      toast.success(existing ? 'Sponsor saved' : `${v.name} added`);
      onClose();
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(e.fieldErrors);
      else toast.error(errorMessage(e));
    }
  };

  return (
    <Drawer
      open={item !== null}
      onClose={onClose}
      title={existing ? existing.name : 'Add sponsor'}
      footer={
        canEdit ? (
          <>
            <Button variant="out" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="volt" loading={save.isPending} onClick={() => void submit()}>
              Save sponsor
            </Button>
          </>
        ) : undefined
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Field label="Sponsor name" required error={errors.name}>
          {(a) => <Input id={a.id} value={v.name} maxLength={120} disabled={!canEdit} onChange={(e) => set('name', e.target.value)} />}
        </Field>
        <Field label="Tier" required error={errors.tier}>
          {(a) => (
            <Select id={a.id} value={v.tier} disabled={!canEdit} onChange={(e) => set('tier', e.target.value as SponsorTier)}>
              {SPONSOR_TIERS.map((t) => (
                <option key={t.value} value={t.value} disabled={t.value === 'TITLE' && titleTaken && v.active}>
                  {t.label}
                  {t.value === 'TITLE' && titleTaken ? ' — already assigned (one only)' : ''}
                </option>
              ))}
            </Select>
          )}
        </Field>
        {canEdit ? (
          <ImageUploader
            purpose="SPONSOR_LOGO"
            label="Logo"
            currentUrl={v.logoUrl}
            onUploaded={(asset) => setV((x) => ({ ...x, logoAssetId: asset.id, logoUrl: asset.previewUrl }))}
            hint="Transparent PNG works best · min 200 × 100 px · shown on a light tile"
          />
        ) : null}
        <Field label="Website link" optional error={errors.websiteUrl}>
          {(a) => (
            <Input
              id={a.id}
              type="url"
              placeholder="https://"
              value={v.websiteUrl}
              disabled={!canEdit}
              onChange={(e) => set('websiteUrl', e.target.value)}
            />
          )}
        </Field>
        <Field label="Named award" optional help="e.g. Player of the Match presented by [Brand]">
          {(a) => (
            <Input id={a.id} value={v.awardName} maxLength={160} disabled={!canEdit} onChange={(e) => set('awardName', e.target.value)} />
          )}
        </Field>
        <div className="field">
          <span className="lbl">Show on</span>
          <Checkbox
            label="Homepage sponsor wall"
            checked={v.showOnWall}
            disabled={!canEdit}
            onChange={(e) => set('showOnWall', e.target.checked)}
          />
          <Checkbox
            label="Auction display ticker"
            checked={v.showOnTicker}
            disabled={!canEdit}
            onChange={(e) => set('showOnTicker', e.target.checked)}
          />
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 12,
            padding: 12,
            borderRadius: 6,
            background: 'var(--surface)',
          }}
        >
          <span style={{ display: 'flex', flexDirection: 'column' }}>
            <span className="lbl">Active</span>
            <span className="help">Inactive sponsors are hidden everywhere but kept on record.</span>
          </span>
          <Toggle
            label={<span className="sr-only">Active</span>}
            checked={v.active}
            disabled={!canEdit}
            onChange={(e) => set('active', e.target.checked)}
          />
        </div>
      </div>
    </Drawer>
  );
}
