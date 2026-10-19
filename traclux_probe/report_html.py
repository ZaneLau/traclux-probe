"""Self-contained report, with no network requests or external assets."""

import json
from itertools import combinations
from pathlib import Path

from .core import STATES, pair_set


def render_html(report, questions, responses, brand=None, description=""):
    """Keep the JSON report contract intact; enrich only the HTML view model."""
    chosen = {identifier: index + 1 for index, identifier in enumerate(report["selected_ids"])}
    baseline_ids = set(report["baseline"]["selected_ids"])
    by_id = {question["id"]: question for question in questions}
    covered = set()
    contributions = {}
    for identifier in report["selected_ids"]:
        pairs = pair_set(by_id[identifier])
        contributions[identifier] = [list(pair) for pair in sorted(pairs - covered)]
        covered.update(pairs)
    available = set().union(*(pair_set(question) for question in questions))
    baseline_pairs = set().union(*(pair_set(by_id[identifier]) for identifier in baseline_ids))
    cells = []
    for first, second in combinations(STATES, 2):
        subset = {pair for pair in available if pair[0] == first and pair[2] == second}
        cells.append({"first": first, "second": second, "available": len(subset),
                      "selected": len(subset & covered), "baseline": len(subset & baseline_pairs)})
    candidates = [{**question, "step": chosen.get(question["id"]),
                   "baseline": question["id"] in baseline_ids,
                   "new_pairs": contributions.get(question["id"], []),
                   "response": responses.get(question["id"])} for question in questions]
    view = {"report": report, "candidates": candidates, "cells": cells,
            "available_pairs": len(available), "brand": brand, "description": description}
    # Raw user input must never terminate the inert JSON script element.
    payload = json.dumps(view, ensure_ascii=False).replace("&", "\\u0026").replace("<", "\\u003c").replace(">", "\\u003e")
    template = Path(__file__).with_name("report_template.html").read_text(encoding="utf-8")
    return template.replace("__TRACLUX_REPORT_DATA__", payload)
