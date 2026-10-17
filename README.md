# Traclux Probe · 探镜社区实验版

**为品牌候选问题做六维覆盖选点，输出能够追溯到原始回答的离线审计报告。**

本仓是一个独立、可运行的 CSSM-HI **基础实现**。无需账户、数据库、依赖安装或付费模型：Python 3.9+ 即可。样例中的品牌和答案完全虚构；这里的“覆盖”是问题状态组合的设计指标，**不代表消费者在 ChatGPT 或其他客户端的真实曝光率**。

## 一分钟复现

```bash
python3 -m traclux_probe \
  --questions examples/fictional-brand/questions.json \
  --responses examples/fictional-brand/responses.json \
  --brand 星屿 --budget 5 --output reports/example.json
```

打开 `reports/example.json`：`selected_ids` 是六维状态两两覆盖优先选点；`baseline` 是按输入顺序选 5 个问题；`entries` 保留每题状态、原文、来源、入口、时间、原始答案和引用。`response: null` 表示该问题没有导入答案，不计为实际采样。演示样例刻意将相似问题放在开头，以便展示选点差异；**不能证明 CSSM-HI 优于真实用户选题或提高销量**。

该固定样例在预算 5 时为 **72 个状态对 vs 输入顺序的 30 个**。两边的输入问题相同；这个数字仅是代码可复核的演示，不是对真实问题需求或商业效果的估计。

## 使用自己的问题与回答

按 `examples/fictional-brand/questions.json` 导入每个问题的 `id`、`text` 与显式 D1–D6 标签。标签由用户填写，本社区版进行约束校验；生产系统的 LLM 类别翻译并未包含在这里。可按 `responses.json` 格式导入你**有权保存**的回答，要求写明 `question_id`、`answer`、`citations`、`entrypoint`、`captured_at`（带时区）和 `source`。本工具不请求模型、网页或客户端，也不会自动购买或抓取问题数据。`--brand` 仅进行示例性的字面提及匹配。

## 公开与商业边界

社区版包含六维抽象状态、互斥校验、覆盖优先的确定性选点、证据导入和对照报告。完整商业 Traclux 产品可另行提供经授权的真实问题来源、自动领域映射、持续多入口采样、校准、托管与团队服务；这些能力**不是本仓运行的前提**。本仓不是现有 Geo-Probe 后端的等价移植，也不包含完整生产 Node0–Node5 或任何真实客户数据。详见 [方法与限制](docs/methodology.md)。

代码许可：Apache-2.0，见 [LICENSE](LICENSE) 和 [NOTICE](NOTICE)。Traclux/探镜名称及 Logo 规则见 [TRADEMARK.md](TRADEMARK.md)。

English summary: Standalone, offline CSSM-HI six-state pair coverage selection with imported response provenance. Run the example above without API keys; synthetic captures do not represent consumer visibility. See [methodology](docs/methodology.md), [contributing](CONTRIBUTING.md) and [security](SECURITY.md).

## 发布状态

这是尚未对外发布的本地候选。首次公开前，须确认版权归属与商标权、样例和依赖许可，审查拟公开的 Git 提交内容，提供真正可用的私密安全报告渠道，然后才创建 Public 仓库。现有私有后端、前端、官网及其 Git 历史不属于本仓。
