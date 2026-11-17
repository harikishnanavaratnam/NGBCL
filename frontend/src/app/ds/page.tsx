import type { Metadata } from 'next';
import { StyleGuide } from './StyleGuide';

export const metadata: Metadata = { title: 'Design system', robots: { index: false } };

export default function DesignSystemPage() {
  return (
    <main id="main">
      <StyleGuide />
    </main>
  );
}
