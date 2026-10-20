import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('privacy', ROOT / 'scripts/check_workbench_privacy.py')
privacy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(privacy)


class WorkbenchPrivacyTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / 'frontend/app/demo/workbench.json').read_text())
        self.case = json.loads((ROOT / 'docs/demo/case.json').read_text())

    def test_reviewed_snapshot_passes(self):
        self.assertEqual(privacy.check_snapshot(self.data, self.case), [])

    def test_rejects_hidden_account_fields_and_unreviewed_routes(self):
        self.data['/workbench/overview']['extra'] = {'email': 'guest@example.invalid', 'serviceAccessStatus': 'trial', 'quotaLimited': True}
        self.data['/runs/unreviewed/events'] = []
        findings = privacy.check_snapshot(self.data, self.case)
        self.assertGreaterEqual(len(findings), 4)
        self.assertNotIn('guest@example.invalid', str(findings))

    def test_rejects_private_content_even_in_display_fields(self):
        for value in ['guest@example.invalid', '13800138000', '/Users/example/private', '00000000-0000-4000-8000-000000000001', 'https://internal.example.invalid/report', '公测积分不足']:
            with self.subTest(value=value):
                candidate = copy.deepcopy(self.data)
                candidate['/workbench/overview']['description'] = value
                self.assertTrue(privacy.check_snapshot(candidate, self.case))

    def test_rejects_unreviewed_answer_without_echoing_it(self):
        candidate = copy.deepcopy(self.data)
        candidate['/workbench/overview']['extra'] = {'rawAnswer': 'Unreviewed response fixture'}
        findings = privacy.check_snapshot(candidate, self.case)
        self.assertTrue(findings)
        self.assertNotIn('Unreviewed response fixture', str(findings))
