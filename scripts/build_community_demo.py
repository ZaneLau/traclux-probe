"""Build the public read-only case from a reviewed, allowlisted JSON artifact."""
import json
from pathlib import Path

def main():
    root = Path(__file__).resolve().parent.parent
    demo = root / "docs/demo"
    data = json.loads((demo / "case.json").read_text())
    embedded = json.dumps(data, ensure_ascii=False).replace("<", "\\u003c")
    (demo / "index.html").write_text((demo / "template.html").read_text().replace("{{CASE_DATA}}", embedded))

if __name__ == "__main__":
    main()
