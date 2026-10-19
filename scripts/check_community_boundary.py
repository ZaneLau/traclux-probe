"""Check the source package boundary without printing secret-bearing lines."""

import json
from pathlib import Path
import re
import sys


def check(root):
    findings = []
    required = [
        "backend/lombok.config",
        "frontend/app/pages/dashboard.vue",
        "frontend/app/pages/engine.vue",
        "frontend/app/pages/sources.vue",
        "frontend/app/pages/probe-analysis.vue",
        "frontend/app/pages/brands/new.vue",
        "backend/src/main/java/com/geoprobe/service/engine/v42/CssmHiStateSpaceService.java",
        "backend/src/main/java/com/geoprobe/service/engine/v42/ProbeRenderingService.java",
        "backend/src/main/java/com/geoprobe/service/engine/v42/V42Node3SamplingGatewayService.java",
        "backend/src/main/java/com/geoprobe/controller/WorkbenchController.java",
    ]
    for name in required:
        if not (root / name).is_file():
            findings.append(f"Missing user-system component: {name}")
    for component in ["backend/src", "frontend/app", "frontend/server"]:
        for path in (root / component).rglob("*"):
            if not path.is_file():
                continue
            relative = path.relative_to(root)
            if path.name.startswith(("Admin", "useAdmin", "admin-", "ProviderQuotaSnapshot")) or "admin" in relative.parts:
                findings.append(f"Internal console source included: {relative}")
            try:
                content = path.read_text()
            except UnicodeError:
                continue
            if re.search(r'@RequestMapping\(\s*"/api/v1/admin', content):
                findings.append(f"Internal management API included: {relative}")
            if re.search(r"sk-(?:proj-)?[A-Za-z0-9_-]{30,}|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|ep-\d{12,}-[a-z0-9]{5,}", content):
                findings.append(f"Possible credential; inspect privately: {relative}")
    prompt_dir = root / "backend/src/main/resources/prompts/community"
    for name in ["cssm_hi_state_space.json", "probe_rendering.json", "minus_one_context_expansion.json", "brand_detection_l3.json"]:
        path = prompt_dir / name
        if not path.is_file():
            findings.append(f"Missing runtime prompt: {name}")
            continue
        document = json.loads(path.read_text())
        if not all(document.get(key) for key in ["version", "systemMessage", "userMessage"]):
            findings.append(f"Incomplete runtime prompt: {name}")
    return findings


if __name__ == "__main__":
    findings = check(Path(__file__).resolve().parent.parent)
    for finding in findings:
        print(finding)
    print(f"Community source boundary: {'FAIL' if findings else 'PASS'}")
    sys.exit(bool(findings))
