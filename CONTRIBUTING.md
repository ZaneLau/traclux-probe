# 参与贡献

Traclux 社区预览版包含原有用户工作台和 CSSM-HI 生成、执行、分析链路。内部运营后台不在范围内。一个 5 探针真实首轮已完成，部分展示和重跑路径仍有已知限制，见 [验证记录](docs/community/validation.md)。

反馈请附页面或接口、脱敏输入、预期与实际结果；不要上传 `.env`、会话、客户数据、未获授权的回答或模型密钥。改动前先确认已有服务、composable 或合同是否已经负责该行为。

| 修改内容 | 本地验证 |
| --- | --- |
| Java 后端 | Java 21；`cd backend && ./mvnw -q test package` |
| Nuxt 工作台 | `cd frontend && pnpm -s typecheck && pnpm test && pnpm build` |
| 启动脚本或旧 Python 工具 | `python3 -m unittest discover -s tests` |
| 开源边界 | `python3 scripts/check_community_boundary.py` |
| 独立部署 | 按启动文档初始化，再运行 `python3 scripts/community_smoke.py`；此脚本不会调用模型 |

需要模型的验收使用你自己确认可用的配额，不使用作者账号。模拟响应只能证明接线，不能证明生成质量或真实客户端表现。自动检查通过也不代表完整链路已验收。

提交贡献须确保你有权按本仓的 Apache-2.0 条款提供代码或样本，并保留第三方许可与署名；本仓目前不要求额外 CLA。安全问题请参考 [SECURITY.md](SECURITY.md)。旧版选题器的议题需要与工作台问题明确区分。
