"""Keep the public saved-case export narrow and its viewer disconnected from APIs."""
from html.parser import HTMLParser
import re


CASE_FIELDS = set("schemaVersion brand category capturedAt model status durationSeconds planned completed failed citations webSearch observedVia sampleKind questions generationProvenance".split())
QUESTION_FIELDS = set("number topic question status latencySeconds answerExcerpt excerpted citations publicNote generation questionSummaryEn".split())
GENERATION_FIELDS = set("bucket abstractTags translatedTags renderSource fallbackUsed qualityGatePassed".split())
DIMENSIONS = {f"D{i}" for i in range(1, 7)}


def check_case(data):
    findings = []
    if set(data) - CASE_FIELDS:
        findings.append("Public case has unreviewed top-level fields")
    if any(isinstance(value, (dict, list)) for key, value in data.items() if key != "questions"):
        findings.append("Public case scalar fields must not contain nested payloads")
    for question in data.get("questions", []):
        if set(question) - QUESTION_FIELDS:
            findings.append("Public question has unreviewed fields")
        if any(isinstance(value, (dict, list)) for key, value in question.items() if key not in {"generation", "citations"}):
            findings.append("Public question scalar fields must not contain nested payloads")
        generation = question.get("generation", {})
        if set(generation) != GENERATION_FIELDS:
            findings.append("Public generation trace differs from the reviewed allowlist")
        if any(not isinstance(generation.get(key), str) for key in ("bucket", "renderSource")) or any(type(generation.get(key)) is not bool for key in ("fallbackUsed", "qualityGatePassed")):
            findings.append("Public generation flags must remain simple text/boolean values")
        for key in ("abstractTags", "translatedTags"):
            tags = generation.get(key, {})
            if set(tags) != DIMENSIONS or not all(isinstance(v, str) for v in tags.values()):
                findings.append("Public generation trace must have exactly six text state tags")
        # No nested response/provider payloads are permitted in the citation field.
        # This captured run had web search disabled and zero citations.
        if question.get("citations") != []:
            findings.append("This saved case has no reviewed citations")
    return findings


class ViewerBoundary(HTMLParser):
    def __init__(self):
        super().__init__()
        self.findings = []
        self.policy = {}

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if any(name.startswith("on") for name in values):
            self.findings.append("Public viewer has an inline event handler")
        if tag in {"form", "iframe", "object", "embed", "base"}:
            self.findings.append("Public viewer has an active embedding/submission surface")
        if tag == "meta" and values.get("http-equiv", "").lower() == "content-security-policy":
            self.policy = {parts[0]: parts[1:] for item in values.get("content", "").split(";") if (parts := item.split())}
        if tag == "script" and not (
            re.fullmatch(r"case-viewer\.js\?v=[a-f0-9]{12}", values.get("src", "")) or
            (values.get("type") == "application/json" and values.get("id") == "case-data")
        ):
            self.findings.append("Public viewer has an unreviewed script")


def check_viewer(html):
    parser = ViewerBoundary()
    parser.feed(html)
    for directive, value in {"default-src": ["'none'"], "connect-src": ["'none'"], "form-action": ["'none'"], "base-uri": ["'none'"], "script-src": ["'self'"]}.items():
        if parser.policy.get(directive) != value:
            parser.findings.append(f"Public viewer must preserve {directive} {' '.join(value)}")
    return parser.findings
