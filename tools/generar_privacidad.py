"""Genera docs/privacy/index.html (política de privacidad para GitHub Pages) a partir de
las seis políticas en Markdown de docs/. Uso: python3 tools/generar_privacidad.py"""
import html, re, pathlib
ROOT = pathlib.Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
LANGS = [("es", "Español", "PRIVACIDAD.md"), ("en", "English", "PRIVACY_EN.md"),
         ("pt", "Português", "PRIVACIDADE_PT.md"), ("fr", "Français", "CONFIDENTIALITE_FR.md"),
         ("de", "Deutsch", "DATENSCHUTZ_DE.md"), ("it", "Italiano", "PRIVACY_IT.md")]

def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    return re.sub(r"(https://[^\s<)]+[^\s<).,;])", r'<a href="\1">\1</a>', text)

def convert(md):
    out, items = [], []
    def flush():
        if items: out.append("<ul>" + "".join(f"<li>{i}</li>" for i in items) + "</ul>"); items.clear()
    for line in md.splitlines():
        line = line.rstrip()
        if line.startswith("- "): items.append(inline(line[2:])); continue
        flush()
        if not line: continue
        if line.startswith("## "): out.append(f"<h2>{inline(line[3:])}</h2>")
        elif line.startswith("# "): out.append(f"<h1>{inline(line[2:])}</h1>")
        else: out.append(f"<p>{inline(line)}</p>")
    flush()
    return "\n".join(out)

tabs = "".join(f'<a href="#{c}" data-lang="{c}">{n}</a>' for c, n, _ in LANGS)
sections = "".join(f'<section data-id="{c}" lang="{c}">{convert((DOCS / f).read_text(encoding="utf-8"))}</section>'
                   for c, _, f in LANGS)
page = f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Bookhollow · Privacidad / Privacy</title>
<style>
:root {{ --bg:#f7f1e6; --ink:#2c211b; --muted:#6b5646; --accent:#2f7f73; --card:#fffaf2; --line:#e3d6c2; }}
@media (prefers-color-scheme: dark) {{ :root {{ --bg:#1b1714; --ink:#f1e7d8; --muted:#c9b59f; --accent:#7fc8b8; --card:#241f1b; --line:#3a322b; }} }}
* {{ box-sizing:border-box; }}
body {{ margin:0; background:var(--bg); color:var(--ink); font:16px/1.6 Georgia, "Times New Roman", serif; }}
header {{ position:sticky; top:0; background:var(--bg); border-bottom:1px solid var(--line); padding:12px 16px; }}
header strong {{ display:block; font-size:20px; margin-bottom:6px; }}
nav {{ display:flex; flex-wrap:wrap; gap:6px; }}
nav a {{ font:14px system-ui, sans-serif; color:var(--accent); text-decoration:none; border:1px solid var(--line); border-radius:999px; padding:4px 12px; }}
nav a.on {{ background:var(--accent); color:var(--bg); border-color:var(--accent); }}
main {{ max-width:760px; margin:0 auto; padding:16px; }}
section {{ display:none; background:var(--card); border:1px solid var(--line); border-radius:16px; padding:8px 20px 20px; }}
section.on {{ display:block; }}
h1 {{ font-size:26px; line-height:1.25; }} h2 {{ font-size:19px; margin-top:28px; color:var(--muted); }}
a {{ color:var(--accent); overflow-wrap:anywhere; }}
</style></head>
<body>
<header><strong>Bookhollow</strong><nav>{tabs}</nav></header>
<main>{sections}</main>
<script>
const langs = {[c for c, _, _ in LANGS]};
function show(lang) {{
  if (!langs.includes(lang)) lang = "en";
  document.querySelectorAll("section").forEach(s => s.classList.toggle("on", s.dataset.id === lang));
  document.querySelectorAll("nav a").forEach(a => a.classList.toggle("on", a.dataset.lang === lang));
  document.documentElement.lang = lang;
  window.scrollTo(0, 0);
}}
const initial = location.hash.slice(1) || (navigator.language || "en").slice(0, 2);
show(initial);
window.addEventListener("hashchange", () => show(location.hash.slice(1)));
</script>
</body></html>
"""
(DOCS / "privacy").mkdir(exist_ok=True)
(DOCS / "privacy" / "index.html").write_text(page, encoding="utf-8")
(DOCS / ".nojekyll").write_text("", encoding="utf-8")
print("docs/privacy/index.html generado")
