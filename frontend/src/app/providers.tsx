'use client';

import { QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { MotionProvider } from '@/design-system/motion/MotionProvider';
import { ToastProvider } from '@/design-system/primitives/Toast';
import { AuthProvider } from '@/lib/auth/session';
import { makeQueryClient } from '@/lib/query/client';

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(makeQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <MotionProvider>
        <ToastProvider>
          <AuthProvider>{children}</AuthProvider>
        </ToastProvider>
      </MotionProvider>
    </QueryClientProvider>
  );
}
