# GEO 问题测评来源：首轮选择

**首选**：[SIGIR 2026 AIO 作者仓](https://github.com/rag24/AIO)的 [11,500 条搜索测评 CSV](https://github.com/rag24/AIO/blob/58f57d7db8c932bc67dcd9b94474db3223d65452/data/AIO_Benchmark_Dataset_v1.csv)。其中有 500 条真实 Amazon 零售**搜索短词**，以及分别基于它们经 Gemini 2.5 Flash 生成的 500 条购物问题和 500 条比较问题。[原作者说明](https://github.com/rag24/AIO#benchmark-query-dataset)。后两组不是未经改写的消费者提问。

| 来源 | 可用之处 | 发布边界 |
| --- | --- | --- |
| [AIO 作者仓](https://github.com/rag24/AIO) | 关键词、问题、比较问法按同一基础 ID 对齐；适合小规模选题实验。 | 上游仓标 [Apache-2.0](https://github.com/rag24/AIO/blob/main/LICENSE)，但汇集不同原始数据源，不能据此保证所有原始文字都能在本仓再许可。这里只发布链接、固定 ID 与复现脚本。 |
| [E-GEO 电商问题库](https://huggingface.co/datasets/psbagga17/E-GEO) | Reddit 购物讨论改写成长问题，更贴近具体消费语境。 | 数据卡写 CC-BY-4.0；关联 Amazon 产品字段另有上游权利，须另核授权，不复制其数据到这里。 |
| [原始 GEO-bench](https://huggingface.co/datasets/GEO-Optim/geo-bench/blob/main/README.md) | 10,000 道跨领域问题，可对照 GEO 原论文。 | 数据卡元数据写 CC-BY-SA-4.0，正文写 CC-BY-NC-SA-4.0；在来源澄清前不用于公开商业仓再发布。 |

## 本地复现候选问题

```bash
python3 scripts/prepare_aio_case.py
python3 -m traclux_probe annotate \
  --input reports/aio-benchmark/questions.txt \
  --output reports/aio-benchmark/labeled.json
```

脚本下载固定提交 `58f57d7db8c932bc67dcd9b94474db3223d65452`，核对 SHA-256 `653ef0387f8467abed0db9bf3acdbfde5dc6c83ff0448a993608d8cc05c0029d`，并对下列 20 个基础 ID 分别取问题和比较题：`2, 3, 5, 7, 12, 13, 14, 16, 23, 24, 27, 30, 34, 37, 39, 43, 45, 46, 48, 50`。相应 40 条题目仅写入忽略版本控制的 `reports/`；`source-manifest.json` 保留来源、组别和 ID。这些 ID 经人为检查排除显著非商品项，**属于方便上手的便利样本，不是随机代表样本**；若用于效果报告，必须另选冻结且独立的评估组。

后续实验需先由独立标注者确认六维状态和问题是否真正重要，再同预算比较覆盖选点、随机与关键词选题，在**未参与选题的评估集**上计算有价值盲点发现率。来源 CSV 不含六维真值、AI 客户端回答或销售结果。这里不触发付费模型，也不声称这些生成的问题代表真实消费需求。
