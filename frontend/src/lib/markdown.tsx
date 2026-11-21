import { Fragment, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * A tiny, safe Markdown renderer for the legal pages: headings, paragraphs, lists, block quotes,
 * tables, rules, **bold**, *italic*, `code` and [links](url). It builds React elements directly, so raw
 * HTML in the source is shown as text and never injected.
 */

/** Links to this site (or nudge.qd.je) stay inside the app; others open in a new tab. */
function safeHref(href: string) {
  const h = href.trim()
  if (/^https?:/i.test(h)) {
    try {
      const u = new URL(h)
      if (u.host === window.location.host || u.host === 'nudge.qd.je') return { external: false, href: `${u.pathname}${u.search}${u.hash}` || '/' }
    } catch {
      return null
    }
    return { external: true, href: h }
  }
  if (/^mailto:/i.test(h)) return { external: true, href: h }
  if (h.startsWith('/') && !h.startsWith('//')) return { external: false, href: h }
  if (h.startsWith('#')) return { external: false, href: h }
  return null
}

function inline(text: string, key = 'i'): ReactNode[] {
  const out: ReactNode[] = []
  const re = /(\*\*([^*]+)\*\*|__([^_]+)__|\*([^*\s][^*]*)\*|_([^_\s][^_]*)_|`([^`]+)`|\[([^\]]+)\]\(([^)\s]+)\)|<(https?:\/\/[^>\s]+)>)/g
  let last = 0
  let m: RegExpExecArray | null
  let n = 0
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index))
    const k = `${key}-${n++}`
    if (m[2] ?? m[3]) out.push(<strong key={k}>{inline(m[2] ?? m[3], k)}</strong>)
    else if (m[4] ?? m[5]) out.push(<em key={k}>{inline(m[4] ?? m[5], k)}</em>)
    else if (m[6]) out.push(<code key={k}>{m[6]}</code>)
    else if (m[7] && m[8]) {
      const link = safeHref(m[8])
      if (!link) out.push(m[7])
      else if (link.external) out.push(<a key={k} href={link.href} target="_blank" rel="noopener noreferrer">{inline(m[7], k)}</a>)
      else if (link.href.startsWith('#')) out.push(<a key={k} href={link.href}>{inline(m[7], k)}</a>)
      else out.push(<Link key={k} to={link.href}>{inline(m[7], k)}</Link>)
    } else if (m[9]) out.push(<a key={k} href={m[9]} target="_blank" rel="noopener noreferrer">{m[9]}</a>)
    last = m.index + m[0].length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')
const cells = (row: string) => row.trim().replace(/^\||\|$/g, '').split('|').map((c) => c.trim())

export function Markdown({ source }: { source: string }) {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let i = 0
  let k = 0
  const isListItem = (l: string) => /^\s*([-*+]|\d+[.)])\s+/.test(l)

  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }

    const h = /^(#{1,4})\s+(.*)$/.exec(line)
    if (h) {
      const level = h[1].length
      const text = h[2].replace(/\s*#+\s*$/, '')
      const props = { key: k++, id: slug(text) }
      blocks.push(level === 1 ? <h1 {...props}>{inline(text)}</h1> : level === 2 ? <h2 {...props}>{inline(text)}</h2> : <h3 {...props}>{inline(text)}</h3>)
      i++
      continue
    }

    if (/^(\*\s*){3,}$|^(-\s*){3,}$|^(_\s*){3,}$/.test(line.trim())) { blocks.push(<hr key={k++} />); i++; continue }

    if (line.trim().startsWith('|') && i + 1 < lines.length && /^\s*\|?\s*:?-{3,}/.test(lines[i + 1])) {
      const head = cells(line)
      i += 2
      const rows: string[][] = []
      while (i < lines.length && lines[i].trim().startsWith('|')) rows.push(cells(lines[i++]))
      blocks.push(
        <div key={k++} className="overflow-x-auto">
          <table>
            <thead><tr>{head.map((c, j) => <th key={j}>{inline(c)}</th>)}</tr></thead>
            <tbody>{rows.map((r, ri) => <tr key={ri}>{r.map((c, j) => <td key={j}>{inline(c)}</td>)}</tr>)}</tbody>
          </table>
        </div>,
      )
      continue
    }

    if (line.startsWith('>')) {
      const quote: string[] = []
      while (i < lines.length && lines[i].startsWith('>')) quote.push(lines[i++].replace(/^>\s?/, ''))
      blocks.push(<blockquote key={k++}>{inline(quote.join(' '))}</blockquote>)
      continue
    }

    if (isListItem(line)) {
      const ordered = /^\s*\d+[.)]\s+/.test(line)
      const items: { text: string; children: string[] }[] = []
      while (i < lines.length && (isListItem(lines[i]) || (/^\s{2,}\S/.test(lines[i]) && items.length))) {
        const l = lines[i]
        if (/^\s{2,}/.test(l) && items.length) {
          // Nested item or continuation line of the previous item.
          if (isListItem(l)) items[items.length - 1].children.push(l.replace(/^\s*([-*+]|\d+[.)])\s+/, ''))
          else items[items.length - 1].text += ` ${l.trim()}`
        } else items.push({ text: l.replace(/^\s*([-*+]|\d+[.)])\s+/, ''), children: [] })
        i++
      }
      const body = items.map((it, j) => (
        <li key={j}>
          {inline(it.text)}
          {it.children.length > 0 && <ul>{it.children.map((c, ci) => <li key={ci}>{inline(c)}</li>)}</ul>}
        </li>
      ))
      blocks.push(ordered ? <ol key={k++}>{body}</ol> : <ul key={k++}>{body}</ul>)
      continue
    }

    const para: string[] = [lines[i++].trim()]
    while (i < lines.length && lines[i].trim() && !/^(#{1,4})\s/.test(lines[i]) && !isListItem(lines[i]) && !lines[i].startsWith('>') && !lines[i].trim().startsWith('|')) {
      para.push(lines[i++].trim())
    }
    blocks.push(<p key={k++}>{para.map((p, j) => <Fragment key={j}>{j > 0 && ' '}{inline(p, `p${j}`)}</Fragment>)}</p>)
  }

  return <div className="prose-nd">{blocks}</div>
}
