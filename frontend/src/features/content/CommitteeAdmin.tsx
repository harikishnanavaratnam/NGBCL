'use client';

import { motion } from 'motion/react';
import { useState } from 'react';
import { EmptyState, ErrorState, Skeleton } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Toggle } from '@/design-system/primitives/Inputs';
import { ConfirmDialog, Drawer, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { ImageUploader } from '@/design-system/sport/ImageUploader';
import s from '@/features/registrations/Registrations.module.css';
import {
  useCommittee,
  useDeleteGroup,
  useDeleteMember,
  useReorderGroups,
  useReorderMembers,
  useSaveGroup,
  useSaveMember,
  type CommitteeGroup,
  type CommitteeMember,
  type CommitteeStyle,
} from '@/lib/api/content';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { initials } from '@/lib/format/names';

function swap<T>(list: T[], i: number, j: number) {
  const out = [...list];
  [out[i], out[j]] = [out[j], out[i]];
  return out;
}

export function CommitteeAdmin() {
  const { can } = useAuth();
  const canEdit = can('CONTENT_MANAGE');
  const toast = useToast();
  const { data, isLoading, error, refetch } = useCommittee();
  const reorderGroups = useReorderGroups();
  const reorderMembers = useReorderMembers();
  const toggleMember = useSaveMember();
  const [groupEdit, setGroupEdit] = useState<CommitteeGroup | 'new' | null>(null);
  const [memberEdit, setMemberEdit] = useState<{ member: CommitteeMember | null; groupId: string } | null>(null);

  const fail = (e: unknown) => toast.error(errorMessage(e));

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Content</span>
          <h1 className="t-h1">Organising committee</h1>
          <span className="muted">Feeds the public Committee page. No personal contact details are stored here.</span>
        </div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <Button variant="out" href="/committee" target="_blank">
            View public page
          </Button>
          {canEdit && (
            <Button
              variant="volt"
              onClick={() => (data?.length ? setMemberEdit({ member: null, groupId: data[0].id }) : setGroupEdit('new'))}
            >
              {data?.length ? 'Add member' : 'Add group'}
            </Button>
          )}
        </div>
      </div>

      {isLoading && <Skeleton height={240} />}
      {error && <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />}
      {data && data.length === 0 && (
        <EmptyState
          title="No committee groups yet"
          description="Start with a group such as “Leadership” (large cards) and add members to it."
          action={canEdit ? { label: 'Add group', onClick: () => setGroupEdit('new') } : undefined}
        />
      )}

      {data?.map((g, gi) => (
        <motion.section
          key={g.id}
          layout
          className="card"
          style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 12 }}
          transition={{ duration: 0.2 }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            {canEdit && (
              <span style={{ display: 'inline-flex', gap: 2 }}>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={`Move ${g.name} up`}
                  disabled={gi === 0 || reorderGroups.isPending}
                  onClick={() => reorderGroups.mutateAsync(swap(data, gi, gi - 1).map((x) => x.id)).catch(fail)}
                >
                  ↑
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  aria-label={`Move ${g.name} down`}
                  disabled={gi === data.length - 1 || reorderGroups.isPending}
                  onClick={() => reorderGroups.mutateAsync(swap(data, gi, gi + 1).map((x) => x.id)).catch(fail)}
                >
                  ↓
                </Button>
              </span>
            )}
            <strong style={{ fontSize: 16 }}>{g.name}</strong>
            <StatusChip tone="neutral">{g.style === 'LEAD' ? 'Large cards' : 'Compact cards'}</StatusChip>
            {canEdit && (
              <span style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
                <Button size="sm" variant="out" onClick={() => setGroupEdit(g)}>
                  Rename
                </Button>
                <Button size="sm" variant="out" onClick={() => setMemberEdit({ member: null, groupId: g.id })}>
                  Add member
                </Button>
              </span>
            )}
          </div>
          {g.members.length === 0 ? (
            <span className="muted" style={{ fontSize: 14 }}>
              No members yet.
            </span>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {g.members.map((m, mi) => (
                <div
                  key={m.id}
                  className="panel-flat"
                  style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '8px 10px', opacity: m.visible ? 1 : 0.6 }}
                >
                  {canEdit && (
                    <span style={{ display: 'inline-flex', gap: 2 }}>
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Move ${m.fullName} up`}
                        disabled={mi === 0 || reorderMembers.isPending}
                        onClick={() =>
                          reorderMembers.mutateAsync({ groupId: g.id, ids: swap(g.members, mi, mi - 1).map((x) => x.id) }).catch(fail)
                        }
                      >
                        ↑
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        aria-label={`Move ${m.fullName} down`}
                        disabled={mi === g.members.length - 1 || reorderMembers.isPending}
                        onClick={() =>
                          reorderMembers.mutateAsync({ groupId: g.id, ids: swap(g.members, mi, mi + 1).map((x) => x.id) }).catch(fail)
                        }
                      >
                        ↓
                      </Button>
                    </span>
                  )}
                  {m.photoUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img src={m.photoUrl} alt="" style={{ width: 36, aspectRatio: '4 / 5', objectFit: 'cover', borderRadius: 3 }} />
                  ) : (
                    <span
                      style={{
                        width: 36,
                        aspectRatio: '4 / 5',
                        borderRadius: 3,
                        background: '#1E2B45',
                        display: 'grid',
                        placeItems: 'center',
                        fontSize: 12,
                        fontWeight: 700,
                        color: 'var(--text-2)',
                      }}
                    >
                      {initials(m.fullName)}
                    </span>
                  )}
                  <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0, flex: 1 }}>
                    <strong style={{ fontSize: 14 }}>{m.fullName}</strong>
                    <span className="muted" style={{ fontSize: 13 }}>
                      {m.role}
                    </span>
                  </span>
                  {canEdit ? (
                    <>
                      <Toggle
                        label={
                          <span className="muted" style={{ fontSize: 12 }}>
                            On site
                          </span>
                        }
                        checked={m.visible}
                        disabled={toggleMember.isPending}
                        onChange={(e) =>
                          toggleMember
                            .mutateAsync({
                              id: m.id,
                              version: m.version,
                              groupId: m.groupId,
                              fullName: m.fullName,
                              role: m.role,
                              bio: m.bio,
                              photoAssetId: m.photoAssetId,
                              visible: e.target.checked,
                            })
                            .catch(fail)
                        }
                      />
                      <Button size="sm" variant="out" onClick={() => setMemberEdit({ member: m, groupId: g.id })}>
                        Edit
                      </Button>
                    </>
                  ) : (
                    !m.visible && <StatusChip tone="neutral">Hidden</StatusChip>
                  )}
                </div>
              ))}
            </div>
          )}
        </motion.section>
      ))}

      {canEdit && data && data.length > 0 && (
        <div>
          <Button variant="out" onClick={() => setGroupEdit('new')}>
            Add group
          </Button>
        </div>
      )}

      <GroupModal key={groupEdit === 'new' ? 'new' : (groupEdit?.id ?? 'none')} group={groupEdit} onClose={() => setGroupEdit(null)} />
      <MemberDrawer
        key={memberEdit ? (memberEdit.member?.id ?? `new-${memberEdit.groupId}`) : 'none'}
        state={memberEdit}
        groups={data ?? []}
        onClose={() => setMemberEdit(null)}
      />
    </div>
  );
}

function GroupModal({ group, onClose }: { group: CommitteeGroup | 'new' | null; onClose: () => void }) {
  const toast = useToast();
  const save = useSaveGroup();
  const del = useDeleteGroup();
  const existing = group && group !== 'new' ? group : null;
  const [name, setName] = useState(existing?.name ?? '');
  const [style, setStyle] = useState<CommitteeStyle>(existing?.style ?? 'COMPACT');
  const [confirm, setConfirm] = useState(false);
  return (
    <Modal
      open={group !== null}
      onClose={onClose}
      title={existing ? 'Edit group' : 'Add group'}
      footer={
        <>
          {existing && (
            <Button variant="ghost" onClick={() => setConfirm(true)} style={{ marginRight: 'auto' }}>
              Delete group
            </Button>
          )}
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="volt"
            loading={save.isPending}
            disabled={!name.trim()}
            onClick={() =>
              save.mutateAsync({ id: existing?.id, name, style, version: existing?.version }).then(
                () => (toast.success('Group saved'), onClose()),
                (e) => toast.error(errorMessage(e)),
              )
            }
          >
            Save group
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Field label="Group name" required help="e.g. Leadership, Tournament operations, Media">
          {(a) => <Input id={a.id} value={name} maxLength={80} onChange={(e) => setName(e.target.value)} autoFocus />}
        </Field>
        <Field label="Card style">
          {(a) => (
            <Select id={a.id} value={style} onChange={(e) => setStyle(e.target.value as CommitteeStyle)}>
              <option value="LEAD">Large cards with portrait and bio (leadership)</option>
              <option value="COMPACT">Compact cards</option>
            </Select>
          )}
        </Field>
      </div>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title={`Delete ${existing?.name ?? 'group'}?`}
        consequences="Only empty groups can be deleted. Move or remove its members first."
        confirmLabel="Delete group"
        tone="danger"
        onConfirm={async () => {
          if (!existing) return;
          try {
            await del.mutateAsync(existing.id);
            toast.success('Group deleted');
            onClose();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </Modal>
  );
}

function MemberDrawer({
  state,
  groups,
  onClose,
}: {
  state: { member: CommitteeMember | null; groupId: string } | null;
  groups: CommitteeGroup[];
  onClose: () => void;
}) {
  const toast = useToast();
  const save = useSaveMember();
  const del = useDeleteMember();
  const m = state?.member ?? null;
  const [v, setV] = useState({
    groupId: m?.groupId ?? state?.groupId ?? '',
    fullName: m?.fullName ?? '',
    role: m?.role ?? '',
    bio: m?.bio ?? '',
    photoAssetId: m?.photoAssetId ?? null,
    photoUrl: m?.photoUrl ?? null,
    visible: m?.visible ?? true,
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [confirm, setConfirm] = useState(false);
  const isLead = groups.find((g) => g.id === v.groupId)?.style === 'LEAD';

  const submit = async () => {
    setErrors({});
    try {
      await save.mutateAsync({
        id: m?.id,
        version: m?.version,
        groupId: v.groupId,
        fullName: v.fullName,
        role: v.role,
        bio: v.bio.trim() || null,
        photoAssetId: v.photoAssetId,
        visible: v.visible,
      });
      toast.success(m ? 'Member saved' : `${v.fullName} added`);
      onClose();
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(e.fieldErrors);
      else toast.error(errorMessage(e));
    }
  };

  return (
    <Drawer
      open={state !== null}
      onClose={onClose}
      title={m ? m.fullName : 'Add committee member'}
      footer={
        <>
          {m && (
            <Button variant="ghost" onClick={() => setConfirm(true)} style={{ marginRight: 'auto' }}>
              Remove
            </Button>
          )}
          <Button variant="out" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="volt" loading={save.isPending} onClick={() => void submit()}>
            Save member
          </Button>
        </>
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Field label="Full name" required error={errors.fullName}>
          {(a) => <Input id={a.id} value={v.fullName} maxLength={120} onChange={(e) => setV({ ...v, fullName: e.target.value })} />}
        </Field>
        <Field label="Role or title" required error={errors.role} help="As it should appear publicly">
          {(a) => <Input id={a.id} value={v.role} maxLength={120} onChange={(e) => setV({ ...v, role: e.target.value })} />}
        </Field>
        <Field label="Group">
          {(a) => (
            <Select id={a.id} value={v.groupId} onChange={(e) => setV({ ...v, groupId: e.target.value })}>
              {groups.map((g) => (
                <option key={g.id} value={g.id}>
                  {g.name}
                </option>
              ))}
            </Select>
          )}
        </Field>
        <ImageUploader
          purpose="COMMITTEE_PHOTO"
          label="Photo (optional)"
          currentUrl={v.photoUrl}
          onUploaded={(asset) => setV((x) => ({ ...x, photoAssetId: asset.id, photoUrl: asset.previewUrl }))}
          hint="4 : 5 · without a photo, initials are shown"
        />
        <Field
          label="One-line bio"
          optional
          error={errors.bio}
          help={isLead ? 'Shown on large leadership cards.' : 'Only shown on large (leadership) cards.'}
        >
          {(a) => <Input id={a.id} value={v.bio} maxLength={200} onChange={(e) => setV({ ...v, bio: e.target.value })} />}
        </Field>
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
          <span className="lbl">Show on public page</span>
          <Toggle
            label={<span className="sr-only">Show publicly</span>}
            checked={v.visible}
            onChange={(e) => setV({ ...v, visible: e.target.checked })}
          />
        </div>
      </div>
      <ConfirmDialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title={`Remove ${m?.fullName ?? 'member'}?`}
        consequences="They disappear from the public Committee page. To keep them on record but hidden, switch off “Show on public page” instead."
        confirmLabel="Remove member"
        tone="danger"
        onConfirm={async () => {
          if (!m) return;
          try {
            await del.mutateAsync(m.id);
            toast.success('Member removed');
            onClose();
          } catch (e) {
            toast.error(errorMessage(e));
          }
        }}
      />
    </Drawer>
  );
}
