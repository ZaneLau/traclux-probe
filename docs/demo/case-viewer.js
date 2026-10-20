"use strict";
const capturedCase = JSON.parse(document.getElementById("case-data").textContent);
const byId = id => document.getElementById(id);
let selected = 1;
function textNode(tag, text, className) {
  const n = document.createElement(tag); n.textContent = text;
  if (className) n.className = className;
  return n;
}
function showDetails(q) {
  selected = q.number;
  byId("detail-title").textContent = `Probe #${q.number} · ${q.status}`;
  byId("question").textContent = q.question;
  const meta = byId("metadata"); meta.replaceChildren();
  for (const [label,value] of [["Plan topic",q.topic],["Model",capturedCase.model],["Source","Model API · captured, not live"],["Captured",capturedCase.capturedAt],["Latency",`${q.latencySeconds} seconds`]]) {
    meta.append(textNode("dt",label),textNode("dd",value));
  }
  byId("answer").textContent = q.answerExcerpt || q.publicNote;
  byId("answer-note").textContent = q.answerExcerpt
    ? "Exact excerpt from the saved response; omitted sections are not reproduced. Model claims are unverified. 原始回答节录，非完整回答。"
    : "The question and execution status are retained. No replacement answer has been invented.";
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
  byId("count").textContent=`${rows.length} / 5`;
}
byId("search").addEventListener("input",renderList);
renderList(); showDetails(capturedCase.questions[0]);
