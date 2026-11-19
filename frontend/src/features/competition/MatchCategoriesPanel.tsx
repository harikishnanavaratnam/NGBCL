'use client';

import { useState } from 'react';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Select, Toggle } from '@/design-system/primitives/Inputs';
import { Modal } from '@/design-system/primitives/Overlay';
import { StatusChip } from '@/design-system/primitives/StatusChip';
import { useToast } from '@/design-system/primitives/Toast';
import { GENDER_LABEL, useCompetitionSetup, useSetupCommands, type GenderRule, type MatchCategory } from '@/lib/api/competition';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { useAuth } from '@/lib/auth/session';
import { PanelError, PanelLoading } from '../settings/PanelStates';
import styles from '../settings/Settings.module.css';

type Draft = { id?: string; code: string; name: string; playersPerSide: 1 | 2; genderRule: GenderRule; active: boolean; version?: number };

/** Settings › Match categories (export AdminSettings tab 6): categories + the matches in every tie. */
export function MatchCategoriesPanel() {
  const { data, isLoading, error, refetch } = useCompetitionSetup();
  const { can } = useAuth();
  const canEdit = can('FIXTURE_MANAGE', 'SETTINGS_MANAGE');
  const cmd = useSetupCommands();
  const toast = useToast();
  const [draft, setDraft] = useState<Draft | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [template, setTemplate] = useState<string[] | null>(null);

  if (isLoading) return <PanelLoading />;
  if (error || !data) return <PanelError error={error} onRetry={() => void refetch()} />;

  const byId = new Map(data.categories.map((c) => [c.id, c]));
  const current = template ?? data.template.map((t) => t.categoryId);
  const dirty = template !== null && template.join() !== data.template.map((t) => t.categoryId).join();
  const active = data.categories.filter((c) => c.active);

  const move = (i: number, dir: -1 | 1) => {
    const next = [...current];
    const j = i + dir;
    if (j < 0 || j >= next.length) return;
    [next[i], next[j]] = [next[j], next[i]];
    setTemplate(next);
  };

  const saveCategory = () => {
    if (!draft) return;
    setErrors({});
    cmd.category.mutate(draft, {
      onSuccess: () => {
        toast.success(draft.id ? 'Category saved' : 'Category added');
        setDraft(null);
      },
      onError: (e) => {
        if (e instanceof ApiError && Object.keys(e.fieldErrors).length) setErrors(e.fieldErrors);
        else toast.error(errorMessage(e));
      },
    });
  };

  return (
    <div className={styles.panel}>
      <section className={styles.section} aria-labelledby="mc-title">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <h2 id="mc-title" className="t-h2">
            Match categories
          </h2>
          {canEdit && (
            <Button variant="volt" onClick={() => setDraft({ code: '', name: '', playersPerSide: 1, genderRule: 'ANY', active: true })}>
              Add category
            </Button>
          )}
        </div>
        <div className="scroll" style={{ overflowX: 'auto' }}>
          <table className="tbl" style={{ minWidth: 620 }}>
            <thead>
              <tr>
                <th>Code</th>
                <th>Name</th>
                <th>Type</th>
                <th>Players per side</th>
                <th>Player eligibility</th>
                <th>Status</th>
                {canEdit && <th />}
              </tr>
            </thead>
            <tbody>
              {data.categories.map((c) => (
                <tr key={c.id}>
                  <td className="mono">{c.code}</td>
                  <td>{c.name}</td>
                  <td>{c.playersPerSide === 1 ? 'Singles' : 'Doubles'}</td>
                  <td className="mono">{c.playersPerSide}</td>
                  <td className="muted">{GENDER_LABEL[c.genderRule]}</td>
                  <td>{c.active ? <StatusChip tone="ok">Active</StatusChip> : <StatusChip tone="neutral">Inactive</StatusChip>}</td>
                  {canEdit && (
                    <td className="r">
                      <Button size="sm" variant="ghost" onClick={() => setDraft(toDraft(c))}>
                        Edit
                      </Button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <hr className="court" />

      <section className={styles.section} aria-labelledby="tpl-title">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <h2 id="tpl-title" className="t-h3">
            Fixture template — matches in every tie, in order
          </h2>
          <span className="help">{current.length} matches per tie</span>
        </div>
        <ol style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 6 }}>
          {current.map((id, i) => (
            <li key={`${id}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 12px', border: '1px solid var(--line)', borderRadius: 6, background: 'var(--bg-2)' }}>
              <span className="mono muted" style={{ width: 20 }}>
                {i + 1}
              </span>
              <Select
                aria-label={`Match ${i + 1} category`}
                value={id}
                disabled={!canEdit}
                style={{ maxWidth: 280 }}
                onChange={(e) => setTemplate(current.map((x, k) => (k === i ? e.target.value : x)))}
              >
                {[...active, ...(byId.get(id)?.active ? [] : [byId.get(id)].filter(Boolean))].map((c) => (
                  <option key={c!.id} value={c!.id}>
                    {c!.code} · {c!.name}
                  </option>
                ))}
              </Select>
              {canEdit && (
                <span style={{ marginLeft: 'auto', display: 'flex', gap: 4 }}>
                  <Button size="sm" variant="ghost" aria-label={`Move match ${i + 1} up`} disabled={i === 0} onClick={() => move(i, -1)}>
                    ↑
                  </Button>
                  <Button size="sm" variant="ghost" aria-label={`Move match ${i + 1} down`} disabled={i === current.length - 1} onClick={() => move(i, 1)}>
                    ↓
                  </Button>
                  <Button size="sm" variant="ghost" aria-label={`Remove match ${i + 1}`} disabled={current.length === 1} onClick={() => setTemplate(current.filter((_, k) => k !== i))}>
                    Remove
                  </Button>
                </span>
              )}
            </li>
          ))}
        </ol>
        {canEdit && (
          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button variant="out" disabled={current.length >= 12 || active.length === 0} onClick={() => setTemplate([...current, active[0].id])}>
              Add match
            </Button>
            {dirty && (
              <>
                <Button variant="ghost" onClick={() => setTemplate(null)}>
                  Discard
                </Button>
                <Button
                  variant="volt"
                  loading={cmd.template.isPending}
                  onClick={() =>
                    cmd.template.mutate(current, {
                      onSuccess: () => {
                        setTemplate(null);
                        toast.success('Fixture template saved');
                      },
                      onError: (e) => toast.error(errorMessage(e)),
                    })
                  }
                >
                  Save template
                </Button>
              </>
            )}
            <span className="help">
              {data.fixturesGenerated ? 'Fixtures already exist — the new template applies only if you regenerate them.' : 'Used when fixtures are generated.'} A player may play at most{' '}
              {data.settings.maxMatchesPerPlayer} matches per tie (Competition › Format).
            </span>
          </div>
        )}
      </section>

      <Modal
        open={draft !== null}
        onClose={() => setDraft(null)}
        title={draft?.id ? `Edit ${draft.code}` : 'Add match category'}
        footer={
          <>
            <Button variant="out" onClick={() => setDraft(null)}>
              Cancel
            </Button>
            <Button variant="volt" loading={cmd.category.isPending} onClick={saveCategory}>
              Save category
            </Button>
          </>
        }
      >
        {draft && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div className={styles.grid2}>
              <Field label="Code" required help="Short, e.g. MS, XD, MD2." error={errors.code}>
                {({ id }) => <Input id={id} maxLength={8} value={draft.code} onChange={(e) => setDraft({ ...draft, code: e.target.value.toUpperCase() })} />}
              </Field>
              <Field label="Name" required error={errors.name}>
                {({ id }) => <Input id={id} maxLength={60} value={draft.name} onChange={(e) => setDraft({ ...draft, name: e.target.value })} />}
              </Field>
              <Field label="Players per side" error={errors.playersPerSide}>
                {({ id }) => (
                  <Select id={id} value={draft.playersPerSide} onChange={(e) => setDraft({ ...draft, playersPerSide: Number(e.target.value) as 1 | 2 })}>
                    <option value={1}>1 — singles</option>
                    <option value={2}>2 — doubles</option>
                  </Select>
                )}
              </Field>
              <Field label="Who may play" error={errors.genderRule}>
                {({ id }) => (
                  <Select id={id} value={draft.genderRule} onChange={(e) => setDraft({ ...draft, genderRule: e.target.value as GenderRule })}>
                    {(Object.keys(GENDER_LABEL) as GenderRule[]).map((g) => (
                      <option key={g} value={g}>
                        {GENDER_LABEL[g]}
                      </option>
                    ))}
                  </Select>
                )}
              </Field>
            </div>
            {draft.id && <Toggle id="cat-active" label="Active" checked={draft.active} onChange={(e) => setDraft({ ...draft, active: e.target.checked })} />}
            {errors.active && <span className="errmsg">{errors.active}</span>}
          </div>
        )}
      </Modal>
    </div>
  );
}

function toDraft(c: MatchCategory): Draft {
  return { id: c.id, code: c.code, name: c.name, playersPerSide: c.playersPerSide, genderRule: c.genderRule, active: c.active, version: c.version };
}
