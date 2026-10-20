"use strict";
const capturedCase = JSON.parse(document.getElementById("case-data").textContent);
const byId = id => document.getElementById(id);
let selected = 1;
const dimensions = [
  ["D1", "Decision stage · 决策阶段"],
  ["D2", "Goal · 想完成什么"],
  ["D3", "Motivation · 为什么在意"],
  ["D4", "Attention · 还能想多复杂"],
  ["D5", "Urgency · 有多着急"],
  ["D6", "Interaction · 怎样提问"]
];
// Editorial glosses for readers; the saved Chinese tags and codes remain unchanged.
const stateReading = {
  initiation: "Just starting to explore", exploration: "Still searching and uncertain",
  formulation: "Forming comparison criteria", verification: "Checking a choice before buying",
  informational_directed: "Get a specific answer", informational_undirected: "Browse and learn",
  instructional_active: "Find a method or guide", resource_acquisition: "Buy or find a resource",
  subsistence_protection: "Avoid harm and protect devices", affection_belonging_participation: "See what others choose",
  understanding_competence: "Understand how things differ", identity_freedom: "Express a personal preference",
  creation_idleness: "Explore for enjoyment", severely_depleted: "Little attention left",
  moderate_strain: "Some effort is manageable", optimal: "Able to think and compare",
  acute_emergency: "Need it immediately", anticipatory_constraint: "Planning to buy soon",
  reflective: "No hurry", specification: "Compare specific requirements",
  recognition: "Recognize useful options while browsing", dialogic: "Ask through a conversation"
};
function textNode(tag, text, className) {
  const n = document.createElement(tag); n.textContent = text;
  if (className) n.className = className;
  return n;
}
function showDetails(q) {
  selected = q.number;
  byId("detail-title").textContent = `Probe #${q.number} · ${q.status}`;
  byId("question").textContent = q.question;
  byId("question-summary").textContent = `English reading aid: ${q.questionSummaryEn}`;
  const meta = byId("metadata"); meta.replaceChildren();
  for (const [id,label] of dimensions) {
    const value = textNode("dd",stateReading[q.generation.abstractTags[id]] || q.generation.abstractTags[id]);
    value.append(document.createElement("br"),textNode("span",q.generation.translatedTags[id],"small-note"));
    meta.append(textNode("dt",`${id} · ${label}`),value);
  }
  byId("generation-trace").textContent = JSON.stringify(q.generation, null, 2);
  byId("execution-meta").textContent = `Saved model API response · ${capturedCase.model} · ${q.latencySeconds}s · ${capturedCase.capturedAt}`;
  byId("plan-topic").textContent = `Plan topic: ${q.topic}. The plan label and final wording can diverge in this preview.`;
  byId("answer").textContent = q.answerExcerpt || q.publicNote;
  byId("answer-note").textContent = q.answerExcerpt
    ? "Excerpt from the saved response. Model claims are unverified. 原始回答摘录，内容未经事实核验。"
    : "Answer omitted because it contains unsupported brand and safety claims. 问题与执行记录仍可查看。";
  for (const b of byId("questions").querySelectorAll("button")) b.setAttribute("aria-pressed",String(Number(b.dataset.number)===selected));
}
function renderList() {
  const query = byId("search").value.toLocaleLowerCase();
  const rows = capturedCase.questions.filter(q => (q.question+q.topic).toLocaleLowerCase().includes(query));
  const list=byId("questions"); list.replaceChildren();
  for (const q of rows) {
    const button=textNode("button","","question"); button.type="button";
    button.dataset.number=String(q.number); button.setAttribute("aria-pressed",String(q.number===selected));
    const content=textNode("div","");
    content.append(textNode("div",`#${q.number} · ${q.topic}`,"question-top"),textNode("div",q.question,"question-text"));
    button.append(content,textNode("div","✓ Saved","gain"));
    button.addEventListener("click",()=>showDetails(q)); list.append(button);
  }
  if (!rows.length) list.append(textNode("div","No matching probes / 没有匹配的问题","empty"));
  byId("count").textContent=`${rows.length} / ${capturedCase.questions.length}`;
}
byId("search").addEventListener("input",renderList);
renderList(); showDetails(capturedCase.questions[0]);
