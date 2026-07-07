#!/usr/bin/env python3
"""Build ARCHITECTURE.pdf from ARCHITECTURE.md (Markdown -> HTML -> PDF).

Toolchain: python-markdown (tables/fenced code/TOC) + Pygments + WeasyPrint.
SVG figures under img/ are embedded; base_url is set to the markdown's folder
so relative img/ references resolve.

Usage:
    python3 build_pdf.py [ARCHITECTURE.md] [ARCHITECTURE.pdf]
Defaults to the files sitting next to this script.
"""
import sys
from pathlib import Path

import markdown

try:
    from pygments.formatters import HtmlFormatter
    _HAS_PYGMENTS = True
except ImportError:  # syntax highlighting is optional
    _HAS_PYGMENTS = False

HERE = Path(__file__).resolve().parent
MD_PATH = Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "ARCHITECTURE.md"
PDF_PATH = Path(sys.argv[2]) if len(sys.argv) > 2 else HERE / "ARCHITECTURE.pdf"

md_text = MD_PATH.read_text(encoding="utf-8")

extensions = ["extra", "toc", "sane_lists", "admonition"]
extension_configs = {"toc": {"title": "Indice", "toc_depth": "2-3"}}
if _HAS_PYGMENTS:
    extensions.append("codehilite")
    extension_configs["codehilite"] = {"guess_lang": False, "css_class": "codehilite", "noclasses": False}

md = markdown.Markdown(extensions=extensions, extension_configs=extension_configs)

# inject TOC placeholder at top if not present
if "[TOC]" not in md_text:
    md_text = "[TOC]\n\n" + md_text

html_body = md.convert(md_text)
pygments_css = HtmlFormatter(style="friendly").get_style_defs(".codehilite") if _HAS_PYGMENTS else ""

CSS = """
@page {
    size: A4;
    margin: 1.6cm 1.4cm 1.6cm 1.4cm;
    @bottom-center { content: counter(page) " / " counter(pages); font-size: 9pt; color: #888; }
    @top-left { content: "runtime-tvm — Architettura"; font-size: 9pt; color: #888; }
}
body { font-family: 'DejaVu Sans', 'Liberation Sans', sans-serif; font-size: 10pt; line-height: 1.45; color: #222; }
h1 { color: #1a4480; font-size: 22pt; border-bottom: 2px solid #1a4480; padding-bottom: 0.2em; page-break-before: always; }
h1:first-of-type { page-break-before: avoid; }
h2 { color: #1a4480; font-size: 14pt; border-bottom: 1px solid #ccc; padding-bottom: 0.15em; margin-top: 1.6em; }
h3 { color: #2c5fa0; font-size: 12pt; margin-top: 1.2em; }
h4 { color: #444; font-size: 11pt; }
.toc { background: #f5f7fb; border: 1px solid #d0d7e2; border-radius: 4px; padding: 0.4em 1em; margin: 0 0 1.5em 0; font-size: 9.5pt; }
.toc ul { margin: 0.3em 0; padding-left: 1.4em; }
.toc li { margin: 0.05em 0; }
.toc a { color: #1a4480; text-decoration: none; }
code { font-family: 'DejaVu Sans Mono', 'Liberation Mono', monospace; font-size: 8.5pt; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }
pre { background: #f6f8fa; border: 1px solid #e0e4e8; border-radius: 4px; padding: 0.5em 0.7em; font-size: 7.5pt; overflow-x: auto; white-space: pre; line-height: 1.25; page-break-inside: avoid; }
pre code { background: none; padding: 0; font-size: 7.5pt; }
.codehilite { background: #f6f8fa; }
img { max-width: 100%; }
table { border-collapse: collapse; width: 100%; margin: 0.6em 0; font-size: 9pt; page-break-inside: avoid; }
th, td { border: 1px solid #d0d7e2; padding: 4px 8px; text-align: left; vertical-align: top; }
th { background: #eef2f8; font-weight: bold; }
tr:nth-child(even) td { background: #fafbfd; }
blockquote { border-left: 3px solid #1a4480; padding-left: 1em; color: #555; margin-left: 0; }
ul, ol { margin: 0.4em 0; padding-left: 1.4em; }
li { margin: 0.15em 0; }
a { color: #1a4480; }
""" + pygments_css

html = f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"><title>runtime-tvm — Architettura</title>
<style>{CSS}</style></head><body>{html_body}</body></html>
"""

import weasyprint  # noqa: E402

weasyprint.HTML(string=html, base_url=str(MD_PATH.parent)).write_pdf(str(PDF_PATH))
print(f"OK: {PDF_PATH} ({PDF_PATH.stat().st_size/1024:.1f} KB)")
