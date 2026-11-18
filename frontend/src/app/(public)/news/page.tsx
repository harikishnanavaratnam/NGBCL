import clsx from 'clsx';
import Link from 'next/link';
import { EmptyState } from '@/design-system/data/States';
import s from '@/features/public/Public.module.css';
import { NewsCard, PageBand } from '@/features/public/Sections';
import { getNews } from '@/lib/api/public';
import { formatDate } from '@/lib/format/dates';

export const metadata = { title: 'News', description: 'Announcements from the NGBCL 1.0 organisers.' };

export default async function NewsPage() {
  const news = await getNews(0, 30);
  const [first, ...rest] = news.content;
  const featured = first?.pinned ? first : null;
  const list = featured ? rest : news.content;
  return (
    <>
      <PageBand eyebrow="From the organisers" title="News" />
      <section className={s.sec} style={{ borderTop: 0, paddingTop: 32 }}>
        <div className="wrap" style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          {news.content.length === 0 && (
            <EmptyState
              title="No news yet"
              description="Announcements from the organisers will appear here."
              action={{ label: 'Back to home', href: '/' }}
            />
          )}
          {featured && (
            <Link href={`/news/${featured.slug}`} prefetch className={s.cardLink}>
              <article className={clsx('card', s.featured)}>
                {featured.coverUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img className={s.newsCover} src={featured.coverUrl} alt="" />
                ) : (
                  <div className={s.newsCover} aria-hidden="true" />
                )}
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <span style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                    <span className={clsx('chip', s.pinned)}>Pinned</span>
                    <span className="mono muted" style={{ fontSize: 12 }}>
                      {formatDate(featured.publishedAt)}
                    </span>
                  </span>
                  <h2 style={{ margin: 0, fontSize: 26 }}>{featured.title}</h2>
                  <p className="muted" style={{ margin: 0 }}>
                    {featured.excerpt}
                  </p>
                  <span className="link">Read more</span>
                </div>
              </article>
            </Link>
          )}
          {list.length > 0 && (
            <div className={s.g3}>
              {list.map((n) => (
                <NewsCard key={n.slug} n={n} />
              ))}
            </div>
          )}
        </div>
      </section>
    </>
  );
}
