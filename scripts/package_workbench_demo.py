"""Package the static Nuxt workbench for Pages; no server or credentials are shipped."""
import argparse
import base64
import hashlib
from pathlib import Path
import re
import shutil


def package(root, destination):
    source = root / 'frontend/.output/public'
    if not (source / 'index.html').is_file():
        raise SystemExit('Generate the demo frontend first')
    if destination.exists():
        raise SystemExit('Use a fresh output directory')
    shutil.copytree(root / 'docs', destination)
    demo = destination / 'demo'
    # Keep the explanatory case at a secondary URL. The primary demo is Nuxt.
    shutil.copytree(root / 'docs/demo', demo / 'case-study')
    shutil.copytree(source, demo, dirs_exist_ok=True)
    for html in [demo / 'index.html', demo / '200.html', demo / '404.html']:
        text = html.read_text()
        hashes = []
        for attrs, body in re.findall(r'<script([^>]*)>(.*?)</script>', text, re.S):
            if 'src=' not in attrs and body:
                hashes.append("'sha256-" + base64.b64encode(hashlib.sha256(body.encode()).digest()).decode() + "'")
        policy = "default-src 'self'; script-src 'self' " + ' '.join(hashes) + "; connect-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; object-src 'none'; base-uri 'self'; form-action 'none'"
        meta = '<meta http-equiv="Content-Security-Policy" content="' + policy + '"><meta name="referrer" content="no-referrer">'
        text = text.replace('<head>', '<head>' + meta, 1)
        # Nuxt templates must be built explicitly for a read-only demo.
        if not re.search(r'(?:"demoMode"|demoMode):true', text):
            raise SystemExit('Refusing to publish a normal application build as demo')
        html.write_text(text)
    (destination / '.nojekyll').touch()
    print('Static workbench packaged:', destination)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    package(Path(__file__).resolve().parent.parent, args.output.resolve())
