import io
import unittest

from traclux_probe.annotate import annotate_lines
from traclux_probe.core import select


class AnnotationPathTest(unittest.TestCase):
    def test_unseen_questions_require_review_and_become_runnable(self):
        lines = ["今天必须买到旅行杯，有哪些现货选择？", "怎么清理保温杯？"]
        user_choices = io.StringIO("3\n4\n4\n2\n\n1\n2\n\n3\n3\n3\n3\n")
        output = io.StringIO()
        data = annotate_lines(lines, user_choices, output)
        self.assertEqual(len(select(data["questions"], 2)), 2)
        self.assertEqual(data["questions"][0]["state"]["D5"], "acute_emergency")
        self.assertEqual(data["questions"][1]["state"]["D2"], "instructional_active")
        self.assertIn("Enter to confirm", output.getvalue())
        self.assertTrue(all(q["annotation_source"] == "human_confirmed_guided_cli" for q in data["questions"]))

    def test_early_eof_never_silently_fills_dimensions(self):
        with self.assertRaises(ValueError):
            annotate_lines(["未知问题"], io.StringIO("1\n"), io.StringIO())


if __name__ == "__main__":
    unittest.main()
