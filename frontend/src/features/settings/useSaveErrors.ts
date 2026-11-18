'use client';

import { useQueryClient } from '@tanstack/react-query';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { useToast } from '@/design-system/primitives/Toast';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { settingsKey } from '@/lib/api/settings';

/**
 * Turns a failed save into the right feedback: field errors go next to their fields,
 * a STALE_DATA conflict offers a reload, anything else becomes an error toast.
 */
export function useSaveErrors<T extends FieldValues>(setError?: UseFormSetError<T>, fieldMap: Record<string, string> = {}) {
  const toast = useToast();
  const qc = useQueryClient();
  return (e: unknown) => {
    if (e instanceof ApiError && e.code === 'STALE_DATA') {
      toast.show({
        tone: 'error',
        message: e.message,
        duration: 0,
        action: { label: 'Reload', onClick: () => void qc.invalidateQueries({ queryKey: settingsKey }) },
      });
      return;
    }
    if (e instanceof ApiError && setError && Object.keys(e.fieldErrors).length) {
      for (const [field, message] of Object.entries(e.fieldErrors)) {
        setError((fieldMap[field] ?? field) as Path<T>, { message });
      }
      toast.error('Some fields need attention.');
      return;
    }
    toast.error(errorMessage(e));
  };
}
