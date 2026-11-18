'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';

export interface Category {
  id: string;
  name: string;
  description: string | null;
  active: boolean;
  displayOrder: number;
  playerCount: number;
  version: number;
}

export const categoriesKey = ['admin', 'categories'] as const;

export function useCategories() {
  return useQuery({ queryKey: categoriesKey, queryFn: () => api<Category[]>('/admin/categories') });
}

export function useCreateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (b: { name: string; description: string | null }) => api<Category>('/admin/categories', { method: 'POST', body: b }),
    onSuccess: () => qc.invalidateQueries({ queryKey: categoriesKey }),
  });
}

export function useUpdateCategory() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...b }: { id: string; name?: string; description?: string | null; active?: boolean; version: number }) =>
      api<Category>(`/admin/categories/${id}`, { method: 'PATCH', body: b }),
    onSuccess: () => qc.invalidateQueries({ queryKey: categoriesKey }),
  });
}

export function useReorderCategories() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ids: string[]) => api<Category[]>('/admin/categories/order', { method: 'PUT', body: { ids } }),
    onMutate: async (ids) => {
      await qc.cancelQueries({ queryKey: categoriesKey });
      const prev = qc.getQueryData<Category[]>(categoriesKey);
      if (prev) qc.setQueryData(categoriesKey, ids.map((id) => prev.find((c) => c.id === id)!).filter(Boolean));
      return { prev };
    },
    onError: (_e, _ids, ctx) => ctx?.prev && qc.setQueryData(categoriesKey, ctx.prev),
    onSettled: () => qc.invalidateQueries({ queryKey: categoriesKey }),
  });
}
