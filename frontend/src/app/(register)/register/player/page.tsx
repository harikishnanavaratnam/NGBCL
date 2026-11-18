import { PlayerRegistration } from '@/features/registration/PlayerRegistration';

export const metadata = { title: 'Register as a player', description: 'Register for NGBCL 1.0 — Jaffna players only. No account needed.' };

export default function Page() {
  return <PlayerRegistration />;
}
