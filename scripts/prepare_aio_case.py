"""Download pinned AIO benchmark metadata into ignored local reports only.

Does not redistribute upstream question text in the community repository.
"""

import csv
import hashlib
import io
import json
from pathlib import Path
from urllib.request import Request, urlopen

REVISION = "58f57d7db8c932bc67dcd9b94474db3223d65452"
EXPECTED_SHA256 = "653ef0387f8467abed0db9bf3acdbfde5dc6c83ff0448a993608d8cc05c0029d"
URL = f"https://raw.githubusercontent.com/rag24/AIO/{REVISION}/data/AIO_Benchmark_Dataset_v1.csv"
# 20 manually reviewed retail base groups; see docs/benchmark-sources.md.
BASE_IDS = (2, 3, 5, 7, 12, 13, 14, 16, 23, 24, 27, 30, 34, 37, 39, 43, 45, 46, 48, 50)
OUTPUT_DIR = Path("reports/aio-benchmark")


def download_pinned_csv():
    request = Request(URL, headers={"User-Agent": "traclux-probe-benchmark-preparer/0.1"})
    with urlopen(request, timeout=20) as response:
        raw = response.read(3_000_001)
    if len(raw) > 3_000_000 or hashlib.sha256(raw).hexdigest() != EXPECTED_SHA256:
        raise ValueError("upstream CSV exceeds limit or differs from pinned SHA256; no output written")
    return list(csv.DictReader(io.StringIO(raw.decode("utf-8-sig"))))


def prepare(rows):
    lookup = {int(row["ID"]): row for row in rows}
    if len(rows) != 11_500 or len(lookup) != len(rows):
        raise ValueError("unexpected upstream IDs/row count")
    manifest = []
    texts = []
    for base_id in BASE_IDS:
        for offset, expected_group in ((0, "Amazon Retail"), (500, "Amazon Retail Q"), (1000, "Amazon Retail Comp")):
            row = lookup[base_id + offset]
            if row["dataset"] != expected_group or not row["query"].strip():
                raise ValueError("unexpected upstream group or blank query")
            manifest.append({"id": base_id + offset, "group": expected_group, "base_id": base_id})
            if offset:
                texts.append(row["query"].strip())
    return manifest, texts


def main():
    manifest, texts = prepare(download_pinned_csv())
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    (OUTPUT_DIR / "questions.txt").write_text("\n".join(texts) + "\n", encoding="utf-8")
    (OUTPUT_DIR / "source-manifest.json").write_text(json.dumps({
        "source": URL, "sha256": EXPECTED_SHA256, "revision": REVISION,
        "license_note": "Upstream aggregate license does not resolve all underlying text rights; do not commit downloaded questions.",
        "selected": manifest}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Prepared {len(texts)} generated question variants from {len(BASE_IDS)} retail groups in {OUTPUT_DIR}; no answers or CSSM-HI labels")


if __name__ == "__main__":
    main()
