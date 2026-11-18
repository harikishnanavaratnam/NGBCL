import { SettingsShell } from '@/features/settings/SettingsShell';

export const metadata = { title: 'Tournament settings' };

export default function SettingsLayout({ children }: LayoutProps<'/admin/system/settings'>) {
  return <SettingsShell>{children}</SettingsShell>;
}
