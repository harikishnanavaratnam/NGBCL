'use client';

import { DndContext, KeyboardSensor, PointerSensor, closestCenter, useSensor, useSensors, type DragEndEvent } from '@dnd-kit/core';
import { SortableContext, arrayMove, sortableKeyboardCoordinates, useSortable, verticalListSortingStrategy } from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { EmptyState, ErrorState, SkeletonRows } from '@/design-system/data/States';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input, Textarea } from '@/design-system/primitives/Inputs';
import { Drawer } from '@/design-system/primitives/Overlay';
import { useToast } from '@/design-system/primitives/Toast';
import { useCategories, useCreateCategory, useReorderCategories, useUpdateCategory, type Category } from '@/lib/api/categories';
import { errorMessage } from '@/lib/api/errors';
import { registrationPayload, useSaveRegistration, useSettings } from '@/lib/api/settings';
import { useAuth } from '@/lib/auth/session';
import styles from './Settings.module.css';
import { useSaveErrors } from './useSaveErrors';

const schema = z.object({
  name: z.string().trim().min(1, 'Give the category a name').max(60, 'Keep it under 60 characters'),
  description: z.string().max(300, 'Keep it under 300 characters'),
});
type Values = z.infer<typeof schema>;

function Row({ c, canEdit, onEdit, onToggle, busy }: { c: Category; canEdit: boolean; onEdit: () => void; onToggle: (v: boolean) => void; busy: boolean }) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: c.id, disabled: !canEdit });
  return (
    <tr
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition, background: isDragging ? 'var(--surface-2)' : undefined, position: 'relative', zIndex: isDragging ? 2 : undefined }}
    >
      <td style={{ width: 36 }}>
        {canEdit && (
          <button type="button" className="handle" aria-label={`Reorder ${c.name}`} {...attributes} {...listeners} style={{ background: 'none', border: 0, padding: 4, color: 'var(--text-3)', cursor: 'grab' }}>
            ⋮⋮
          </button>
        )}
      </td>
      <td>
        <strong style={{ fontWeight: 600 }}>{c.name}</strong>
        {!c.active && <div className="t-caption">Hidden from new registrations</div>}
      </td>
      <td className="muted">{c.description ?? '—'}</td>
      <td className="r">{c.playerCount || '—'}</td>
      <td className="c">
        <input type="checkbox" role="switch" className="sw" checked={c.active} disabled={!canEdit || busy} aria-label={`${c.name} active`} onChange={(e) => onToggle(e.target.checked)} />
      </td>
      <td className="r">
        {canEdit && (
          <Button size="sm" variant="ghost" onClick={onEdit}>
            Edit
          </Button>
        )}
      </td>
    </tr>
  );
}

