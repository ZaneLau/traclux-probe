# Traclux · 探镜社区版

**用 CSSM-HI 生成探针，接入模型执行，再在现有用户工作台中查看和分析结果。**

[English](README.md) · [启动与功能说明](docs/community/README.md) · [来源清单](docs/community/source-manifest.json) · [安全政策](SECURITY.md)

这个分支是**尚未发布的完整系统候选版**，复用原来的 Spring Boot 后端和 Nuxt 用户工作台。你自己用的运营后台不在开源范围内。当前已发布的 v0.1.0 和静态选题演示仍属于旧范围，不能当作本工作台的演示。

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

已验证构建、原有自动测试、空库迁移、包内提示词加载，以及普通用户接口路径。详细证据见[验证记录](docs/community/validation.md)。

**真实模型的“生成 → 执行 → 有数据的分析页面”仍待验收。** 本轮开发没有已核实的免费额度账号，因此没有发起真实供应商调用。模型端点的兼容性需要逐个核实，不能承诺任意模型直接支持全部能力。API 回答也不能等同于消费端客户端的回答；算法生成的探针不是购买的真实用户搜索日志，当前不宣称已证明算法优越性。

Python 选题器、虚构示例和[旧静态演示](https://zanelau.github.io/traclux-probe/demo/)保留为历史辅助材料，不再代表本项目的主要产品。

项目代码采用 [Apache-2.0](LICENSE)，保留的前端模板、字体等第三方文件遵守各自许可证。问题反馈请提供对应页面、预期行为及脱敏复现，不附带密钥、会话、私有回答或数据库导出。
