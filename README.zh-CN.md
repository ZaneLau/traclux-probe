# Traclux Probe · 探镜社区版

**为 GEO 测试选择问题，看清选择理由，并保留回答证据。**

[Traclux 官网](https://www.traclux.com/) · [English](README.md) · [在线演示](https://zanelau.github.io/traclux-probe/demo/) · [方法与限制](docs/methodology.md)

当你有一批候选问题，但预算只够测试其中一部分时，本工具根据你提供的六维标签选择问题，显示选题覆盖的状态组合，并支持回查导入的答案、来源和时间。

**Python 3.9+，零第三方依赖，无需账户或模型密钥，Apache-2.0。**

如果对你有帮助，欢迎在 [GitHub 留一个 Star](https://github.com/ZaneLau/traclux-probe)。有可复现的问题，或想反馈自己的选题场景，可以[提交 Issue](https://github.com/ZaneLau/traclux-probe/issues)。

![选题对照、问题列表与回答证据](docs/demo/preview.png)

## 先看演示

**[点击打开交互演示 →](https://zanelau.github.io/traclux-probe/demo/)** 无需安装、登录或模型密钥。

也可以下载仓库后，用浏览器打开 [`docs/demo/index.html`](docs/demo/index.html)。全部数据已包含在文件内，可以离线使用。GitHub 上直接点 HTML 会看到源码，需要下载后打开。[详细打开方式与操作动图](docs/demo/README.md)。

- 上方查看相同预算下的两组选题。
- 点选问题，右侧查看选择时新增的覆盖、标签和回答证据。
- 切换“Input order”或“All questions”，可以看到未选中的问题；搜索筛选、JSON 下载均可直接使用。

示例品牌“星屿”、问题和答案全部虚构。五道选题覆盖 72 个状态对，原始顺序的前五题覆盖 30 个；候选池共有 112 个状态对。样例刻意把相似问题排在开头以展示机制，**这个差异不能证明算法效果优越，也不代表真实客户端曝光率**。

## 自己生成同一份报告

在仓库根目录运行：

```bash
python3 -m traclux_probe \
  --questions examples/fictional-brand/questions.json \
  --responses examples/fictional-brand/responses.json \
  --brand 星屿 --budget 5 \
  --output reports/example.json --html reports/example.html
```

用浏览器打开 `reports/example.html` 即可。原有 JSON 报告保留，`--html` 为可选参数。生成、查看都不需要数据库、服务器或模型调用。

## 使用自己的问题

将你有权使用的问题写入 UTF-8 文本，一行一题，逐维人工确认标签：

```bash
python3 -m traclux_probe annotate \
  --input examples/fictional-brand/raw-questions.txt \
  --output reports/my-questions.json
python3 -m traclux_probe --questions reports/my-questions.json \
  --budget 2 --output reports/my-report.json --html reports/my-report.html
```

少量词形匹配只提供未经验证的建议，不会静默推断用户心理状态。也可以直接导入[带六维标签的 JSON](examples/fictional-brand/questions.json)。

回答可按[示例格式](examples/fictional-brand/responses.json)导入，保留问题 ID、入口、来源、带时区的时间、原文和引用；工具不自动采集答案。缺少回答保持未导入，不计作采样。`--brand` 只在 JSON 中进行示例性的字面匹配。

**分享前检查内容：**HTML 包含完整候选池和全部导入回答，方便离线查看未选中的题；页面内下载的 JSON 沿用 CLI 的选中题报告。两种文件都需检查后再分享。

## 开源与商业边界

社区版完整提供基础状态定义、互斥校验、覆盖选点、人工标注、回答导入和离线报告。商业产品可另外提供授权数据、自动领域映射、持续采样、跨入口校准、托管与团队服务。商业服务不是社区版运行的前提。

首个社区版本为 **v0.1.0**，这是独立基础实现，并非完整生产引擎。真实需求上的算法效果尚未验证。详见[更新记录](CHANGELOG.md)、[方法](docs/methodology.md)、[许可证](LICENSE)、[商标政策](TRADEMARK.md)和[安全政策](SECURITY.md)。
