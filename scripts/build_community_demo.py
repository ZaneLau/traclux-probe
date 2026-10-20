"""Build the public read-only case from a reviewed, allowlisted JSON artifact."""
import json
import hashlib
from pathlib import Path

def main():
    root = Path(__file__).resolve().parent.parent
    demo = root / "docs/demo"
    data = json.loads((demo / "case.json").read_text())
    embedded = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
    html = (demo / "template.html").read_text().replace("{{CASE_DATA}}", embedded)
    for placeholder, name in [("STYLE_HASH", "style.css"), ("SCRIPT_HASH", "case-viewer.js")]:
        html = html.replace("{{" + placeholder + "}}", hashlib.sha256((demo / name).read_bytes()).hexdigest()[:12])
    (demo / "index.html").write_text(html)

if __name__ == "__main__":
    main()