/** Skill categories (CAT-001..006, export AdminSet-4 / AdminPpl-7): drag to reorder, activate / deactivate. */
export function CategoriesPanel() {
  const toast = useToast();
  const { can } = useAuth();
  const canEdit = can('CATEGORY_MANAGE');
  const { data, isLoading, error, refetch } = useCategories();
  const settings = useSettings();
  const saveReg = useSaveRegistration();
  const create = useCreateCategory();
  const update = useUpdateCategory();
  const reorder = useReorderCategories();
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const sensors = useSensors(useSensor(PointerSensor, { activationConstraint: { distance: 4 } }), useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }));

  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', description: '' } });
  const onFormError = useSaveErrors(form.setError);

  const open = (c: Category | 'new') => {
    form.reset(c === 'new' ? { name: '', description: '' } : { name: c.name, description: c.description ?? '' });
    setEditing(c);
  };

  const submit = form.handleSubmit((v) => {
    const description = v.description.trim() || null;
    const done = { onSuccess: () => { toast.success(editing === 'new' ? 'Category added' : 'Category updated'); setEditing(null); }, onError: onFormError };
    if (editing === 'new') create.mutate({ name: v.name, description }, done);
    else if (editing) update.mutate({ id: editing.id, name: v.name, description, version: editing.version }, done);
  });

  const onDragEnd = (e: DragEndEvent) => {
    if (!data || !e.over || e.active.id === e.over.id) return;
    const from = data.findIndex((c) => c.id === e.active.id);
    const to = data.findIndex((c) => c.id === e.over!.id);
    const ids = arrayMove(data, from, to).map((c) => c.id);
    reorder.mutate(ids, { onError: (err) => toast.error(errorMessage(err)) });
  };

  const required = settings.data?.registration.categoryRequired ?? false;

  return (
    <div className={styles.panel}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
        <h2 className="t-h2">Skill categories</h2>
        {canEdit && (
          <Button variant="volt" size="sm" onClick={() => open('new')}>
            Add category
          </Button>
        )}
      </div>

      {settings.data && (
        <label className="panel-flat" style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 16, padding: 14, flexWrap: 'wrap' }}>
          <span>
            <strong style={{ fontSize: 14 }}>Required at registration</strong>
            <span className="t-caption" style={{ display: 'block' }}>
              {required ? 'On: players must choose a category.' : 'Off: players may skip choosing a category (default).'}
            </span>
          </span>
          <input
            type="checkbox"
            role="switch"
            className="sw"
            checked={required}
            disabled={!can('SETTINGS_MANAGE') || saveReg.isPending}
            aria-label="Skill category required at registration"
            onChange={(e) =>
              saveReg.mutate(registrationPayload(settings.data!, { categoryRequired: e.target.checked }), {
                onSuccess: () => toast.success(e.target.checked ? 'Category is now required' : 'Category is now optional'),
                onError: (err) => toast.error(errorMessage(err)),
              })
            }
          />
        </label>
      )}

      {isLoading && <SkeletonRows rows={4} cols={4} />}
      {error && <ErrorState message={errorMessage(error)} onRetry={() => void refetch()} />}
      {data && data.length === 0 && (
        <EmptyState
          title="No skill categories yet"
          description="Categories are optional. Add the levels you want players to choose from, e.g. the ones in your rules."
          action={canEdit ? { label: 'Add category', onClick: () => open('new') } : undefined}
        />
      )}
      {data && data.length > 0 && (
        <div className="scroll">
          <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
            <table className="tbl">
              <thead>
                <tr>
                  <th aria-label="Reorder" />
                  <th>Name</th>
                  <th>Description</th>
                  <th className="r">Players</th>
                  <th className="c">Active</th>
                  <th />
                </tr>
              </thead>
              <SortableContext items={data.map((c) => c.id)} strategy={verticalListSortingStrategy}>
                <tbody>
                  {data.map((c) => (
                    <Row
                      key={c.id}
                      c={c}
                      canEdit={canEdit}
                      busy={update.isPending}
                      onEdit={() => open(c)}
                      onToggle={(active) =>
                        update.mutate(
                          { id: c.id, active, version: c.version },
                          { onSuccess: () => toast.success(active ? `${c.name} is active` : `${c.name} hidden from new registrations`), onError: (err) => toast.error(errorMessage(err)) },
                        )
                      }
                    />
                  ))}
                </tbody>
              </SortableContext>
            </table>
          </DndContext>
        </div>
      )}
      <p className="t-caption">Names are yours to define. Deactivated categories stay on existing players’ records. Drag ⋮⋮ (or focus it and use the arrow keys) to reorder.</p>

      <Drawer
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing === 'new' ? 'Add skill category' : 'Edit skill category'}
        footer={
          <>
            <Button variant="out" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button variant="volt" loading={create.isPending || update.isPending} onClick={() => void submit()}>
              {editing === 'new' ? 'Add category' : 'Save'}
            </Button>
          </>
        }
      >
        <form onSubmit={submit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <Field label="Name" required error={form.formState.errors.name?.message}>
            {({ id, describedBy }) => <Input id={id} aria-describedby={describedBy} data-autofocus {...form.register('name')} />}
          </Field>
          <Field label="Description" optional help="Shown to players on the registration form." error={form.formState.errors.description?.message}>
            {({ id }) => <Textarea id={id} rows={3} {...form.register('description')} />}
          </Field>
        </form>
      </Drawer>
    </div>
  );
}
