import type { Metadata } from 'next';
import Link from 'next/link';
import { Suspense } from 'react';
import { EmptyState, Skeleton } from '@/design-system/data/States';
import { Markdown } from '@/features/public/Markdown';
import s from '@/features/public/Public.module.css';
import { getArticle, staticSlugs } from '@/lib/api/public';
import { formatDateTime } from '@/lib/format/dates';

export async function generateStaticParams() {
  return staticSlugs('news');
}

export async function generateMetadata({ params }: PageProps<'/news/[slug]'>): Promise<Metadata> {
  const a = await getArticle((await params).slug);
  return a ? { title: a.title, openGraph: a.coverUrl ? { images: [a.coverUrl] } : undefined } : { title: 'News' };
}

export default function ArticlePage(props: PageProps<'/news/[slug]'>) {
  return (
    <Suspense
      fallback={
        <div className="wrap" style={{ padding: '56px 32px' }}>
          <Skeleton height={320} />
        </div>
      }
    >
      <Article params={props.params} />
    </Suspense>
  );
}

async function Article({ params }: Pick<PageProps<'/news/[slug]'>, 'params'>) {
  const a = await getArticle((await params).slug);
  if (!a) {
    return (
      <div className="wrap" style={{ padding: '64px 32px' }}>
        <EmptyState
          title="Article not found"
          description="It may have been removed or isn’t published yet."
          action={{ label: 'All news', href: '/news' }}
        />
      </div>
    );
  }
  return (
    <article className={s.sec} style={{ borderTop: 0, paddingTop: 48 }}>
      <div className="wrap">
        <div className={s.article}>
          <div className={`eyebrow ${s.crumb}`}>
            <Link href="/news">News</Link> / Article
          </div>
          <h1 style={{ margin: 0, fontSize: 'clamp(28px, 4vw, 40px)', lineHeight: 1.15 }}>{a.title}</h1>
          <div className="muted" style={{ fontSize: 14 }}>
            <span className="mono">{formatDateTime(a.publishedAt)}</span> · NGBCL organisers
          </div>
          {a.coverUrl && (
            // eslint-disable-next-line @next/next/no-img-element
            <img className={s.newsCover} src={a.coverUrl} alt="" />
          )}
          <Markdown text={a.body} />
          <div style={{ borderTop: '1px solid var(--line)', paddingTop: 20 }}>
            <Link href="/news" className="link">
              ← All news
            </Link>
          </div>
        </div>
      </div>
    </article>
  );
}
