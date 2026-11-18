'use client';

import { useMemo, useRef, useState } from 'react';
import { DataTable, type Column } from '@/design-system/data/DataTable';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Textarea, Toggle } from '@/design-system/primitives/Inputs';
import { Drawer, Modal } from '@/design-system/primitives/Overlay';
import { StatusChip, type ChipTone } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { ImageUploader } from '@/design-system/sport/ImageUploader';
import { Markdown } from '@/features/public/Markdown';
import { useDebounced } from '@/features/registrations/shared';
import s from '@/features/registrations/Registrations.module.css';
import { useAnnouncements, useSaveAnnouncement, type Announcement, type AnnouncementStatus } from '@/lib/api/content';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { formatDateTime, fromZonedInput, toZonedInput } from '@/lib/format/dates';

const STATUS: Record<Announcement['displayStatus'], { label: string; tone: ChipTone }> = {
  PUBLISHED: { label: 'Published', tone: 'ok' },
  SCHEDULED: { label: 'Scheduled', tone: 'provisional' },
  DRAFT: { label: 'Draft', tone: 'neutral' },
  ARCHIVED: { label: 'Archived', tone: 'neutral' },
};

export function AnnouncementsAdmin() {
  const { can } = useAuth();
  const canEdit = can('CONTENT_MANAGE');
  const toast = useToast();
  const { data, isLoading, error, refetch } = useAnnouncements();
  const save = useSaveAnnouncement();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('');
  const [editing, setEditing] = useState<Announcement | 'new' | null>(null);
  const query = useDebounced(q, 150).trim().toLowerCase();

  const rows = useMemo(
    () => (data ?? []).filter((a) => (!status || a.displayStatus === status) && (!query || a.title.toLowerCase().includes(query))),
    [data, status, query],
  );

  const columns: Column<Announcement>[] = [
    {
      key: 'title',
      header: 'Title',
      cell: (a) => <strong style={{ color: a.status === 'ARCHIVED' ? 'var(--text-2)' : undefined }}>{a.title}</strong>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (a) => <StatusChip tone={STATUS[a.displayStatus].tone}>{STATUS[a.displayStatus].label}</StatusChip>,
    },
    {
      key: 'pinned',
      header: 'Pinned',
      cell: (a) => (
        <span onClick={(e) => e.stopPropagation()}>
          <Toggle
            label={<span className="sr-only">Pinned</span>}
            checked={a.pinned}
            disabled={!canEdit || save.isPending}
            onChange={(e) =>
              save
                .mutateAsync({
                  id: a.id,
                  version: a.version,
                  title: a.title,
                  body: a.body,
                  coverAssetId: a.coverAssetId,
                  status: a.status,
                  publishAt: a.publishAt,
                  pinned: e.target.checked,
                })
                .then(
                  () => toast.success(e.target.checked ? 'Pinned to the top' : 'Unpinned'),
                  (err) => toast.error(errorMessage(err)),
                )
            }
          />
        </span>
      ),
    },
    {
      key: 'when',
      header: 'Publish time',
      cell: (a) => (a.publishAt ? <span className="mono muted">{formatDateTime(a.publishAt)}</span> : <span className="muted">—</span>),
    },
    { key: 'author', header: 'Author', cell: (a) => a.authorName ?? '—' },
  ];

  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titleRow}>
          <span className="eyebrow">Content</span>
          <h1 className="t-h1">Announcements</h1>
          <span className="muted">Published items appear on the homepage and the News page.</span>
        </div>
        {canEdit && (
          <Button variant="volt" onClick={() => setEditing('new')}>
            New announcement
          </Button>
        )}
      </div>
      <section className="card" style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 14 }}>
        <div className={s.filters}>
          <Input
            type="search"
            placeholder="Search titles"
            aria-label="Search announcements"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
          <Select aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="">All statuses</option>
            <option value="PUBLISHED">Published</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="DRAFT">Draft</option>
            <option value="ARCHIVED">Archived</option>
          </Select>
        </div>
        <DataTable
          caption="Announcements"
          columns={columns}
          rows={data ? rows : undefined}
          rowKey={(a) => a.id}
          loading={isLoading}
          error={error}
          onRetry={() => void refetch()}
          onRowClick={(a) => setEditing(a)}
          empty={{
            title: data?.length ? 'Nothing matches' : 'No announcements yet',
            description: data?.length
              ? 'Try another search or status.'
              : 'Post news for players, teams and fans — it shows on the homepage.',
            action: canEdit && !data?.length ? { label: 'New announcement', onClick: () => setEditing('new') } : undefined,
          }}
        />
      </section>
      <AnnouncementDrawer
        key={editing === 'new' ? 'new' : (editing?.id ?? 'none')}
        item={editing}
        canEdit={canEdit}
        onClose={() => setEditing(null)}
      />
    </div>
  );
}

