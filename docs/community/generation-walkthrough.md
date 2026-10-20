# From a brand to a probe / 从品牌到探针

[Open the saved case](https://zanelau.github.io/traclux-probe/demo/#explore) · [Public case JSON](../demo/case.json) · [Run your own instance](README.md)

CSSM-HI asks: **what would people in different decision situations ask about this category?** Traclux describes those situations with six state dimensions, plans combinations, generates natural questions, then runs them against a configured model. The workbench stores the questions and answers for inspection.

通俗说：同样买充电器，有的人刚开始了解，有的人正在比较，有的人下单前只想再确认一下。CSSM-HI 用六个维度描述这些差异，选出组合，再生成问句并交给模型回答。生成的问题用于探测模型回答。

## Follow one actual probe / 看一条真实探针

Input / 输入：**Anker · USB-C充电器**，比较对象包含 **UGREEN、Baseus**。下表来自 2026-10-20 已完成运行的第一条问题；英文解释是阅读辅助，原始中文标签和编码保留在 JSON 中。

| Dimension | Saved code | Saved category wording / 保存的品类表达 | English reading aid |
| --- | --- | --- | --- |
| D1 · Decision stage / 决策阶段 | `verification` | 核对看中的C充靠不靠谱 | Checking a choice before committing |
| D2 · Goal / 目标 | `resource_acquisition` | 就想赶紧买到合适的C充 | Trying to buy a suitable charger |
| D3 · Motivation / 动机 | `subsistence_protection` | 要安全别充坏我的设备 | Protecting devices from harm |
| D4 · Attention / 注意力 | `optimal` | 思路清晰选得很顺畅 | Able to think and compare |
| D5 · Urgency / 时间 | `acute_emergency` | 急着用马上就要买到 | Needs it immediately |
| D6 · Interaction / 交互 | `specification` | 对着参数详情挨个比对 | Comparing specific details |

The stored generated question / 实际生成的问题：

> 下单前想确认下，Anker的USB-C充电器和UGREEN比，直接拿哪个更稳不容易踩坑？

English gist: “Before ordering, should I choose Anker or UGREEN for a USB-C charger?”

The record shows `ORTHOGONAL` planning, `LLM` rendering, no fallback and a passed quality gate. Human review is still useful: the question, for example, does not explicitly express immediate urgency.

这条记录属于 `ORTHOGONAL` 桶，由模型生成，未使用回退，质量检查通过。仍可人工检查表达是否到位：例如问句没有明确说“马上要用”。

The question was executed against **doubao-seed-2-0-lite-260428** through its API. Its response was saved; the demo shows a reviewed exact excerpt and **57.5 seconds** sampling latency. The full five-probe run took **173 seconds**, including generation and downstream processing. Web search was off; there were no citations.

## Compare three situations / 再比较三种问法

The following questions and tags come from the same saved run.

| Probe | Saved D1 / D2 / D3 | What the resulting question asks / 实际问法 |
| --- | --- | --- |
| #1 | `verification` / `resource_acquisition` / `subsistence_protection` | Confirm Anker vs UGREEN before ordering / 下单前确认哪个更稳 |
| #2 | `formulation` / `instructional_active` / `understanding_competence` | Find a method for comparing UGREEN and Baseus / 想弄清比较思路与差别 |
| #5 | `initiation` / `informational_undirected` / `affection_belonging_participation` | Explore what others usually consider first / 刚开始了解，大家一般先看什么 |

Select these questions in the [public demo](https://zanelau.github.io/traclux-probe/demo/#explore) to see all six tags and answer excerpts. One response body (#4) is withheld because of unverified brand/safety claims; its question, generation tags and successful execution record remain visible.

## Where the implementation lives / 源码在哪里

The engine code is in `backend/src/main/java/com/geoprobe/service/engine/v42/`.

| Stage | What happens / 做什么 | Open implementation |
| --- | --- | --- |
| Fixed states | Six axes have fixed abstract codes; the model does not invent new axes / 六维抽象编码固定 | [CssmHiAbstractStates](../../backend/src/main/java/com/geoprobe/service/engine/v42/CssmHiAbstractStates.java) |
| Category wording | Translate abstract codes into category-specific states, validate the pool, retain deterministic fallback / 翻译为品类表达并校验 | [CssmHiStateSpaceService](../../backend/src/main/java/com/geoprobe/service/engine/v42/CssmHiStateSpaceService.java) |
| Blueprint planning | Choose combinations within the task budget, account for coverage and exclusions, plan buckets / 按任务规模做覆盖选点与约束 | [OrthogonalMatrixService](../../backend/src/main/java/com/geoprobe/service/engine/v42/OrthogonalMatrixService.java), [ProbeBlueprint](../../backend/src/main/java/com/geoprobe/dto/engine/v42/ProbeBlueprint.java) |
| Question generation | Render natural wording while retaining tags and generation metadata / 渲染自然问句并保留状态 | [ProbeRenderingService](../../backend/src/main/java/com/geoprobe/service/engine/v42/ProbeRenderingService.java), [prompts](../../backend/src/main/resources/prompts) |
| Quality checks | Inspect generated batches and identify repair targets / 检查批次质量与修复目标 | [ProbeQualityGateService](../../backend/src/main/java/com/geoprobe/service/engine/v42/ProbeQualityGateService.java) |
| Execute and store | Dispatch to the configured model and persist records / 调用自配模型并保存 | [V42Node3SamplingGatewayService](../../backend/src/main/java/com/geoprobe/service/engine/v42/V42Node3SamplingGatewayService.java), [persistence](../../backend/src/main/java/com/geoprobe/service/DiagnosisTaskRuntimePersistenceService.java) |
| User workbench | Brand setup, plans, results, citations, analysis and history / 完整用户工作台 | [frontend source](../../frontend/app), [feature and route map](README.md#user-features) |

The planner supports `ORTHOGONAL`, `PARETO` and `ADVERSARIAL` buckets. This case contains five `ORTHOGONAL` probes. The JSON includes state tags and rendering flags; it does not include candidate rankings or the complete planning trace.

规划器包含三个桶，此案例的五条问题均属于 `ORTHOGONAL`。JSON 中可查看状态和生成标记。覆盖程度与算法效果需要另做对照实验。

## Inspect without a model account / 不接模型也能检查

```bash
git clone https://github.com/ZaneLau/traclux-probe.git
cd traclux-probe
python3 -m json.tool docs/demo/case.json
```

No installation beyond Python, model call or database is needed to read the saved artifact. To generate **new** probes, use the [self-hosting guide](README.md) and your own model account. Check API compatibility, quota and pricing with your provider.

## Case data / 案例数据

- **Recorded fields:** question text, `abstractTags`, `translatedTags`, bucket, render source, fallback and quality flags come from saved probe records.
- **Reading aids:** English question summaries and plain-language dimension names are editorial explanations. They do not replace original Chinese questions, stored category translations or model answers.
- **Demo:** the static page displays saved data. Searching and switching questions make no model calls. Implementation details are in the [demo guide](../demo/README.md).
- **Interpretation:** this five-probe case illustrates the workflow. API results may differ from consumer-app answers, and topic labels may not match every generated question. See [known limitations](known-limitations.md).

演示无需注册或 Key，只展示已保存的案例。生成新问题请自部署，并使用自己的模型服务。
