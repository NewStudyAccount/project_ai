/** 极简 Markdown 渲染（标题/粗斜体/代码/列表/段落），避免引入平行依赖 */
export function renderMarkdown(src: string): string {
  if (!src) return ''
  const escaped = src
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const lines = escaped.split(/\r?\n/)
  const html: string[] = []
  let inCode = false
  let inList = false
  for (const line of lines) {
    if (line.startsWith('```')) {
      if (inCode) {
        html.push('</code></pre>')
        inCode = false
      } else {
        html.push('<pre><code>')
        inCode = true
      }
      continue
    }
    if (inCode) {
      html.push(line + '\n')
      continue
    }
    const h = /^(#{1,6})\s+(.*)$/.exec(line)
    if (h) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      const level = h[1].length
      html.push(`<h${level}>${inline(h[2])}</h${level}>`)
      continue
    }
    if (/^\s*[-*]\s+/.test(line)) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inline(line.replace(/^\s*[-*]\s+/, ''))}</li>`)
      continue
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    if (line.trim() === '') {
      continue
    }
    html.push(`<p>${inline(line)}</p>`)
  }
  if (inCode) html.push('</code></pre>')
  if (inList) html.push('</ul>')
  return html.join('\n')
}

function inline(text: string): string {
  return text
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
}
