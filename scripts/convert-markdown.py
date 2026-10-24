"""Archive project Markdown in the existing HTML guide, then remove verified sources."""
from pathlib import Path
import html
import re
from urllib.parse import unquote

root = Path(__file__).resolve().parents[1]
excluded = {'.git', 'node_modules', 'target', '.codex', '.agents', '.aws', '.venv', 'venv'}
files = sorted(p for p in root.rglob('*') if p.is_file() and p.suffix.lower() == '.md' and not excluded.intersection(p.relative_to(root).parts))
assert files, 'No Markdown sources found'
sources = {p: p.read_text(encoding='utf-8-sig') for p in files}
ids = {p: 'markdown-' + re.sub(r'[^a-z0-9]+', '-', p.relative_to(root).as_posix().lower()).strip('-') for p in files}

def render(source, path):
    anchors = {}
    used = {}
    def slug(text):
        base = re.sub(r'[^\w\- ]', '', text.lower()).replace(' ', '-')
        n = used.get(base, 0)
        used[base] = n + 1
        return base + (f'-{n}' if n else '')
    for line in source.splitlines():
        m = re.match(r'^#{1,6}\s+(.+)', line)
        if m:
            key = slug(m[1])
            anchors[key] = ids[path] + '-' + key
    def link(url):
        target, _, fragment = html.unescape(url).partition('#')
        resolved = (path.parent / unquote(target)).resolve() if target else path
        if resolved in ids:
            return '#' + ids[resolved] + ('-' + fragment if fragment else '')
        if target and not re.match(r'^(?:[a-z]+:|/)', target, re.I):
            return html.escape((path.relative_to(root).parent / target).as_posix(), quote=True) + ('#' + fragment if fragment else '')
        return url
    def inline(text):
        text = html.escape(text)
        tokens = []
        def code(m):
            tokens.append('<code>' + m[1] + '</code>')
            return f'\x00{len(tokens)-1}\x00'
        text = re.sub(r'`([^`]+)`', code, text)
        text = re.sub(r'!\[([^\]]*)\]\(([^)]+)\)', lambda m: f'<img alt="{m[1]}" src="{link(m[2])}" loading="lazy">', text)
        text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', lambda m: f'<a href="{link(m[2])}">{m[1]}</a>', text)
        text = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', text)
        text = re.sub(r'(?<!\*)\*([^*]+)\*', r'<em>\1</em>', text)
        return re.sub(r'\x00(\d+)\x00', lambda m: tokens[int(m[1])], text)
    used.clear()
    out = []
    lines = source.splitlines()
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        fence = re.match(r'^\s*(`{3,}|~{3,})(.*)', line)
        if fence:
            code = []
            i += 1
            while i < len(lines) and not lines[i].lstrip().startswith(fence[1]):
                code.append(lines[i]); i += 1
            out.append('<pre><code class="language-' + html.escape(fence[2].strip(), quote=True) + '">' + html.escape('\n'.join(code)) + '</code></pre>')
        elif (m := re.match(r'^(#{1,6})\s+(.+)', line)):
            level = min(6, len(m[1]) + 2)
            out.append(f'<h{level} id="{ids[path]}-{slug(m[2])}">{inline(m[2])}</h{level}>')
        elif i + 1 < len(lines) and '|' in line and re.match(r'^\s*\|?\s*:?-{3,}', lines[i+1]):
            cells = lambda row: row.strip().strip('|').split('|')
            out.append('<div class="tablebox"><table><thead><tr>' + ''.join('<th>' + inline(c.strip()) + '</th>' for c in cells(line)) + '</tr></thead><tbody>')
            i += 2
            while i < len(lines) and '|' in lines[i] and lines[i].strip():
                out.append('<tr>' + ''.join('<td>' + inline(c.strip()) + '</td>' for c in cells(lines[i])) + '</tr>'); i += 1
            out.append('</tbody></table></div>')
            continue
        elif re.match(r'^\s*([-+*]|\d+\.)\s+', line):
            ordered = bool(re.match(r'^\s*\d+\.', line))
            tag = 'ol' if ordered else 'ul'
            out.append('<' + tag + '>')
            while i < len(lines) and (m := re.match(r'^\s*(?:[-+*]|\d+\.)\s+(.+)', lines[i])):
                out.append('<li>' + inline(m[1]) + '</li>'); i += 1
            out.append('</' + tag + '>')
            continue
        elif line.startswith('>'):
            out.append('<blockquote><p>' + inline(line.lstrip('> ')) + '</p></blockquote>')
        elif re.match(r'^\s*([-*_])\1{2,}\s*$', line):
            out.append('<hr>')
        else:
            out.append('<p>' + inline(line) + '</p>')
        i += 1
    return '\n'.join(out)

target = root / 'indexLatest.html'
original = target.read_text(encoding='utf-8')
assert original.count('</main>') == 1
parts = ['<section id="markdown-documents" class="doc-group"><h2>Project Markdown documents</h2><nav aria-label="Converted documents"><ul>']
for p in files:
    parts.append(f'<li><a href="#{ids[p]}">{html.escape(p.relative_to(root).as_posix())}</a></li>')
parts.append('</ul></nav>')
for p, source in sources.items():
    parts.append(f'<article class="doc-section" id="{ids[p]}"><h2>{html.escape(p.relative_to(root).as_posix())}</h2>')
    parts.append(render(source, p))
    # Preserve exact original text as well as the readable conversion.
    parts.append('<details><summary>Original Markdown source</summary><pre><code>' + html.escape(source) + '</code></pre></details></article>')
parts.append('</section>')
result = original.replace('</main>', '\n'.join(parts) + '</main>')
target.write_text(result, encoding='utf-8')
verified = target.read_text(encoding='utf-8')
for p, source in sources.items():
    assert html.escape(source) in verified, f'Content missing: {p}'
    assert f'id="{ids[p]}"' in verified
    assert p.read_text(encoding='utf-8-sig') == source, f'Source changed: {p}'
for p in files:
    p.unlink()
print(f'Converted and verified {len(files)} Markdown files in {target.name}; deleted sources:')
for p in files:
    print(p.relative_to(root))
