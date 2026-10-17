import json
import unittest
from pathlib import Path

from traclux_probe.cli import parse_responses
from traclux_probe.core import coverage, select, validate_questions


FIXTURE = Path(__file__).resolve().parents[1] / "examples" / "fictional-brand"


class CoreTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.questions = json.loads((FIXTURE / "questions.json").read_text(encoding="utf-8"))["questions"]

    def test_pair_coverage_beats_input_order_on_deliberately_clustered_example(self):
        greedy = select(self.questions, 5)
        baseline = select(self.questions, 5, "input-order")
        self.assertGreater(coverage(greedy), coverage(baseline))
        self.assertEqual(len({q["id"] for q in greedy}), 5)

    def test_illegal_dimension_and_incompatible_pair_rejected(self):
        questions = json.loads(json.dumps(self.questions))
        questions[0]["state"]["D2"] = "made_up"
        with self.assertRaises(ValueError):
            validate_questions(questions)
        questions = json.loads(json.dumps(self.questions))
        questions[0]["state"]["D4"] = "severely_depleted"
        with self.assertRaises(ValueError):
            validate_questions(questions)

    def test_response_provenance_and_unmatched_ids(self):
        responses = parse_responses(FIXTURE / "responses.json", {q["id"] for q in self.questions})
        self.assertEqual(responses["q01"]["entrypoint"], "synthetic-fixture")
        with self.assertRaises(ValueError):
            parse_responses(FIXTURE / "responses.json", {"unrelated"})


if __name__ == "__main__":
    unittest.main()
