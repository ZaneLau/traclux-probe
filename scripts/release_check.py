"""Local/CI source release gate. Never prints secret-bearing matched lines."""
import json
from pathlib import Path
import re
import subprocess
import sys

from check_community_boundary import check
from check_public_demo import check_case, check_viewer

def main():
    root = Path(__file__).resolve().parent.parent
    findings = check(root)
    files = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
    signature = re.compile(rb"sk-(?:proj-)?[A-Za-z0-9_-]{30,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]{20,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|ep-\d{12,}-[a-z0-9]{5,}")
    for name in filter(None, files):
        path = root / name
        if not path.is_file():
            continue
        if any(part in {".private", ".runtime", "node_modules", "target", ".output"} for part in path.relative_to(root).parts) or (path.name.startswith(".env") and not path.name.endswith(".example")):
            findings.append(f"Private/generated file tracked: {name}")
        if signature.search(path.read_bytes()):
            findings.append(f"Credential signature; inspect privately: {name}")
    demo = root / "docs/demo"
    data = json.loads((demo / "case.json").read_text())
    findings.extend(check_case(data))
    findings.extend(check_viewer((demo / "index.html").read_text()))
    embedded = re.search(r'<script type="application/json" id="case-data">(.*?)</script>', (demo / "index.html").read_text(), re.S)
    if not embedded or json.loads(embedded[1]) != data:
        findings.append("Demo and public case JSON differ; rebuild the demo")
    if data["completed"] != 5 or len(data["questions"]) != 5:
        findings.append("Public case execution count mismatch")
    for finding in findings:
        print(finding)
    print(f"Community release source gate: {'FAIL' if findings else 'PASS'}")
    return bool(findings)

if __name__ == "__main__":
    sys.exit(main())
