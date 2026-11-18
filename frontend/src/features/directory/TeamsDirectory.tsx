'use client';

import { motion } from 'motion/react';
import { useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Textarea, Toggle } from '@/design-system/primitives/Inputs';
import { Drawer } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { Segmented } from '@/design-system/primitives/Tabs';
import { useToast } from '@/design-system/primitives/Toast';
import { CrestTile } from '@/design-system/sport/CrestTile';
import { ImageUploader } from '@/design-system/sport/ImageUploader';
import { teamColor } from '@/design-system/tokens';
import { useCreateTeam, useTeamFee, useTeamStatus, useTeams, useUpdateTeam, type Team } from '@/lib/api/directory';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { formatLkPhone } from '@/lib/api/registration';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime } from '@/lib/format/dates';
import { ColorPicker } from './ColorPicker';
import s from '../registrations/Registrations.module.css';

export function TeamsDirectory() {
  const { can } = useAuth();
  const canManage = can('DIRECTORY_MANAGE');
  const { data: teams, isLoading, error, refetch } = useTeams();
  const [view, setView] = useState<'grid' | 'table'>('grid');
  const [editing, setEditing] = useState<Team | 'new' | null>(null);

  const columns: Column<Team>[] = [
    {
      key: 'team',
      header: 'Team',
      cell: (t) => <CrestTile logoUrl={t.logoUrl} name={t.name} code={t.shortCode} colorSlot={t.colorSlot} size="s" showName />,
    },
    {
      key: 'source',
      header: 'Source',
      cell: (t) => <StatusChip tone="neutral">{t.creationSource === 'ADMIN' ? 'Admin-created' : 'Public'}</StatusChip>,
    },
    {
      key: 'contact',
      header: 'Contact (private)',
      cell: (t) => (t.contactName ? `${t.contactName} · ${formatLkPhone(t.contactPhone)}` : <span className="muted">—</span>),
    },
    {
      key: 'fee',
      header: 'Entry fee',
      cell: (t) => <StatusChip tone={t.feeReceived ? 'ok' : 'pending'}>{t.feeReceived ? 'Fee received' : 'Fee pending'}</StatusChip>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (t) => <StatusChip status={t.status}>{t.status === 'ACTIVE' ? 'Active' : 'Inactive'}</StatusChip>,
    },
  ];

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Directory</span>
          <h1 className="t-h1">Teams</h1>
          <span className="muted">Approved and admin-created teams{teams ? ` · ${teams.length} of 10 colour slots used` : ''}.</span>
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <Segmented
            label="View"
            value={view}
            onChange={setView}
            options={[
              { value: 'grid', label: 'Crest grid' },
              { value: 'table', label: 'Table' },
            ]}
          />
          {canManage && (
            <Button variant="volt" onClick={() => setEditing('new')}>
              Create team
            </Button>
          )}
        </div>
      </div>

      {isLoading ? (
        <div className={s.crestGrid}>
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} height={200} />
          ))}
        </div>
      ) : error ? (
        <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />
      ) : !teams?.length ? (
        <EmptyState
          title="No teams yet"
          description="Teams appear here when a team application is approved, or create one directly."
          action={
            canManage
              ? { label: 'Create team', onClick: () => setEditing('new') }
              : { label: 'Team applications', href: '/admin/registrations/teams' }
          }
        />
      ) : view === 'grid' ? (
        <div className={s.crestGrid}>
          {teams.map((t, i) => (
            <motion.button
              key={t.id}
              type="button"
              className={s.crestCard}
              style={{ ['--band' as string]: teamColor(t.colorSlot), opacity: t.status === 'ACTIVE' ? 1 : 0.55 }}
              onClick={() => setEditing(t)}
              initial={{ opacity: 0, y: 8 }}
              animate={{ opacity: t.status === 'ACTIVE' ? 1 : 0.55, y: 0 }}
              transition={{ delay: i * 0.03, duration: 0.24 }}
            >
              <CrestTile logoUrl={t.logoUrl} name={t.name} colorSlot={t.colorSlot} size="l" />
              <strong>{t.name}</strong>
              <span className="mono muted">{t.shortCode}</span>
              <span style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'center' }}>
                <StatusChip tone="neutral">{t.creationSource === 'ADMIN' ? 'Admin-created' : 'Public'}</StatusChip>
                <StatusChip tone={t.feeReceived ? 'ok' : 'pending'}>{t.feeReceived ? 'Fee received' : 'Fee pending'}</StatusChip>
                {t.status === 'INACTIVE' && <StatusChip status="INACTIVE">Inactive</StatusChip>}
              </span>
            </motion.button>
          ))}
        </div>
      ) : (
        <DataTable caption="Teams" columns={columns} rows={teams} rowKey={(t) => t.id} onRowClick={(t) => setEditing(t)} />
      )}

      <TeamDrawer
        key={editing === 'new' ? 'new' : (editing?.id ?? 'none')}
        team={editing}
        teams={teams ?? []}
        canManage={canManage}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

