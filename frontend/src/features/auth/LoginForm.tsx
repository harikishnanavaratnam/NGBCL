'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/design-system/primitives/Button';
import { Field } from '@/design-system/primitives/Field';
import { Input } from '@/design-system/primitives/Inputs';
import { ApiError, errorMessage } from '@/lib/api/errors';
import { homePathFor, useAuth } from '@/lib/auth/session';

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email').email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
});
type Values = z.infer<typeof schema>;

/** Staff sign-in. Players, teams and visitors never need an account. */
export function LoginForm() {
  const { login } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [formError, setFormError] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const user = await login(values);
      const next = params.get('next');
      router.replace(next && next.startsWith('/') && !next.startsWith('//') ? next : homePathFor(user));
    } catch (e) {
      setFormError(
        e instanceof ApiError && e.status === 401
          ? 'Email or password is incorrect.'
          : e instanceof ApiError && e.status === 429
            ? 'Too many attempts. Wait a minute and try again.'
            : errorMessage(e),
      );
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
      {formError && (
        <div role="alert" className="chip c-err" style={{ whiteSpace: 'normal', textTransform: 'none', letterSpacing: 0, fontSize: 14, padding: '10px 12px' }}>
          {formError}
        </div>
      )}
      <Field label="Email" error={errors.email?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="email" autoComplete="username" aria-describedby={describedBy} aria-invalid={invalid} {...register('email')} />
        )}
      </Field>
      <Field label="Password" error={errors.password?.message}>
        {({ id, describedBy, invalid }) => (
          <Input id={id} type="password" autoComplete="current-password" aria-describedby={describedBy} aria-invalid={invalid} {...register('password')} />
        )}
      </Field>
      <Button type="submit" variant="volt" loading={isSubmitting}>
        Sign in
      </Button>
    </form>
  );
}
