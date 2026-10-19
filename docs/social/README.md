# Sharing Traclux Probe

Use [preview.png](preview.png) when sharing the repository. It is a 1280 × 640 PNG, below 1 MB, using the project's existing colors and the public fictional-demo screenshot. It contains no customer data or credentials. The image is also the repository's GitHub social preview.

- Repository: https://github.com/ZaneLau/traclux-probe
- Interactive demo: https://zanelau.github.io/traclux-probe/demo/
- Website: https://www.traclux.com/

## What it does

Traclux Probe selects a test set from questions with human-supplied six-dimensional labels. It shows state-pair coverage and selection contributions, preserves imported answer evidence, and exports self-contained HTML and JSON reports.

Python 3.9+, standard library only, Apache-2.0. No account, API key, model call or server is needed. The tool does not capture answers or measure consumer-client visibility. The fictional demo illustrates its mechanism; it is not an effectiveness benchmark.

## 中文作品介绍

标题：开源了一个 GEO 选题工具：从候选问题里挑选测试集，并生成离线报告

做 GEO / AI 搜索测试时，经常会遇到一个问题：手里有很多候选问题，但预算只能测试一部分，该怎么选？

我把项目里一个独立的小工具整理成了 Traclux Probe 社区版。你提供问题和六维标签，它根据状态对覆盖选择测试集，展示每道题增加了哪些覆盖；已有的回答可以导入，保留来源、时间和引用，最后生成可以离线打开的 HTML 和 JSON 报告。

它使用 Python 标准库，不需要账户、模型密钥或付费服务。可以先打开上面的在线演示，再下载仓库复现。

目前是基础实现：标签需要人工提供，回答需要自行导入；它不自动采集客户端答案，也不保证品牌排名提升。演示品牌和数据全部虚构，用来说明机制，不是算法胜出的证明。

欢迎反馈实际选题场景或可复现的问题。如果这个工具对你有帮助，也欢迎在 GitHub 留一个 Star。

## English product facts

- Problem: choosing which questions to test when a candidate pool exceeds the testing budget.
- Input: question text and six human-supplied state labels; captured answers are optional.
- Output: a deterministic greedy selection, inspectable pair-coverage contributions, and offline HTML/JSON reports.
- Try it without signup at the demo URL above; reproduce it with the README command.
- Limits: labels and provenance are supplied records, no automatic answer acquisition, no validated real-demand performance claim.

These are reference facts, not a generated Show HN submission. [HN's rules](https://news.ycombinator.com/newsguidelines.html) require authors to write their own posts and replies and prohibit generated or AI-edited text, automated posting, and soliciting votes or comments. Follow each community's rules; identify your connection to the project.

Share only in communities that allow project introductions. Do not offer rewards for Stars or organize artificial engagement.
