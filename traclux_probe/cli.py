"""Offline, standard-library-only CLI; never sends questions to a provider."""

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

from .core import coverage, select, validate_questions


def read_json(path):
    with Path(path).open(encoding="utf-8") as source:
        return json.load(source)


def write_json(path, data):
    output = Path(path)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_questions(path):
    data = read_json(path)
    if not isinstance(data, dict):
        raise ValueError("questions file must be a JSON object")
    questions = data.get("questions")
    validate_questions(questions)
    return questions


def parse_responses(path, allowed_ids):
    data = read_json(path)
    if not isinstance(data, dict) or not isinstance(data.get("responses"), list):
        raise ValueError("responses file needs a responses list")
    response_map = {}
    for response in data["responses"]:
        if not isinstance(response, dict):
            raise ValueError("each response must be an object")
        identifier = response.get("question_id")
        if not isinstance(identifier, str) or identifier not in allowed_ids or identifier in response_map:
            raise ValueError("unknown or duplicate response question_id")
        for field in ("answer", "entrypoint", "captured_at", "source"):
            if not isinstance(response.get(field), str) or not response[field].strip():
                raise ValueError("response " + str(identifier) + " needs " + field)
        try:
            captured = datetime.fromisoformat(response["captured_at"].replace("Z", "+00:00"))
            if captured.tzinfo is None:
                raise ValueError()
        except ValueError:
            raise ValueError("captured_at requires a timezone-aware ISO date") from None
        citations = response.get("citations")
        if not isinstance(citations, list) or any(not isinstance(c, str) or not c.startswith(("https://", "http://")) for c in citations):
            raise ValueError("citations must be URL strings")
        response_map[identifier] = response
    return response_map


def main(argv=None):
    parser = argparse.ArgumentParser(description="Run an offline CSSM-HI community experiment")
    parser.add_argument("--questions", required=True, help="JSON questions with explicit D1-D6 annotations")
    parser.add_argument("--responses", help="Optional captured answers; no provider requests are made")
    parser.add_argument("--budget", required=True, type=int, help="Maximum number of questions to select")
    parser.add_argument("--output", required=True, help="JSON report path")
    parser.add_argument("--brand", help="Optional exact brand string for illustrative mention detection")
    args = parser.parse_args(argv)
    try:
        questions = parse_questions(args.questions)
        if args.brand is not None and not args.brand.strip():
            raise ValueError("brand must be non-empty")
        responses = parse_responses(args.responses, {q["id"] for q in questions}) if args.responses else {}
        chosen = select(questions, args.budget)
        baseline = select(questions, args.budget, "input-order")
        entries = []
        for q in chosen:
            response = responses.get(q["id"])
            entries.append({"question_id": q["id"], "text": q["text"], "state": q["state"],
                            "annotation_source": q.get("annotation_source", "unspecified"),
                            "response": response, "brand_mentioned": (
                                args.brand.casefold() in response["answer"].casefold()
                                if args.brand and response else None)})
        report = {"schema_version": 1, "method": "community-cssm-hi-greedy-pair-coverage",
                  "generated_at": datetime.now(timezone.utc).isoformat(),
                  "limitations": "Input-order is only a simple baseline; coverage is a design metric, not proof of user demand, client visibility, or causality. Exact brand match is illustrative.",
                  "budget": args.budget, "candidate_count": len(questions),
                  "selected_ids": [q["id"] for q in chosen],
                  "selection_pair_coverage": coverage(chosen),
                  "baseline": {"method": "input-order", "selected_ids": [q["id"] for q in baseline],
                               "pair_coverage": coverage(baseline)},
                  "entries": entries}
        write_json(args.output, report)
        print("selected", len(chosen), "questions; pair coverage", coverage(chosen),
              "vs input-order", coverage(baseline), "; report", args.output)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
