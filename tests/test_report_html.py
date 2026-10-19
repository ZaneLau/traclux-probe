import contextlib
import io
import json
import re
import tempfile
import unittest
from pathlib import Path

from traclux_probe.cli import main
from traclux_probe.core import coverage
from traclux_probe.report_html import render_html


FIXTURE = Path(__file__).resolve().parents[1] / "examples" / "fictional-brand"


def view_model(html):
    return json.loads(re.search(r'<script id="report-data" type="application/json">(.*?)</script>', html, re.S)[1])


class HtmlReportTest(unittest.TestCase):
    def run_report(self, directory, responses=True):
        args = ["--questions", str(FIXTURE / "questions.json"), "--budget", "5",
                "--output", str(directory / "report.json"), "--html", str(directory / "index.html")]
        if responses:
            args += ["--responses", str(FIXTURE / "responses.json")]
        with contextlib.redirect_stdout(io.StringIO()):
            main(args)
        return json.loads((directory / "report.json").read_text()), (directory / "index.html").read_text()

    def test_report_reconstructs_real_selection_gains_and_preserves_json(self):
        with tempfile.TemporaryDirectory() as temp:
            report, html = self.run_report(Path(temp))
        data = view_model(html)
        self.assertEqual(data["report"], report)
        questions = data["candidates"]
        selected = sorted((q for q in questions if q["step"]), key=lambda q: q["step"])
        self.assertEqual([q["id"] for q in selected], report["selected_ids"])
        previous = 0
        for index, q in enumerate(selected):
            current = coverage(selected[:index + 1])
            self.assertEqual(len(q["new_pairs"]), current - previous)
            previous = current
        self.assertEqual(sum(cell["selected"] for cell in data["cells"]), report["selection_pair_coverage"])
        self.assertEqual(sum(cell["baseline"] for cell in data["cells"]), report["baseline"]["pair_coverage"])
        self.assertEqual(data["available_pairs"], coverage(questions))
        self.assertEqual([q["id"] for q in questions if q["baseline"]], report["baseline"]["selected_ids"])
        self.assertIsNone(next(q for q in questions if q["id"] == "q02")["response"])

    def test_no_imported_answers_stay_missing_and_hostile_text_is_inert(self):
        with tempfile.TemporaryDirectory() as temp:
            report, html = self.run_report(Path(temp), responses=False)
        data = view_model(html)
        self.assertTrue(all(q["response"] is None for q in data["candidates"]))
        hostile = '</script><script>window.injected=true</script>&<img src=x onerror=alert(1)>'
        data["candidates"][0]["text"] = hostile
        response = {"question_id": "q01", "answer": hostile, "citations": [],
                    "entrypoint": "fixture", "source": "test", "captured_at": "2026-10-17T00:00:00Z"}
        html = render_html(report, data["candidates"], {"q01": response}, hostile, hostile)
        self.assertNotIn(hostile, html)
        decoded = view_model(html)
        self.assertEqual(decoded["candidates"][0]["text"], hostile)
        self.assertEqual(decoded["candidates"][0]["response"]["answer"], hostile)
        self.assertEqual(decoded["description"], hostile)
        self.assertEqual(decoded["brand"], hostile)
        self.assertIn("connect-src 'none'", html)

    def test_html_cannot_overwrite_json(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "report.json"
            output.write_text("existing report")
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit):
                main(["--questions", str(FIXTURE / "questions.json"), "--budget", "5",
                      "--output", str(output), "--html", str(output)])
            self.assertEqual(output.read_text(), "existing report")


if __name__ == "__main__":
    unittest.main()
