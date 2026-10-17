"""Explicit six-dimensional state coding and coverage-first selection.

This is a standalone community implementation, not a copy of the production
Node0/Node1 orchestration or a claim of equivalent consumer visibility.
"""

from itertools import combinations

STATES = {
    "D1": ("initiation", "exploration", "formulation", "verification"),
    "D2": ("informational_directed", "informational_undirected", "instructional_active", "resource_acquisition"),
    "D3": ("subsistence_protection", "affection_belonging_participation", "understanding_competence", "identity_freedom", "creation_idleness"),
    "D4": ("severely_depleted", "moderate_strain", "optimal"),
    "D5": ("acute_emergency", "anticipatory_constraint", "reflective"),
    "D6": ("specification", "recognition", "dialogic"),
}


def validate_questions(questions):
    if not isinstance(questions, list) or not questions:
        raise ValueError("questions must be a non-empty list")
    seen = set()
    for question in questions:
        if not isinstance(question, dict) or not isinstance(question.get("id"), str) or not question["id"].strip():
            raise ValueError("each question needs a non-empty string id")
        if question["id"] in seen:
            raise ValueError("duplicate question id: " + question["id"])
        seen.add(question["id"])
        if not isinstance(question.get("text"), str) or not question["text"].strip():
            raise ValueError("question " + question["id"] + " needs text")
        state = question.get("state")
        if not isinstance(state, dict) or set(state) != set(STATES):
            raise ValueError("question " + question["id"] + " must declare exactly D1-D6")
        for dimension, choices in STATES.items():
            if state[dimension] not in choices:
                raise ValueError("invalid state " + dimension + " for " + question["id"])
        if state["D1"] == "verification" and state["D4"] == "severely_depleted":
            raise ValueError("incompatible D1/D4 states for " + question["id"])
        if state["D5"] == "acute_emergency" and state["D6"] == "dialogic":
            raise ValueError("incompatible D5/D6 states for " + question["id"])


def pair_set(question):
    state = question["state"]
    return {(a, state[a], b, state[b]) for a, b in combinations(STATES, 2)}


def select(questions, budget, strategy="cssm-hi"):
    validate_questions(questions)
    if not isinstance(budget, int) or isinstance(budget, bool) or not (1 <= budget <= len(questions)):
        raise ValueError("budget must be an integer from 1 to the question count")
    if strategy == "input-order":
        return questions[:budget]
    if strategy != "cssm-hi":
        raise ValueError("strategy must be cssm-hi or input-order")
    # Deterministic greedy maximization of newly covered state pairs. This
    # priority is the core objective; ties preserve input order for auditability.
    remaining = list(questions)
    selected = []
    covered = set()
    for _ in range(budget):
        choice = max(remaining, key=lambda q: len(pair_set(q) - covered))
        remaining.remove(choice)
        selected.append(choice)
        covered.update(pair_set(choice))
    return selected


def coverage(questions):
    return len(set().union(*(pair_set(q) for q in questions))) if questions else 0