function AnnouncementDrawer({ item, canEdit, onClose }: { item: Announcement | 'new' | null; canEdit: boolean; onClose: () => void }) {
  const toast = useToast();
  const save = useSaveAnnouncement();
  const existing = item && item !== 'new' ? item : null;
  const [title, setTitle] = useState(existing?.title ?? '');
  const [body, setBody] = useState(existing?.body ?? '');
  const [cover, setCover] = useState<{ id: string | null; url: string | null }>({
    id: existing?.coverAssetId ?? null,
    url: existing?.coverUrl ?? null,
  });
  const [pinned, setPinned] = useState(existing?.pinned ?? false);
  const [when, setWhen] = useState<'now' | 'schedule'>(existing?.displayStatus === 'SCHEDULED' ? 'schedule' : 'now');
  const [at, setAt] = useState(existing?.publishAt && existing.displayStatus === 'SCHEDULED' ? toZonedInput(existing.publishAt) : '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [preview, setPreview] = useState(false);
  const bodyRef = useRef<HTMLTextAreaElement>(null);

  const wrap = (before: string, after = before, placeholder = 'text') => {
    const el = bodyRef.current;
    if (!el) return;
    const { selectionStart: a, selectionEnd: b } = el;
    const sel = body.slice(a, b) || placeholder;
    const next = body.slice(0, a) + before + sel + after + body.slice(b);
    setBody(next);
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(a + before.length, a + before.length + sel.length);
    });
  };
  const listify = () => {
    const el = bodyRef.current;
    if (!el) return;
    const { selectionStart: a, selectionEnd: b } = el;
    const start = body.lastIndexOf('\n', a - 1) + 1;
    const chunk = body.slice(start, b) || 'Item';
    const next =
      body.slice(0, start) +
      chunk
        .split('\n')
        .map((l) => (l.startsWith('- ') ? l : `- ${l}`))
        .join('\n') +
      body.slice(b);
    setBody(next);
  };

  const submit = async (status: AnnouncementStatus) => {
    setErrors({});
    let publishAt: string | null = null;
    if (status === 'PUBLISHED') {
      if (when === 'schedule') {
        publishAt = at ? fromZonedInput(at) : null;
        if (!publishAt) return setErrors({ publishAt: 'Choose when to publish.' });
      } else {
        publishAt =
          existing?.status === 'PUBLISHED' && existing.displayStatus !== 'SCHEDULED' ? existing.publishAt : new Date().toISOString();
      }
    } else if (status === 'ARCHIVED') {
      publishAt = existing?.publishAt ?? null;
    }
    try {
      await save.mutateAsync({
        id: existing?.id,
        version: existing?.version,
        title,
        body,
        coverAssetId: cover.id,
        pinned,
        status,
        publishAt,
      });
      toast.success(
        status === 'DRAFT' ? 'Draft saved' : status === 'ARCHIVED' ? 'Archived' : when === 'schedule' ? 'Scheduled' : 'Published',
      );
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
      size={720}
      title={existing ? 'Edit announcement' : 'New announcement'}
      footer={
        canEdit ? (
          <div className={s.footer}>
            {existing && existing.status !== 'ARCHIVED' && (
              <Button variant="ghost" onClick={() => void submit('ARCHIVED')} disabled={save.isPending} className={s.hint}>
                Archive
              </Button>
            )}
            <Button variant="out" onClick={() => setPreview(true)} disabled={!body.trim()}>
              Preview
            </Button>
            <Button variant="out" onClick={() => void submit('DRAFT')} loading={save.isPending}>
              {existing?.status === 'PUBLISHED' ? 'Unpublish (draft)' : 'Save draft'}
            </Button>
            <Button variant="volt" onClick={() => void submit('PUBLISHED')} loading={save.isPending}>
              {when === 'schedule' ? 'Schedule' : existing?.status === 'PUBLISHED' ? 'Update' : 'Publish'}
            </Button>
          </div>
        ) : undefined
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Field label="Title" required error={errors.title}>
          {(a) => <Input id={a.id} value={title} maxLength={160} disabled={!canEdit} onChange={(e) => setTitle(e.target.value)} />}
        </Field>
        <Field
          label="Body"
          required
          error={errors.body}
          help="**bold**, *italic*, [link](https://…), “- ” for lists. Blank line = new paragraph."
        >
          {(a) => (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {canEdit && (
                <div style={{ display: 'flex', gap: 6 }} role="toolbar" aria-label="Formatting">
                  <Button size="sm" variant="out" onClick={() => wrap('**')} aria-label="Bold">
                    <strong>B</strong>
                  </Button>
                  <Button size="sm" variant="out" onClick={() => wrap('*')} aria-label="Italic">
                    <em>I</em>
                  </Button>
                  <Button size="sm" variant="out" onClick={() => wrap('[', '](https://)', 'link text')}>
                    Link
                  </Button>
                  <Button size="sm" variant="out" onClick={listify}>
                    List
                  </Button>
                </div>
              )}
              <Textarea
                id={a.id}
                ref={bodyRef}
                rows={10}
                value={body}
                maxLength={20000}
                disabled={!canEdit}
                onChange={(e) => setBody(e.target.value)}
              />
            </div>
          )}
        </Field>
        {canEdit ? (
          <ImageUploader
            purpose="NEWS_COVER"
            label="Cover image (optional)"
            currentUrl={cover.url}
            onUploaded={(asset) => setCover({ id: asset.id, url: asset.previewUrl })}
            hint="16 : 9 · min 960 × 540 px · published with the announcement"
          />
        ) : (
          // eslint-disable-next-line @next/next/no-img-element
          cover.url && <img src={cover.url} alt="" style={{ width: '100%', borderRadius: 6 }} />
        )}
        {cover.id && canEdit && (
          <div>
            <Button size="sm" variant="ghost" onClick={() => setCover({ id: null, url: null })}>
              Remove cover
            </Button>
          </div>
        )}
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
          <span className="lbl">Pin to top of homepage</span>
          <Toggle
            label={<span className="sr-only">Pin</span>}
            checked={pinned}
            disabled={!canEdit}
            onChange={(e) => setPinned(e.target.checked)}
          />
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
          <Field label="Publish">
            {(a) => (
              <Select id={a.id} value={when} disabled={!canEdit} onChange={(e) => setWhen(e.target.value as 'now' | 'schedule')}>
                <option value="now">Now</option>
                <option value="schedule">Schedule for…</option>
              </Select>
            )}
          </Field>
          {when === 'schedule' && (
            <Field label="Date and time (Sri Lanka)" error={errors.publishAt}>
              {(a) => <Input id={a.id} type="datetime-local" value={at} disabled={!canEdit} onChange={(e) => setAt(e.target.value)} />}
            </Field>
          )}
        </div>
        {existing && (
          <span className="help">
            Link: /news/{existing.slug}
            {existing.publishAt
              ? ` · ${existing.displayStatus === 'SCHEDULED' ? 'goes live' : 'published'} ${formatDateTime(existing.publishAt)}`
              : ''}
          </span>
        )}
      </div>
      <Modal open={preview} onClose={() => setPreview(false)} title={title || 'Preview'} width={720}>
        {cover.url && (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={cover.url}
            alt=""
            style={{ width: '100%', aspectRatio: '16 / 9', objectFit: 'cover', borderRadius: 6, marginBottom: 16 }}
          />
        )}
        <Markdown text={body} />
      </Modal>
    </Drawer>
  );
}
