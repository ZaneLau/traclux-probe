# 参与贡献

欢迎提交问题、改进文档和贡献代码。当前优先处理部署、模型兼容和问答展示问题，详见[已知限制](docs/community/known-limitations.md)。

反馈请附页面或接口、脱敏输入、预期与实际结果；不要上传 `.env`、会话、客户数据、未获授权的回答或模型密钥。修改前请检查现有服务、composable 和接口约定，尽量复用已有实现。

| 修改内容 | 本地验证 |
| --- | --- |
| Java 后端 | Java 21；`cd backend && ./mvnw -q test package` |
| Nuxt 工作台 | `cd frontend && pnpm -s typecheck && pnpm test && pnpm build` |
| 启动脚本或旧 Python 工具 | `python3 -m unittest discover -s tests` |
| 发布内容检查 | `python3 scripts/check_community_boundary.py` |
| 独立部署 | 按启动文档初始化，再运行 `python3 scripts/community_smoke.py`；此脚本不会调用模型 |

涉及模型调用时，请使用自己的服务商账号，并在 PR 中说明所用模型、测试步骤和观察到的结果。若使用模拟响应，请标明。

提交贡献须确保你有权按本仓的 Apache-2.0 条款提供代码或样本，并保留第三方许可与署名；本仓目前不要求额外 CLA。安全问题请参考 [SECURITY.md](SECURITY.md)。报告旧版 Python 选题器问题时，请注明使用的命令。
