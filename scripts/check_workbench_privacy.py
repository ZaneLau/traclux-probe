"""Check public snapshot boundaries; findings never include matched values."""
import json
from pathlib import Path
import re
from urllib.parse import urlsplit

BRAND = '/brand-spaces/demo-anker'
ROUTES = {
    '/brand-spaces', '/brand-spaces/archived',
    *(BRAND + suffix for suffix in ['/basic-profile', '/config', '/probe-plan', '/runs', '/runs/demo-run-20261020']),
    *('/workbench/' + name for name in ['overview', 'brand-performance', 'citation-sources', 'problem-analysis']),
}
FORBIDDEN_FIELD = re.compile(r'token|password|secret|cookie|api.?key|endpoint|credit|balance|billing|account|organization|membership|tenant|(?:user|owner)(?:Id|name)|email|phone|createdBy|updatedBy|rawRecord|taskOrder|taskMetadata|taskPayload|truthSource|sourceWindowKey', re.I)
PRIVATE_TEXT = re.compile(r'\b[\w.+-]+@[\w.-]+\.[a-z]{2,}\b|(?<!\d)1[3-9]\d{9}(?!\d)|/Users/|/home/|\b(?:localhost|127\.0\.0\.1)\b|\b[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}\b|sk-(?:proj-)?[\w-]{30,}|ep-\d{12,}-[a-z0-9]{5,}|-----BEGIN .*PRIVATE KEY-----|积分|余额|公测|测试账号|测试用户|测试数据', re.I)


def check_snapshot(data, approved_case):
    findings = []
    if set(data) != ROUTES:
        findings.append('Snapshot contains an unreviewed projection route')
    approved_answers = {q.get('answerExcerpt') or '此回答未在公开案例中展示。' for q in approved_case['questions']}

    def visit(value, path='snapshot'):
        if isinstance(value, dict):
            for key, item in value.items():
                if FORBIDDEN_FIELD.search(key):
                    findings.append(f'Private account/configuration field at {path}')
                if key in {'rawAnswer', 'answerPreview'} and item and item not in approved_answers:
                    findings.append(f'Unreviewed answer at {path}')
                visit(item, path + '.' + key)
        elif isinstance(value, list):
            for item in value:
                visit(item, path + '[]')
        elif isinstance(value, str):
            if PRIVATE_TEXT.search(value):
                findings.append(f'Private identifier or obsolete test/account text at {path}')
            for url in re.findall(r'https?://[^\s<>"\']+', value):
                parsed = urlsplit(url)
                if parsed.scheme != 'https' or parsed.netloc != 'www.anker.com' or parsed.query or parsed.fragment:
                    findings.append(f'Unreviewed URL at {path}')
    visit(data)
    return findings


def check(root: Path):
    return check_snapshot(json.loads((root / 'frontend/app/demo/workbench.json').read_text()), json.loads((root / 'docs/demo/case.json').read_text()))
