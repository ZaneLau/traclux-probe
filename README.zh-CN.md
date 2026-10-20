# Traclux · 探镜社区版

**用 CSSM-HI 生成探针，接入模型执行，再在现有用户工作台中查看和分析结果。**

[**免 Key 真实案例演示**](https://zanelau.github.io/traclux-probe/demo/) · [English](README.md) · [启动与功能说明](docs/community/README.md) · [来源清单](docs/community/source-manifest.json) · [安全政策](SECURITY.md)

这是 **v0.2.0 社区预览版**，开源已有 Spring Boot 后端和 Nuxt 用户工作台，采用 Apache-2.0。作者自己用的运营后台保持私有。你配置自己的模型账号即可运行。

[![真实探针案例：点选问题查看回答摘录](docs/demo/preview.png)](https://zanelau.github.io/traclux-probe/demo/)

先打开[只读案例](https://zanelau.github.io/traclux-probe/demo/)，无需注册或 API Key，就能看这次实际生成的问题、执行状态和回答摘录。它是公开案例查看器；完整用户工作台在你自己的实例中运行。

## CSSM-HI 的特色：同一个品牌，不同状态的人会怎样问？

临下单的人、怕充坏设备的人、刚开始了解充电器的人，需要问的问题不一样。Traclux 先描述用户的**六种状态**，选择状态组合，再让模型生成自然问句。生成时用过哪些状态，可以和最后的问题一起查看。

```text
Anker · USB-C 充电器
  → 正在核对选择 + 想买到产品 + 担心设备安全
  → 有精力比较 + 急着用 + 按参数细节提问
  → “下单前想确认下，Anker的USB-C充电器和UGREEN比，直接拿哪个更稳不容易踩坑？”
  → 接入自己的模型执行 → 保存回答 → 在工作台查看
```

这条问句和六维状态来自**同一次已保存的真实运行**，并非事后编排的示意例子。状态是生成目标；最终问句不一定完整表达每个目标，例如这句并没有明确说出“马上要用”。

| 状态 | 通俗含义 | 这条探针保存的实际标签 |
| --- | --- | --- |
| D1 · 决策阶段 | 已经了解到哪一步？ | 核对看中的 C 充靠不靠谱 |
| D2 · 目标 | 想完成什么？ | 就想赶紧买到合适的 C 充 |
| D3 · 动机 | 为什么在意？ | 要安全，别充坏设备 |
| D4 · 注意力 | 还能想多复杂？ | 思路清晰，可以比较 |
| D5 · 时间 | 有多着急？ | 急着用，马上要买到 |
| D6 · 交互 | 怎样提问？ | 对着参数详情挨个比对 |

**建议先点演示里的 [#1、#2、#5](https://zanelau.github.io/traclux-probe/demo/#explore)**：分别看看临下单确认、找比较方法、刚开始了解时的问题有何不同。右侧会一起切换实际状态标签和回答摘录，还能展开原始编码或[下载公开 JSON](docs/demo/case.json)。

[生成过程与源码导读](docs/community/generation-walkthrough.md)把固定状态、品类翻译、覆盖选点、问句生成与质量检查对应到具体代码。这些实现都已开源；五条样例用于解释生成过程，不证明算法优于其他方法。

**演示不消耗任何模型额度。** 浏览、搜索和切换问题都只读取本页保存的数据，没有在线试跑或作者代付接口。要生成新问题，请自部署并接自己的模型；服务商可能收取调用费用。

| 用户要做的事 | 开源内容 |
| --- | --- |
| 配置品牌 | 品牌识别、品牌空间、主题、固定问题及设置 |
| 生成探针方案 | CSSM-HI 状态建模、覆盖选点、问句生成、质量控制 |
| 执行探针 | 方案确认、手动执行、模型适配、回答保存及失败记录 |
| 分析结果 | 可见度总览、品牌表现、引用来源、问题分析、已有溯源与解释入口 |
| 跟踪执行 | 运行进度、事件、历史批次与详情 |
| 独立部署 | 普通用户认证、数据访问隔离、MySQL、Redis、知识资产 PostgreSQL、Docker Compose |

内部 `/admin` 页面和管理接口、用户运营管理、管理重跑、计费配置、供应商额度管理，以及作者账号、密钥和线上数据保留私有。用户执行所依赖的内部计量仍保留；本地初始化的额度不要求向 Traclux 购买订阅。

## 本地启动

需要 Docker Compose 和 Python 3；构建使用 Java 21 和仓库锁定的 Nuxt 依赖。

```bash
git clone https://github.com/ZaneLau/traclux-probe.git
cd traclux-probe
python3 scripts/community_init.py
# 私下编辑 .env 中的 MODEL_BASE_URL、MODEL_API_KEY、MODEL_NAME。
docker compose up --build -d
```

打开 **http://localhost:3100/login**。用户名和随机密码见你自己生成的 `.env` 中的 `COMMUNITY_USERNAME` / `COMMUNITY_PASSWORD`。不要提交或分享此文件。默认模型地址为本机关闭的端口，不会调用作者账号。可用 `SAMPLING_*` 单独配置被测模型；具体适配限制见[启动说明](docs/community/README.md)。

```bash
python3 scripts/community_smoke.py
docker compose stop
```

检查脚本只验证登录、用户接口与管理接口不可用，不调用模型。社区配置默认关闭后台定时执行和供应商联网搜索；手动执行同样会消耗你自己的模型额度。

## 当前验证到哪里

已完成一个 Anker / USB-C 充电器真实案例：CSSM-HI 建模和生成、模型执行、5/5 回答保存、有数据的总览/品牌表现/运行详情。首轮耗时 173 秒。登录表单、空库迁移及构建检查也已验证。详见[验证记录](docs/community/validation.md)。

这是早期版本，[已知限制](docs/community/known-limitations.md)包括问答分组展示、主题关联、汇总表达、手动重跑和调用计量。默认只监听本机，不应直接作为公开多用户服务；[安全检查记录](docs/community/security-review.md)逐项说明检查范围和剩余告警。

本轮联网关闭、引用为 0；API 回答不能等同于消费端客户端回答。探针不是购买的真实用户搜索日志；本例不证明算法优于其他方法，也不足以判断品牌排名。模型用量与费用来自你自己的服务商账号。

![真实案例查看短动图](docs/demo/walkthrough.gif)

Python 选题器、虚构示例和[v0.1.0 历史演示](https://github.com/ZaneLau/traclux-probe/tree/v0.1.0/docs/demo)保留为历史辅助材料，不再代表本项目的主要产品。

项目代码采用 [Apache-2.0](LICENSE)，保留的前端模板、字体等第三方文件遵守各自许可证。问题反馈请提供对应页面、预期行为及脱敏复现，不附带密钥、会话、私有回答或数据库导出。

欢迎先试案例，再反馈部署与模型兼容情况。如果它对你有帮助，也欢迎在 GitHub 留一个 Star。
