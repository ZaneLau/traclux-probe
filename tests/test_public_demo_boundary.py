"""Regressions for accidental private-field exports and live visitor API surfaces."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("public_demo", ROOT / "scripts/check_public_demo.py")
boundary = importlib.util.module_from_spec(spec)
spec.loader.exec_module(boundary)


class PublicDemoBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((ROOT / "docs/demo/case.json").read_text())
        self.html = (ROOT / "docs/demo/index.html").read_text()

    def test_reviewed_artifacts_pass(self):
        self.assertEqual(boundary.check_case(self.data), [])
        self.assertEqual(boundary.check_viewer(self.html), [])

    def test_private_nested_payload_is_rejected_at_each_export_boundary(self):
        for path in [(), ("questions", 0), ("questions", 0, "generation"), ("questions", 0, "generation", "translatedTags")]:
            with self.subTest(path=path):
                data = copy.deepcopy(self.data)
                target = data
                for key in path:
                    target = target[key]
                target["privateProviderPayload"] = {"credential": "harmless-test-value"}
                self.assertTrue(boundary.check_case(data))

    def test_live_connections_forms_and_external_scripts_are_rejected(self):
        for html in [
            self.html.replace("connect-src 'none'", "connect-src 'self'"),
            self.html + '<form action="/api/trial"></form>',
            self.html + '<script src="https://example.invalid/trial.js"></script>',
            self.html.replace("script-src 'self'", "script-src 'self' 'unsafe-inline'"),
        ]:
            with self.subTest():
                self.assertTrue(boundary.check_viewer(html))

    def test_known_field_cannot_be_used_to_smuggle_a_provider_payload(self):
        self.data["questions"][0]["generation"]["renderSource"] = {"credential": "harmless-test-value"}
        self.assertTrue(boundary.check_case(self.data))


if __name__ == "__main__":
    unittest.main()
