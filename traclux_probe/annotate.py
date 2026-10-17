"""Guided human annotation of raw question lines; suggestions are never silently accepted."""

import argparse
import json
import sys
from pathlib import Path

from .core import STATES, validate_questions


def suggestions(text):
    """Conservative string hints; not an intent classifier or a quality claim."""
    hint = {}
    if any(word in text for word in ("怎么", "如何", "how to", "How do")):
        hint["D2"] = "instructional_active"
    if any(word in text for word in ("马上", "急着", "今天必须", "urgent")):
        hint["D5"] = "acute_emergency"
    if any(word in text for word in ("靠谱吗", "到底", "是否", "值不值得")):
        hint["D1"] = "verification"
    return hint


def annotate_lines(lines, input_stream=sys.stdin, output_stream=sys.stdout):
    questions = []
    for line in lines:
        text = line.strip()
        if not text or text.startswith("#"):
            continue
        identifier = f"q{len(questions) + 1:03d}"
        output_stream.write(f"\n{identifier}  {text}\n")
        hints = suggestions(text)
        state = {}
        for dimension, choices in STATES.items():
            while True:
                output_stream.write(f"{dimension}: " + ", ".join(f"{i + 1}={choice}" for i, choice in enumerate(choices)) + "\n")
                suggested = hints.get(dimension)
                instruction = f" [hint: {suggested}; Enter to confirm]" if suggested else " [choose number]"
                output_stream.write(f"{dimension}{instruction}: ")
                output_stream.flush()
                value = input_stream.readline()
                if value == "":
                    raise ValueError("input ended before all dimensions were confirmed")
                value = value.strip()
                if not value and suggested:
                    state[dimension] = suggested
                    break
                if value.isdigit() and 1 <= int(value) <= len(choices):
                    state[dimension] = choices[int(value) - 1]
                    break
                output_stream.write("Choose an offered number; Enter accepts only a displayed hint.\n")
        question = {"id": identifier, "text": text, "state": state,
                    "annotation_source": "human_confirmed_guided_cli"}
        # Fail closed before writing any output when a human chooses a conflict.
        if state["D1"] == "verification" and state["D4"] == "severely_depleted":
            raise ValueError(f"{identifier}: verification is incompatible with severely_depleted; restart annotation")
        if state["D5"] == "acute_emergency" and state["D6"] == "dialogic":
            raise ValueError(f"{identifier}: acute_emergency is incompatible with dialogic; restart annotation")
        questions.append(question)
    validate_questions(questions)
    return {"description": "Human-confirmed labels; rule hints are unvalidated and shown before confirmation.",
            "questions": questions}


def main(argv=None):
    parser = argparse.ArgumentParser(description="Guide a human through six-state annotation of raw questions")
    parser.add_argument("--input", required=True, help="UTF-8 text file; one question per line")
    parser.add_argument("--output", required=True, help="destination for reviewed JSON")
    args = parser.parse_args(argv)
    try:
        lines = Path(args.input).read_text(encoding="utf-8").splitlines()
        result = annotate_lines(lines)
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Saved {len(result['questions'])} human-confirmed questions to {output}")
    except (ValueError, OSError) as exc:
        parser.error(str(exc))


if __name__ == "__main__":
    main()
