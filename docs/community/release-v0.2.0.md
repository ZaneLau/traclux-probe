# Traclux v0.2.0 — community preview

Traclux is now the self-hosted CSSM-HI probe generation and execution system, with a Nuxt workbench and Spring Boot backend.

Start with a brand and category, generate probes, connect your own model, execute them and inspect saved results. The release includes the Java/Spring Boot engine, Nuxt user app, ordinary-user authentication, isolated databases, packaged prompts and Docker Compose setup.

- [Explore the real-model case](https://zanelau.github.io/traclux-probe/demo/) — no signup or API key.
- [Run it locally](https://github.com/ZaneLau/traclux-probe#start-locally).
- [Read the known limitations](known-limitations.md) and [security review](security-review.md).

One Anker / USB-C charger run completed 5/5 probes in 173 seconds and saved five raw responses. The demo includes its generated questions, six-dimensional state tags and response excerpts. Web search was disabled.

This is an early community preview. Grouped question/answer presentation, topic mapping, summary wording, manual re-execution and call accounting have known gaps. Default deployment is local-only; external model usage belongs to your provider account. Do not expose the instance as an untrusted public service.

The earlier Python selection utility remains available; see [selector methodology](../methodology.md).

代码采用 Apache-2.0，包含 CSSM-HI 探针生成、模型执行、用户工作台和后端。可以先打开免登录案例，或按 README 部署。反馈时请提供复现步骤，并移除密钥和个人数据。
