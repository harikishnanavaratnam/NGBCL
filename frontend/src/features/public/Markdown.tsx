import { Fragment, type ReactNode } from 'react';
import s from './Public.module.css';

/**
 * Tiny, safe renderer for announcement bodies: paragraphs, "- " / "1. " lists, "### " headings,
 * **bold**, *italic* and [links](https://…). No HTML is ever injected; only http(s) and mailto
 * links are kept.
 */
export function Markdown({ text }: { text: string }) {
  const blocks = text.replace(/\r\n/g, '\n').split(/\n{2,}/);
  return (
    <div className={s.prose}>
      {blocks.map((block, i) => {
        const lines = block.split('\n').filter((l) => l.trim() !== '');
        if (lines.length === 0) return null;
        if (lines.every((l) => /^\s*[-•*]\s+/.test(l))) {
          return (
            <ul key={i}>
              {lines.map((l, j) => (
                <li key={j}>{inline(l.replace(/^\s*[-•*]\s+/, ''))}</li>
              ))}
            </ul>
          );
        }
        if (lines.every((l) => /^\s*\d+[.)]\s+/.test(l))) {
          return (
            <ol key={i}>
              {lines.map((l, j) => (
                <li key={j}>{inline(l.replace(/^\s*\d+[.)]\s+/, ''))}</li>
              ))}
            </ol>
          );
        }
        if (lines.length === 1 && /^#{1,3}\s+/.test(lines[0])) {
          return <h3 key={i}>{inline(lines[0].replace(/^#{1,3}\s+/, ''))}</h3>;
        }
        return (
          <p key={i}>
            {lines.map((l, j) => (
              <Fragment key={j}>
                {j > 0 && <br />}
                {inline(l)}
              </Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}

const TOKEN = /(\[([^\]]+)\]\(([^)\s]+)\)|\*\*([^*]+)\*\*|\*([^*]+)\*|_([^_]+)_)/g;

function safeHref(url: string) {
  return /^(https?:\/\/|mailto:)/i.test(url) ? url : null;
}

function inline(text: string): ReactNode[] {
  const out: ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  let k = 0;
  TOKEN.lastIndex = 0;
  while ((m = TOKEN.exec(text)) !== null) {
    if (m.index > last) out.push(text.slice(last, m.index));
    if (m[2] !== undefined) {
      const href = safeHref(m[3]);
      out.push(
        href ? (
          <a key={k++} href={href} target={href.startsWith('http') ? '_blank' : undefined} rel="noopener noreferrer">
            {m[2]}
          </a>
        ) : (
          m[2]
        ),
      );
    } else if (m[4] !== undefined) out.push(<strong key={k++}>{m[4]}</strong>);
    else out.push(<em key={k++}>{m[5] ?? m[6]}</em>);
    last = TOKEN.lastIndex;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}