interface Values {
  name: string;
  shortCode: string;
  colorSlot: number | null;
  description: string;
  logoAssetId: string | null;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  feeReceived: boolean;
}

function TeamDrawer({
  team,
  teams,
  canManage,
  onClose,
}: {
  team: Team | 'new' | null;
  teams: Team[];
  canManage: boolean;
  onClose: () => void;
}) {
  const toast = useToast();
  const create = useCreateTeam();
  const update = useUpdateTeam();
  const fee = useTeamFee();
  const status = useTeamStatus();
  const existing = team && team !== 'new' ? team : null;
  const [deactivateReason, setDeactivateReason] = useState('');
  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    defaultValues: {
      name: existing?.name ?? '',
      shortCode: existing?.shortCode ?? '',
      colorSlot: existing?.colorSlot ?? null,
      description: existing?.description ?? '',
      logoAssetId: existing?.logoAssetId ?? null,
      contactName: existing?.contactName ?? '',
      contactEmail: existing?.contactEmail ?? '',
      contactPhone: existing?.contactPhone ?? '',
      feeReceived: existing?.feeReceived ?? false,
    },
  });
  const v = useWatch({ control });
  const name = (v.name ?? '').trim().toLowerCase();
  const clash = name.length >= 3 && teams.some((t) => t.id !== existing?.id && t.name.toLowerCase() === name);
  const used = new Set(teams.filter((t) => t.id !== existing?.id && t.colorSlot !== null).map((t) => t.colorSlot as number));
  const readOnly = !canManage;

  const onSubmit = handleSubmit(async (x) => {
    const body = {
      name: x.name.trim(),
      shortCode: x.shortCode.trim() || undefined,
      colorSlot: x.colorSlot,
      description: x.description.trim() || null,
      logoAssetId: x.logoAssetId,
      contactName: x.contactName.trim() || null,
      contactEmail: x.contactEmail.trim() || null,
      contactPhone: x.contactPhone.trim() || null,
    };
    try {
      if (existing) {
        const saved = await update.mutateAsync({
          id: existing.id,
          version: existing.version,
          ...body,
          shortCode: body.shortCode ?? existing.shortCode,
        });
        if (x.feeReceived !== saved.feeReceived) await fee.mutateAsync({ id: saved.id, received: x.feeReceived, version: saved.version });
        toast.success('Team saved');
      } else {
        const saved = await create.mutateAsync(body);
        if (x.feeReceived) await fee.mutateAsync({ id: saved.id, received: true, version: saved.version });
        toast.success(`${saved.name} created`);
      }
      onClose();
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'TEAM_NAME_TAKEN') return setError('name', { message: e.message });
        if (e.code === 'SHORT_CODE_TAKEN' || e.code === 'SHORT_CODE_INVALID') return setError('shortCode', { message: e.message });
        if (Object.keys(e.fieldErrors).length) {
          Object.entries(e.fieldErrors).forEach(([f, m]) => setError(f as keyof Values, { message: m }));
          return;
        }
      }
      toast.error(errorMessage(e));
    }
  });

  return (
    <Drawer
      open={team !== null}
      onClose={onClose}
      title={existing ? existing.name : 'Create team'}
      footer={
        canManage ? (
          <>
            <Button variant="out" onClick={onClose}>
              Cancel
            </Button>
            <Button variant="volt" loading={isSubmitting} disabled={clash} onClick={() => void onSubmit()}>
              {existing ? 'Save team' : 'Create team'}
            </Button>
          </>
        ) : undefined
      }
    >
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void onSubmit();
        }}
        style={{ display: 'flex', flexDirection: 'column', gap: 18 }}
      >
        {!existing ? (
          <span className="help">
            Saved as <StatusChip tone="neutral">Admin-created</StatusChip> — no public application needed.
          </span>
        ) : (
          <span className="help">
            {existing.creationSource === 'ADMIN' ? 'Admin-created team.' : 'Created from a public application.'}
            {existing.feeReceivedAt ? ` Fee marked received ${formatDateTime(existing.feeReceivedAt)}.` : ''}
          </span>
        )}
        <Field
          label="Team name"
          required
          error={errors.name?.message ?? (clash ? 'A team with this name already exists.' : undefined)}
          success={!clash && name.length >= 3 && !errors.name ? 'Name available' : undefined}
        >
          {(a) => (
            <Input
              id={a.id}
              maxLength={40}
              disabled={readOnly}
              {...register('name', {
                validate: (x) => (x.trim().length >= 3 && x.trim().length <= 40) || 'Team names are 3–40 characters.',
              })}
            />
          )}
        </Field>
        <Field
          label="Short code"
          optional={!existing}
          error={errors.shortCode?.message}
          help={existing ? '2–3 letters or digits.' : 'Leave empty to generate one from the name.'}
        >
          {(a) => (
            <Input
              id={a.id}
              maxLength={3}
              disabled={readOnly}
              style={{ maxWidth: 120, textTransform: 'uppercase' }}
              {...register('shortCode', { validate: (x) => !x || /^[A-Za-z0-9]{2,3}$/.test(x) || '2–3 letters or digits.' })}
            />
          )}
        </Field>
        <div className="field">
          <span className="lbl">Team colour</span>
          <ColorPicker
            value={v.colorSlot ?? null}
            onChange={(c) => setValue('colorSlot', c, { shouldDirty: true })}
            used={used}
            allowAuto={!existing}
          />
        </div>
        {canManage ? (
          <ImageUploader
            purpose="TEAM_LOGO"
            label="Team logo"
            currentUrl={existing?.logoUrl}
            onUploaded={(asset) => setValue('logoAssetId', asset.id, { shouldDirty: true })}
            hint="1 : 1 square · verified and published when you save"
          />
        ) : (
          <CrestTile logoUrl={existing?.logoUrl} name={existing?.name ?? 'Team'} colorSlot={existing?.colorSlot} size="l" />
        )}
        <Field label="Short description" optional>
          {(a) => <Textarea id={a.id} rows={2} maxLength={300} disabled={readOnly} {...register('description')} />}
        </Field>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <span className="eyebrow">Contact person · private</span>
          <Field label="Name">{(a) => <Input id={a.id} maxLength={120} disabled={readOnly} {...register('contactName')} />}</Field>
          <Field label="Email" error={errors.contactEmail?.message}>
            {(a) => <Input id={a.id} type="email" disabled={readOnly} {...register('contactEmail')} />}
          </Field>
          <Field label="Phone" help="With country code, e.g. +94771234567">
            {(a) => <Input id={a.id} type="tel" disabled={readOnly} {...register('contactPhone')} />}
          </Field>
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
            <span className="lbl">Entry fee received</span>
            <span className="help">Records the date and who marked it</span>
          </span>
          <Toggle label={<span className="sr-only">Fee received</span>} disabled={readOnly} {...register('feeReceived')} />
        </div>
        {existing && canManage && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, borderTop: '1px solid var(--line)', paddingTop: 16 }}>
            <span className="eyebrow">Status</span>
            {existing.status === 'ACTIVE' ? (
              <>
                <Field label="Reason for deactivating" help="Inactive teams are hidden from the public site. Nothing is deleted.">
                  {(a) => (
                    <Input id={a.id} value={deactivateReason} maxLength={300} onChange={(e) => setDeactivateReason(e.target.value)} />
                  )}
                </Field>
                <div>
                  <Button
                    variant="danger"
                    size="sm"
                    loading={status.isPending}
                    disabled={!deactivateReason.trim()}
                    onClick={() =>
                      status
                        .mutateAsync({ id: existing.id, status: 'INACTIVE', reason: deactivateReason.trim(), version: existing.version })
                        .then(
                          () => (toast.success('Team deactivated'), onClose()),
                          (e) => toast.error(errorMessage(e)),
                        )
                    }
                  >
                    Deactivate team
                  </Button>
                </div>
              </>
            ) : (
              <div>
                <Button
                  variant="out"
                  size="sm"
                  loading={status.isPending}
                  onClick={() =>
                    status.mutateAsync({ id: existing.id, status: 'ACTIVE', version: existing.version }).then(
                      () => (toast.success('Team reactivated'), onClose()),
                      (e) => toast.error(errorMessage(e)),
                    )
                  }
                >
                  Reactivate team
                </Button>
              </div>
            )}
          </div>
        )}
      </form>
    </Drawer>
  );
}
