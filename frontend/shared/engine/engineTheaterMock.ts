import type {
  BrandPromptTopicSeed,
  BrandTrackingSeed,
  ChatEvidenceSeed,
  EngineEntityRef,
  ProbeSeed,
  SourceDomainSeed,
  SourceUrlSeed,
  TheaterActor,
  TheaterAttempt,
  TheaterBrandDataset,
  TheaterBucket,
  TheaterBucketKey,
  TheaterConsoleLine,
  TheaterConsoleLevel,
  TheaterEdgeType,
  TheaterEvent,
  TheaterEventStatus,
  TheaterEventType,
  TheaterEvidence,
  TheaterMention,
  TheaterProbeRun,
  TheaterResultBridge,
  TheaterStage,
  TheaterStageStep,
  TheaterStateEdge,
  TheaterStateNode,
  TheaterTask
} from '../../app/types/engine'

interface TheaterBuilderInput {
  brandOptions: Array<{ key: string, label: string }>
  competitorByBrand: Record<string, string[]>
  brandPromptTopicSeeds: Record<string, BrandPromptTopicSeed[]>
  probeSeedsByBrand: Record<string, ProbeSeed[]>
  sourceDomainsByBrand: Record<string, SourceDomainSeed[]>
  sourceUrlsByBrand: Record<string, SourceUrlSeed[]>
  chatEvidenceByBrand: Record<string, ChatEvidenceSeed[]>
  brandTrackingByWorkspaceBrand: Record<string, BrandTrackingSeed[]>
}

const BRAND_CATEGORY_MAP: Record<string, string> = {
  摩恩: '高端卫浴',
  卡贝: '家装五金',
  正泰: '家居电气',
  美厨物语: '厨房器具'
}

const STAGE_DEFINITIONS: Array<{ key: TheaterStage, label: string, description: string }> = [
  { key: 'task-decomposition', label: '任务拆解', description: '将前台任务单拆成主题簇、角色切面与问题姿态。' },
  { key: 'state-assembly', label: '状态装配', description: '把主题映射到 CSSM-HI 状态簇与风险因子。' },
  { key: 'orchestration', label: '编排成团', description: '通过 OATS 三桶给探针分配优先级与攻防路径。' },
  { key: 'actor-generation', label: '虚拟用户生成', description: '根据状态簇生成代表性问答角色与立场代理。' },
  { key: 'probe-generation', label: '探针生成', description: '将角色与主题拼装为可执行 Probe 队列。' },
  { key: 'execution', label: '探测执行', description: 'Node3~5B 启动联网证据、提及解析与门线收束。' },
  { key: 'result-bridging', label: '结果收束', description: '将执行证据桥接到 ABV 指标与后续页面。' }
]

const STATE_TEMPLATE_DEFS = [
  { label: '主流购买决策', summary: '高频用户先看主流口碑与首选心智。', bucketKey: 'mainstream' as TheaterBucketKey },
  { label: '安装适配风险', summary: '安装复杂度与适配门槛决定是否触发弃选。', bucketKey: 'coverage' as TheaterBucketKey },
  { label: '预算拉扯窗口', summary: '价格梯度与替代品牌会拉低首选率。', bucketKey: 'adversarial' as TheaterBucketKey },
  { label: '使用体验强信号', summary: '体验描述是 AI 首选率最直观的解释因子。', bucketKey: 'mainstream' as TheaterBucketKey },
  { label: '售后服务预期', summary: '售后闭环速度决定防御完整度。', bucketKey: 'coverage' as TheaterBucketKey },
  { label: '替代品牌侵入', summary: '对比提问会放大竞品抢答与替代路径。', bucketKey: 'adversarial' as TheaterBucketKey },
  { label: '证据可信门槛', summary: '来源质量与引用密度决定确认率。', bucketKey: 'coverage' as TheaterBucketKey },
  { label: '高价值场景触发', summary: '高价值使用场景会放大溢价与专业度叙事。', bucketKey: 'mainstream' as TheaterBucketKey }
]

const BUCKET_DEFINITIONS: Array<{ key: TheaterBucketKey, label: string, objective: string, tone: string }> = [
  { key: 'coverage', label: '基础覆盖', objective: '守住高频确定性问题，先把可见度底盘打稳。', tone: '稳定面' },
  { key: 'mainstream', label: '主流机会', objective: '抢占主流偏好窗口，放大首选与确认优势。', tone: '机会面' },
  { key: 'adversarial', label: '对抗压力', objective: '压测负面路径与替代选择，找出最易失守的节点。', tone: '压力面' }
]

const ACTOR_TEMPLATES: Record<TheaterBucketKey, Array<{ name: string, role: string, stance: string, posture: string }>> = {
  coverage: [
    { name: '稳妥首装用户', role: '新房首装', stance: '先求稳，再看口碑', posture: '确认型提问' },
    { name: '复核型采购人', role: '工程复核', stance: '优先看可靠与交付', posture: '审校型提问' },
    { name: '售后敏感业主', role: '长期使用者', stance: '在意售后闭环速度', posture: '风险规避型提问' }
  ],
  mainstream: [
    { name: '改善型家庭', role: '升级消费', stance: '愿意为体验与设计多付一点', posture: '偏好型提问' },
    { name: '口碑跟随者', role: '种子用户', stance: '偏爱高频推荐与成熟方案', posture: '跟随型提问' },
    { name: '场景驱动者', role: '高价值场景', stance: '会先看真实使用体验', posture: '场景型提问' }
  ],
  adversarial: [
    { name: '预算拉扯者', role: '价格敏感', stance: '总在替代品牌间来回比较', posture: '对比型提问' },
    { name: '替代守门员', role: '竞品心智', stance: '默认怀疑品牌溢价是否值得', posture: '攻防型提问' },
    { name: '质疑型决策者', role: '负面验证', stance: '先问风险、故障与差评', posture: '怀疑型提问' }
  ]
}

function normalizeToken(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function stageSteps(currentStage: TheaterStage): TheaterStageStep[] {
  const currentIndex = STAGE_DEFINITIONS.findIndex(stage => stage.key === currentStage)
  return STAGE_DEFINITIONS.map((stage, index) => ({
    ...stage,
    status: index < currentIndex ? 'done' : index === currentIndex ? 'active' : 'pending'
  }))
}

function stageLabel(stage: TheaterStage) {
  return STAGE_DEFINITIONS.find(item => item.key === stage)?.label || stage
}

function resolveBucketForProbe(probe: ProbeSeed): TheaterBucketKey {
  if (probe.tags.includes('comparison') || probe.tags.includes('price') || probe.tags.includes('after-sale')) {
    return 'adversarial'
  }
  if (probe.tags.includes('brand') || probe.tags.includes('reliability') || probe.tags.includes('install')) {
    return 'coverage'
  }
  return 'mainstream'
}

function levelFromEventType(type: TheaterEventType): TheaterConsoleLevel {
  if (type === 'attempt_started' || type === 'bucket_bound' || type === 'probe_generated') {
    return 'route'
  }
  if (type === 'evidence_attached' || type === 'brand_mentioned') {
    return 'network'
  }
  if (type === 'round_summary' || type === 'final_gate') {
    return 'gate'
  }
  return 'system'
}

function statusFromSequence(sequence: number, total: number): TheaterEventStatus {
  if (sequence === total - 1 || sequence === total) {
    return 'success'
  }
  if (sequence >= total - 5) {
    return 'running'
  }
  if (sequence % 9 === 0) {
    return 'warning'
  }
  return 'info'
}

function buildTimeLabel(sequence: number) {
  const totalSeconds = sequence * 21
  const hour = 9 + Math.floor(totalSeconds / 3600)
  const minute = Math.floor((totalSeconds % 3600) / 60)
  const second = totalSeconds % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
}

function uniqueById<T extends { id: string }>(rows: T[]) {
  return rows.filter((row, index) => rows.findIndex(item => item.id === row.id) === index)
}

function buildRefs(baseRefs: EngineEntityRef, overrides: Partial<EngineEntityRef> = {}): EngineEntityRef {
  return {
    ...baseRefs,
    ...overrides,
    tagIds: overrides.tagIds || baseRefs.tagIds
  }
}

export function buildTheaterByBrand(input: TheaterBuilderInput): Record<string, TheaterBrandDataset> {
  return Object.fromEntries(
    input.brandOptions.map(({ key: brand }) => {
      const topicSeeds = (input.brandPromptTopicSeeds[brand] || []).slice(0, 5)
      const probes = (input.probeSeedsByBrand[brand] || []).slice(0, 18)
      const sourceDomains = (input.sourceDomainsByBrand[brand] || []).slice(0, 6)
      const sourceUrlMap = new Map((input.sourceUrlsByBrand[brand] || []).map(item => [item.id, item]))
      const chats = input.chatEvidenceByBrand[brand] || []
      const trackedBrand = (input.brandTrackingByWorkspaceBrand[brand] || []).find(item => item.status === 'tracked') || input.brandTrackingByWorkspaceBrand[brand]?.[0]
      const refSource = probes[0]?.refs || trackedBrand?.refs || {}
      const taskId = refSource.taskId || `task-${normalizeToken(brand)}-theater`
      const baseRefs = buildRefs(refSource, { taskId })
      const category = BRAND_CATEGORY_MAP[brand] || '家居消费'
      const competitorCount = (input.competitorByBrand[brand] || []).length

      const task: TheaterTask = {
        id: `theater-task-${normalizeToken(brand)}`,
        brand,
        category,
        objective: `围绕 ${brand} 的品牌可见度、防御完整度与替代风险，生成可执行探针集并回放执行证据。`,
        competitorCount,
        modelScope: ['ChatGPT', 'Gemini', 'Perplexity', 'DeepSeek'],
        networkEnabled: true,
        probeTarget: probes.length,
        currentRound: 2,
        totalRounds: 3,
        progress: 72,
        currentStage: 'execution',
        statusLabel: 'Node3~5B 正在探测',
        taskLine: `${brand} / ${category} / ${competitorCount} 个竞品 / 18 条 Probe`,
        refs: baseRefs
      }

      const stateClusters = STATE_TEMPLATE_DEFS.map((stateSeed, index) => ({
        id: `${normalizeToken(brand)}-state-${index + 1}`,
        label: stateSeed.label,
        summary: `${brand} 在「${topicSeeds[index % topicSeeds.length]?.topic || '关键主题'}」上的 ${stateSeed.summary}`,
        bucketKey: stateSeed.bucketKey
      }))

      const buckets: TheaterBucket[] = BUCKET_DEFINITIONS.map((bucketDef) => {
        const bucketProbes = probes.filter(probe => resolveBucketForProbe(probe) === bucketDef.key)
        return {
          key: bucketDef.key,
          label: bucketDef.label,
          objective: bucketDef.objective,
          tone: bucketDef.tone,
          targetProbeCount: bucketProbes.length,
          generatedProbeCount: bucketProbes.length,
          executedProbeCount: bucketDef.key === 'coverage' ? Math.min(bucketProbes.length, 6) : bucketDef.key === 'mainstream' ? Math.min(bucketProbes.length, 5) : Math.min(bucketProbes.length, 4),
          topicCoverage: new Set(bucketProbes.map(probe => probe.topicId)).size,
          actorCoverage: ACTOR_TEMPLATES[bucketDef.key].length,
          fallbackCount: bucketDef.key === 'adversarial' ? 2 : bucketDef.key === 'coverage' ? 1 : 0,
          refs: buildRefs(baseRefs)
        }
      })

      const actors: TheaterActor[] = BUCKET_DEFINITIONS.flatMap((bucketDef, bucketIndex) => {
        const templates = ACTOR_TEMPLATES[bucketDef.key]
        return templates.map((actorSeed, actorIndex) => {
          const stateIds = stateClusters
            .filter(state => state.bucketKey === bucketDef.key)
            .slice(0, 2)
            .map(state => state.id)
          return {
            id: `${normalizeToken(brand)}-${bucketDef.key}-actor-${actorIndex + 1}`,
            name: actorSeed.name,
            role: actorSeed.role,
            stance: actorSeed.stance,
            posture: actorSeed.posture,
            sourceStateIds: stateIds,
            bucketKey: bucketDef.key,
            summary: `${actorSeed.role} 会优先围绕 ${topicSeeds[(bucketIndex + actorIndex) % topicSeeds.length]?.topic || '关键主题'} 提问。`,
            revealedAt: 10 + bucketIndex,
            refs: buildRefs(baseRefs)
          }
        })
      })

      const actorsByBucket = Object.fromEntries(
        BUCKET_DEFINITIONS.map(bucket => [bucket.key, actors.filter(actor => actor.bucketKey === bucket.key)])
      ) as Record<TheaterBucketKey, TheaterActor[]>

      const probeRuns: TheaterProbeRun[] = probes.map((probe, index) => {
        const bucketKey = resolveBucketForProbe(probe)
        const bucketActors = actorsByBucket[bucketKey]
        const actor = bucketActors[index % bucketActors.length] || actors[0]!
        const status: TheaterProbeRun['status'] = index < 8 ? 'complete' : index < 12 ? 'running' : index < 16 ? 'queued' : 'fallback'
        return {
          id: `${normalizeToken(brand)}-theater-probe-${index + 1}`,
          probeId: probe.id,
          label: probe.prompt,
          topic: probe.topic,
          bucketKey,
          actorId: actor.id,
          stateIds: actor.sourceStateIds,
          status,
          summary: `${actor.name} 围绕「${probe.topic}」发起 ${status === 'fallback' ? '回退验证' : '主线探测'}。`,
          revealedAt: 13 + Math.floor(index / 3),
          refs: buildRefs(probe.refs, { probeId: probe.id, taskId, topicId: probe.topicId, tagIds: probe.tags })
        }
      })

      const attempts: TheaterAttempt[] = probeRuns.slice(0, 8).map((run, index) => {
        const probe = probes.find(item => item.id === run.probeId) || probes[index]
        const chat = chats.find(item => item.probeId === run.probeId) || chats[index]
        return {
          id: `${run.id}-attempt-1`,
          probeId: run.probeId,
          round: index < 5 ? 1 : 2,
          model: chat?.model || task.modelScope[index % task.modelScope.length] || 'ChatGPT',
          latencyMs: 920 + index * 118,
          confidence: Math.max(0.62, 0.88 - index * 0.03),
          status: index < 6 ? 'succeeded' : index === 6 ? 'running' : 'failed',
          query: chat?.query || probe?.prompt || run.label,
          renderedPrompt: `${brand} / ${probe?.topic || run.topic} / ${run.bucketKey} / ${actorDisplayLabel(actors.find(actor => actor.id === run.actorId))}`,
          networkQueries: [
            `${brand}${probe?.topic || run.topic} 真实用户评价`,
            `${brand}${probe?.topic || run.topic} 竞品对比`,
            `${brand}${probe?.topic || run.topic} 引用来源`
          ],
          revealedAt: 19 + index * 3,
          refs: buildRefs(run.refs, { invocationId: `${run.id}-invocation-1`, chatId: chat?.id })
        }
      })

      const evidences: TheaterEvidence[] = attempts.slice(0, 7).map((attempt, index) => {
        const chat = chats.find(item => item.probeId === attempt.probeId) || chats[index]
        const citation = chat?.citations[0]
        const sourceUrl = citation ? sourceUrlMap.get(citation.sourceId) : undefined
        const sourceDomain = sourceDomains.find(domain => domain.id === sourceUrl?.domainId) || sourceDomains[index % sourceDomains.length] || sourceDomains[0]!
        return {
          id: `${attempt.id}-evidence`,
          probeId: attempt.probeId,
          attemptId: attempt.id,
          sourceDomainId: sourceDomain.id,
          sourceUrlId: sourceUrl?.id || sourceDomain.id,
          title: citation?.title || `${sourceDomain.domain} / ${brand} / ${probeRuns[index]?.topic || '关键主题'}`,
          snippet: chat?.summary?.[1] || `${sourceDomain.domain} 提供了关于 ${brand} 的结构化外部证据，支撑本轮提及解析。`,
          confidence: Math.max(0.58, 0.9 - index * 0.04),
          revealedAt: attempt.revealedAt + 1,
          refs: buildRefs(attempt.refs, { evidenceId: `${attempt.id}-evidence`, sourceDomainId: sourceDomain.id, sourceUrlId: sourceUrl?.id })
        }
      })

      const mentions: TheaterMention[] = attempts.slice(0, 7).map((attempt, index) => {
        const chat = chats.find(item => item.probeId === attempt.probeId) || chats[index]
        const probe = probes.find(item => item.id === attempt.probeId) || probes[index]
        return {
          id: `${attempt.id}-mention`,
          probeId: attempt.probeId,
          attemptId: attempt.id,
          brand,
          sentiment: probe?.sentiment || 68,
          position: probe?.position || 2.1,
          confidence: Math.max(0.56, 0.87 - index * 0.04),
          excerpt: chat?.answer || `${brand} 在该轮回答中被呈现为 ${probe && probe.sentiment >= 60 ? '更稳妥' : '仍需补强'} 的选择。`,
          revealedAt: attempt.revealedAt + 2,
          refs: buildRefs(attempt.refs, { citationId: chat?.citations?.[0]?.id })
        }
      })

      const resultBridge: TheaterResultBridge = {
        id: `${normalizeToken(brand)}-result-bridge`,
        abvIndex: trackedBrand?.visibility || 67,
        firstChoiceRate: Math.max(52, (trackedBrand?.visibility || 67) - 5.3),
        confirmationRate: Math.max(55, (trackedBrand?.visibility || 67) - 1.7),
        defenseIntegrity: Math.max(50, (trackedBrand?.visibility || 67) - 3.8),
        summary: [
          `${brand} 当前已形成 3 条 OATS 编排路径，主流机会桶对首选率拉动最明显。`,
          `执行期已产生 ${evidences.length} 条有效外部证据，说明引用满足率开始稳定。`,
          `当前最脆弱的失守点集中在「${stateClusters.find(item => item.bucketKey === 'adversarial')?.label || '替代路径'}」。`
        ],
        routes: [
          { label: '查看品牌洞察', path: '/engine', description: '跳到结果层查看 ABV、平台、情感与引用结果。' },
          { label: '查看探针分析', path: '/probe-analysis', description: '回到 Probe 资产层检查主题、标签与探针结构。' },
          { label: '查看信源归因', path: '/sources', description: '进入信源层继续看域名、URL 与引用占比。' }
        ],
        refs: buildRefs(baseRefs)
      }

      const nodes: TheaterStateNode[] = []
      const edges: TheaterStateEdge[] = []

      const pushNode = (node: TheaterStateNode) => {
        nodes.push(node)
      }

      const pushEdge = (sourceId: string, targetId: string, type: TheaterEdgeType, stage: TheaterStage, revealedAt: number, bucketKey?: TheaterBucketKey, refs: EngineEntityRef = baseRefs) => {
        edges.push({
          id: `${sourceId}->${targetId}`,
          sourceId,
          targetId,
          type,
          stage,
          bucketKey,
          revealedAt,
          refs
        })
      }

      const taskNodeId = `${task.id}-node`
      pushNode({
        id: taskNodeId,
        type: 'task',
        label: `${brand} 前台任务单`,
        subtitle: task.taskLine,
        detailLines: [task.objective, `目标模型：${task.modelScope.join(' / ')}`, `当前阶段：${stageLabel(task.currentStage)}`],
        stage: 'task-decomposition',
        layer: 0,
        slot: 0,
        status: 'done',
        revealedAt: 1,
        emphasis: 'core',
        refs: baseRefs
      })

      topicSeeds.forEach((topic, index) => {
        const nodeId = `${topic.topicId}-node`
        pushNode({
          id: nodeId,
          type: 'topic',
          label: topic.topic,
          subtitle: `${topic.prompts.length} 条前台任务单`,
          detailLines: topic.prompts.slice(0, 3),
          stage: 'task-decomposition',
          layer: 1,
          slot: index,
          status: 'done',
          revealedAt: 2 + Math.floor(index / 2),
          emphasis: index < 2 ? 'core' : 'support',
          refs: buildRefs(baseRefs, { topicId: topic.topicId })
        })
        pushEdge(taskNodeId, nodeId, 'contains', 'task-decomposition', 2 + Math.floor(index / 2), undefined, buildRefs(baseRefs, { topicId: topic.topicId }))
      })

      stateClusters.forEach((state, index) => {
        const stateNodeId = `${state.id}-node`
        pushNode({
          id: stateNodeId,
          type: 'state',
          label: state.label,
          subtitle: 'CSSM-HI 状态簇',
          detailLines: [state.summary, `主要落在 ${BUCKET_DEFINITIONS.find(item => item.key === state.bucketKey)?.label || '未知桶'}。`],
          stage: 'state-assembly',
          layer: 2,
          slot: index,
          bucketKey: state.bucketKey,
          status: index < 5 ? 'done' : 'active',
          revealedAt: 4 + Math.floor(index / 2),
          emphasis: index < 4 ? 'core' : 'support',
          refs: baseRefs
        })

        const topic = topicSeeds[index % topicSeeds.length] || topicSeeds[0]!
        pushEdge(`${topic.topicId}-node`, stateNodeId, 'derives', 'state-assembly', 4 + Math.floor(index / 2), state.bucketKey, buildRefs(baseRefs, { topicId: topic.topicId }))
      })

      buckets.forEach((bucket, index) => {
        const bucketNodeId = `${normalizeToken(brand)}-bucket-${bucket.key}`
        pushNode({
          id: bucketNodeId,
          type: 'bucket',
          label: bucket.label,
          subtitle: `${bucket.generatedProbeCount}/${bucket.targetProbeCount} 探针已成团`,
          detailLines: [bucket.objective, `主题覆盖 ${bucket.topicCoverage} / 角色覆盖 ${bucket.actorCoverage} / 回退 ${bucket.fallbackCount}`],
          stage: 'orchestration',
          layer: 3,
          slot: index,
          bucketKey: bucket.key,
          status: 'done',
          revealedAt: 7 + index,
          emphasis: 'core',
          refs: bucket.refs
        })

        stateClusters
          .filter(state => state.bucketKey === bucket.key)
          .forEach((state) => {
            pushEdge(`${state.id}-node`, bucketNodeId, 'routes', 'orchestration', 7 + index, bucket.key, baseRefs)
          })
      })

      actors.forEach((actor, index) => {
        const nodeId = `${actor.id}-node`
        pushNode({
          id: nodeId,
          type: 'actor',
          label: actor.name,
          subtitle: `${actor.role} / ${actor.posture}`,
          detailLines: [actor.stance, actor.summary],
          stage: 'actor-generation',
          layer: 4,
          slot: index,
          bucketKey: actor.bucketKey,
          status: 'done',
          revealedAt: actor.revealedAt,
          emphasis: index < 4 ? 'core' : 'support',
          refs: actor.refs
        })
        pushEdge(`${normalizeToken(brand)}-bucket-${actor.bucketKey}`, nodeId, 'contains', 'actor-generation', actor.revealedAt, actor.bucketKey, actor.refs)
      })

      probeRuns.forEach((run, index) => {
        const nodeId = `${run.id}-node`
        pushNode({
          id: nodeId,
          type: 'probe',
          label: clipText(run.label, 22),
          subtitle: `${run.topic} / ${statusLabelFromProbeRun(run.status)}`,
          detailLines: [run.summary, `所属桶：${BUCKET_DEFINITIONS.find(item => item.key === run.bucketKey)?.label || run.bucketKey}`],
          stage: 'probe-generation',
          layer: 5,
          slot: index,
          bucketKey: run.bucketKey,
          status: probeRunNodeStatus(run.status),
          revealedAt: run.revealedAt,
          emphasis: index < 8 ? 'core' : 'support',
          refs: run.refs
        })
        pushEdge(`${run.actorId}-node`, nodeId, 'executes', 'probe-generation', run.revealedAt, run.bucketKey, run.refs)
      })

      attempts.forEach((attempt, index) => {
        const nodeId = `${attempt.id}-node`
        pushNode({
          id: nodeId,
          type: 'attempt',
          label: `Attempt ${attempt.round}`,
          subtitle: `${attempt.model} / ${attempt.latencyMs}ms`,
          detailLines: [attempt.renderedPrompt, `联网查询：${attempt.networkQueries.join('；')}`],
          stage: 'execution',
          layer: 6,
          slot: index,
          bucketKey: probeRuns.find(run => run.probeId === attempt.probeId)?.bucketKey,
          status: attemptNodeStatus(attempt.status),
          revealedAt: attempt.revealedAt,
          emphasis: index < 4 ? 'core' : 'support',
          refs: attempt.refs
        })
        const probeRun = probeRuns.find(run => run.probeId === attempt.probeId)
        if (probeRun) {
          pushEdge(`${probeRun.id}-node`, nodeId, 'executes', 'execution', attempt.revealedAt, probeRun.bucketKey, attempt.refs)
        }
      })

      const sourceNodes = uniqueById(
        evidences.map((evidence, index) => {
          const domain = sourceDomains.find(item => item.id === evidence.sourceDomainId) || sourceDomains[index % sourceDomains.length] || sourceDomains[0]!
          return {
            id: `${domain.id}-theater-node`,
            type: 'source' as const,
            label: domain.domain,
            subtitle: `${domain.type} / 引用占比 ${domain.share}%`,
            detailLines: [`Used ${domain.used}%`, `Avg Citations ${domain.avgCitations}`],
            stage: 'execution' as TheaterStage,
            layer: 8,
            slot: index,
            status: 'done' as const,
            revealedAt: evidence.revealedAt,
            emphasis: index < 3 ? 'core' as const : 'support' as const,
            refs: buildRefs(evidence.refs, { sourceDomainId: domain.id })
          }
        })
      )
      sourceNodes.forEach(pushNode)

      evidences.forEach((evidence, index) => {
        const nodeId = `${evidence.id}-node`
        pushNode({
          id: nodeId,
          type: 'evidence',
          label: clipText(evidence.title, 20),
          subtitle: `${Math.round(evidence.confidence * 100)}% 证据可信度`,
          detailLines: [evidence.snippet],
          stage: 'execution',
          layer: 7,
          slot: index,
          bucketKey: probeRuns.find(run => run.probeId === evidence.probeId)?.bucketKey,
          status: 'done',
          revealedAt: evidence.revealedAt,
          emphasis: index < 4 ? 'core' : 'support',
          refs: evidence.refs
        })
        pushEdge(`${evidence.attemptId}-node`, nodeId, 'supports', 'execution', evidence.revealedAt, probeRuns.find(run => run.probeId === evidence.probeId)?.bucketKey, evidence.refs)
        pushEdge(nodeId, `${evidence.sourceDomainId}-theater-node`, 'supports', 'execution', evidence.revealedAt, probeRuns.find(run => run.probeId === evidence.probeId)?.bucketKey, evidence.refs)
      })

      mentions.forEach((mention, index) => {
        const nodeId = `${mention.id}-node`
        pushNode({
          id: nodeId,
          type: 'mention',
          label: `${mention.brand} 被提及`,
          subtitle: `情感 ${mention.sentiment} / 位次 ${mention.position}`,
          detailLines: [mention.excerpt, `置信度 ${Math.round(mention.confidence * 100)}%`],
          stage: 'execution',
          layer: 9,
          slot: index,
          bucketKey: probeRuns.find(run => run.probeId === mention.probeId)?.bucketKey,
          status: index < 5 ? 'done' : 'active',
          revealedAt: mention.revealedAt,
          emphasis: index < 4 ? 'core' : 'support',
          refs: mention.refs
        })
        const evidence = evidences.find(item => item.attemptId === mention.attemptId)
        if (evidence) {
          pushEdge(`${evidence.id}-node`, nodeId, 'mentions', 'execution', mention.revealedAt, probeRuns.find(run => run.probeId === mention.probeId)?.bucketKey, mention.refs)
        }
      })

      const resultNodeId = `${resultBridge.id}-node`
      pushNode({
        id: resultNodeId,
        type: 'result',
        label: `${brand} 结果桥`,
        subtitle: `ABV ${resultBridge.abvIndex.toFixed(1)}`,
        detailLines: resultBridge.summary,
        stage: 'result-bridging',
        layer: 10,
        slot: 0,
        status: 'active',
        revealedAt: 45,
        emphasis: 'core',
        refs: resultBridge.refs
      })
      mentions.forEach((mention) => {
        pushEdge(`${mention.id}-node`, resultNodeId, 'converges', 'result-bridging', 45, probeRuns.find(run => run.probeId === mention.probeId)?.bucketKey, mention.refs)
      })

      const eventSeeds: Array<Omit<TheaterEvent, 'status' | 'timeLabel'>> = [
        eventSeed(1, 'task-decomposition', 'task_ingested', '前台任务单入场', `${brand} 前台任务单已接收，准备拆成主题簇与问答角色。`, taskNodeId, undefined, undefined, [task.objective], baseRefs),
        eventSeed(2, 'task-decomposition', 'task_ingested', '任务单映射到主题簇', `已识别 ${topicSeeds.length} 个主题簇，优先保留高频购买与售后类任务。`, `${topicSeeds[0]?.topicId}-node`, undefined, undefined, topicSeeds.slice(0, 3).map(item => item.topic), baseRefs),
        eventSeed(3, 'task-decomposition', 'task_ingested', '问题姿态完成归一', '将前台表述归并为确定性、对比型、风险规避型三类问题姿态。', `${topicSeeds[1]?.topicId}-node`, undefined, undefined, ['确定性提问', '对比型提问', '风险规避型提问'], baseRefs),
        eventSeed(4, 'state-assembly', 'state_assembled', '主流与安装状态装配完成', '主流购买决策、安装适配风险被正式装配到状态空间。', `${stateClusters[0]?.id}-node`, undefined, 'mainstream', stateClusters.slice(0, 2).map(item => item.label), baseRefs),
        eventSeed(5, 'state-assembly', 'state_assembled', '预算与替代状态装配完成', '预算拉扯窗口、替代品牌侵入被标记为重点压力区。', `${stateClusters[2]?.id}-node`, undefined, 'adversarial', stateClusters.slice(2, 4).map(item => item.label), baseRefs),
        eventSeed(6, 'state-assembly', 'state_assembled', '售后与证据门槛入场', '售后服务预期、证据可信门槛成为后续门线的关键解释因子。', `${stateClusters[4]?.id}-node`, undefined, 'coverage', stateClusters.slice(4, 6).map(item => item.label), baseRefs),
        eventSeed(7, 'orchestration', 'bucket_bound', '基础覆盖桶就位', '高频确定性问题被分配到基础覆盖桶。', `${normalizeToken(brand)}-bucket-coverage`, undefined, 'coverage', ['以可靠性、安装、售后类任务为主'], baseRefs),
        eventSeed(8, 'orchestration', 'bucket_bound', '主流机会桶就位', '主流体验与品牌偏好问题进入机会面。', `${normalizeToken(brand)}-bucket-mainstream`, undefined, 'mainstream', ['以体验、心智、首选类任务为主'], baseRefs),
        eventSeed(9, 'orchestration', 'bucket_bound', '对抗压力桶就位', '价格、替代与负面验证问题进入压力面。', `${normalizeToken(brand)}-bucket-adversarial`, undefined, 'adversarial', ['以对比、价格、风险类任务为主'], baseRefs),
        eventSeed(10, 'actor-generation', 'actor_generated', '基础覆盖角色生成', '稳妥首装用户、复核型采购人与售后敏感业主已生成。', `${actors[0]?.id}-node`, undefined, 'coverage', actors.filter(actor => actor.bucketKey === 'coverage').map(actor => actor.name), baseRefs),
        eventSeed(11, 'actor-generation', 'actor_generated', '主流机会角色生成', '改善型家庭、口碑跟随者、场景驱动者开始进入剧场。', `${actors[3]?.id}-node`, undefined, 'mainstream', actors.filter(actor => actor.bucketKey === 'mainstream').map(actor => actor.name), baseRefs),
        eventSeed(12, 'actor-generation', 'actor_generated', '对抗压力角色生成', '预算拉扯者、替代守门员与质疑型决策者开始压测品牌边界。', `${actors[6]?.id}-node`, undefined, 'adversarial', actors.filter(actor => actor.bucketKey === 'adversarial').map(actor => actor.name), baseRefs),
        eventSeed(13, 'probe-generation', 'probe_generated', 'Probe 批次 A 已生成', `第一批 Probe 已覆盖 ${topicSeeds[0]?.topic} 与 ${topicSeeds[1]?.topic}。`, `${probeRuns[0]?.id}-node`, probeRuns[0]?.probeId, probeRuns[0]?.bucketKey, probeRuns.slice(0, 3).map(item => clipText(item.label, 16)), baseRefs),
        eventSeed(14, 'probe-generation', 'probe_generated', 'Probe 批次 B 已生成', `第二批 Probe 将主流体验问题编排进机会桶。`, `${probeRuns[3]?.id}-node`, probeRuns[3]?.probeId, probeRuns[3]?.bucketKey, probeRuns.slice(3, 6).map(item => clipText(item.label, 16)), baseRefs),
        eventSeed(15, 'probe-generation', 'probe_generated', 'Probe 批次 C 已生成', '压力桶开始补全预算与替代路径。', `${probeRuns[6]?.id}-node`, probeRuns[6]?.probeId, probeRuns[6]?.bucketKey, probeRuns.slice(6, 9).map(item => clipText(item.label, 16)), baseRefs),
        eventSeed(16, 'probe-generation', 'probe_generated', 'Probe 批次 D 已生成', '覆盖桶补全售后与安装验证 Probe。', `${probeRuns[9]?.id}-node`, probeRuns[9]?.probeId, probeRuns[9]?.bucketKey, probeRuns.slice(9, 12).map(item => clipText(item.label, 16)), baseRefs),
        eventSeed(17, 'probe-generation', 'probe_generated', 'Probe 批次 E 已生成', '最后一组 Probe 预留给压力桶回退与补样。', `${probeRuns[12]?.id}-node`, probeRuns[12]?.probeId, probeRuns[12]?.bucketKey, probeRuns.slice(12, 15).map(item => clipText(item.label, 16)), baseRefs),
        eventSeed(18, 'probe-generation', 'probe_generated', 'Probe 队列成团完成', `共生成 ${probeRuns.length} 条 Probe，等待 Node3~5B 启动。`, `${probeRuns[15]?.id}-node`, probeRuns[15]?.probeId, probeRuns[15]?.bucketKey, ['Coverage / Mainstream / Adversarial 已联通'], baseRefs),
        ...attempts.slice(0, 6).flatMap((attempt, index) => {
          const evidence = evidences.find(item => item.attemptId === attempt.id)
          const mention = mentions.find(item => item.attemptId === attempt.id)
          const probeRun = probeRuns.find(item => item.probeId === attempt.probeId)
          const baseSequence = 19 + index * 3
          const rows: Array<Omit<TheaterEvent, 'status' | 'timeLabel'>> = [
            eventSeed(baseSequence, 'execution', 'attempt_started', `Probe 执行启动 · ${probeRun?.topic || '未知主题'}`, `${attempt.model} 已接管 Probe，并开始联网拉取外部证据。`, `${attempt.id}-node`, attempt.probeId, probeRun?.bucketKey, attempt.networkQueries, attempt.refs)
          ]
          if (evidence) {
            rows.push(eventSeed(baseSequence + 1, 'execution', 'evidence_attached', `证据挂载 · ${clipText(evidence.title, 16)}`, evidence.snippet, `${evidence.id}-node`, attempt.probeId, probeRun?.bucketKey, [`来源域名：${sourceDomains.find(item => item.id === evidence.sourceDomainId)?.domain || evidence.sourceDomainId}`], evidence.refs))
          }
          if (mention) {
            rows.push(eventSeed(baseSequence + 2, 'execution', 'brand_mentioned', `提及解析 · ${mention.brand}`, `解析到品牌提及，情感 ${mention.sentiment}，位次 ${mention.position.toFixed(1)}。`, `${mention.id}-node`, attempt.probeId, probeRun?.bucketKey, [clipText(mention.excerpt, 34)], mention.refs))
          }
          return rows
        }),
        eventSeed(37, 'execution', 'round_summary', '第 1 轮收束', '第一轮执行完成，主流机会桶拉动明显，对抗压力桶出现 1 次回退。', `${probeRuns[6]?.id}-node`, undefined, 'mainstream', ['主流机会桶完成率 83%', '对抗压力桶出现 1 次回退'], baseRefs),
        eventSeed(38, 'execution', 'attempt_started', `Probe 执行启动 · ${probeRuns[6]?.topic || '关键主题'}`, '第二轮开始对未达门线的 Probe 做补样探测。', `${attempts[6]?.id}-node`, attempts[6]?.probeId, probeRuns[6]?.bucketKey, attempts[6]?.networkQueries || [], attempts[6]?.refs || baseRefs),
        eventSeed(39, 'execution', 'evidence_attached', `证据挂载 · ${clipText(evidences[6]?.title || '关键证据', 16)}`, evidences[6]?.snippet || '补样证据已回流至剧场。', `${evidences[6]?.id}-node`, evidences[6]?.probeId, probeRuns[6]?.bucketKey, [`来源域名：${sourceDomains.find(item => item.id === evidences[6]?.sourceDomainId)?.domain || '未知'}`], evidences[6]?.refs || baseRefs),
        eventSeed(40, 'execution', 'brand_mentioned', `提及解析 · ${brand}`, `最新提及说明 ${brand} 在第二轮中重新回到可确认状态。`, `${mentions[6]?.id}-node`, mentions[6]?.probeId, probeRuns[6]?.bucketKey, [clipText(mentions[6]?.excerpt || '暂无摘要', 34)], mentions[6]?.refs || baseRefs),
        eventSeed(41, 'execution', 'attempt_started', `回退探测 · ${probeRuns[7]?.topic || '关键主题'}`, '压力桶开始执行回退路径，验证竞品替代是否持续侵入。', `${attempts[7]?.id}-node`, attempts[7]?.probeId, probeRuns[7]?.bucketKey, attempts[7]?.networkQueries || [], attempts[7]?.refs || baseRefs),
        eventSeed(42, 'execution', 'round_summary', '第 2 轮收束', '执行端开始收口，引用满足率与品牌提及密度都已回升。', `${probeRuns[7]?.id}-node`, undefined, 'coverage', ['引用满足率 77%', '品牌提及密度稳定回升'], baseRefs),
        eventSeed(43, 'execution', 'engine_log', '监控端开始 final gate 预检', '系统正在复核 Coverage / Mainstream / Adversarial 三桶是否全部满足门线。', taskNodeId, undefined, undefined, ['检查 Gate: coverage', '检查 Gate: mainstream', '检查 Gate: adversarial'], baseRefs),
        eventSeed(44, 'result-bridging', 'final_gate', 'Final Gate 通过', `${brand} 已通过 final gate，结果桥开始向品牌洞察与信源归因投递。`, resultNodeId, undefined, undefined, ['ABV / 首选率 / 防御完整度开始回写'], baseRefs),
        eventSeed(45, 'result-bridging', 'final_gate', '结果桥就绪', '引擎剧场完成收束，可以跳转到结果页继续追查。', resultNodeId, undefined, undefined, resultBridge.summary, baseRefs)
      ]

      const events: TheaterEvent[] = eventSeeds.map(seed => ({
        ...seed,
        timeLabel: buildTimeLabel(seed.sequence),
        status: statusFromSequence(seed.sequence, eventSeeds.length)
      }))

      const consoleLines: TheaterConsoleLine[] = events
        .filter((event, index) => index < 28 || event.type === 'final_gate')
        .map((event, index) => ({
          id: `${normalizeToken(brand)}-console-${index + 1}`,
          sequence: event.sequence,
          stage: event.stage,
          level: levelFromEventType(event.type),
          message: consoleMessageFromEvent(event),
          timeLabel: event.timeLabel,
          eventId: event.id,
          refs: event.refs
        }))

      return [brand, {
        task,
        stageSteps: stageSteps(task.currentStage),
        nodes,
        edges,
        buckets,
        actors,
        probeRuns,
        attempts,
        evidences,
        mentions,
        events,
        consoleLines,
        resultBridge
      } satisfies TheaterBrandDataset]
    })
  ) as Record<string, TheaterBrandDataset>
}

function eventSeed(
  sequence: number,
  stage: TheaterStage,
  type: TheaterEventType,
  title: string,
  summary: string,
  nodeId: string | undefined,
  probeId: string | undefined,
  bucketKey: TheaterBucketKey | undefined,
  detailLines: string[],
  refs: EngineEntityRef
): Omit<TheaterEvent, 'status' | 'timeLabel'> {
  return {
    id: `event-${sequence}`,
    sequence,
    stage,
    type,
    title,
    summary,
    nodeId,
    probeId,
    bucketKey,
    detailLines,
    refs
  }
}

function consoleMessageFromEvent(event: TheaterEvent) {
  switch (event.type) {
    case 'task_ingested':
      return `[task] ${event.title} -> ${event.summary}`
    case 'state_assembled':
      return `[state] ${event.title} -> 已写入状态空间节点`
    case 'bucket_bound':
      return `[oats] ${event.title} -> ${event.bucketKey || 'unknown'} ready`
    case 'actor_generated':
      return `[actor] ${event.title} -> 角色代理已挂接到图谱`
    case 'probe_generated':
      return `[probe] ${event.title} -> Probe 队列开始排队`
    case 'attempt_started':
      return `[route] ${event.title} -> 执行链启动`
    case 'evidence_attached':
      return `[network] ${event.title} -> 外部证据已挂载`
    case 'brand_mentioned':
      return `[parse] ${event.title} -> 提及解析完成`
    case 'round_summary':
      return `[round] ${event.title} -> 汇总已更新`
    case 'engine_log':
      return `[engine] ${event.summary}`
    case 'final_gate':
      return `[gate] ${event.title} -> ${event.summary}`
    default:
      return event.summary
  }
}

function actorDisplayLabel(actor?: TheaterActor) {
  if (!actor) {
    return '未知代理'
  }
  return `${actor.name}/${actor.posture}`
}

function clipText(value: string, max: number) {
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function statusLabelFromProbeRun(status: TheaterProbeRun['status']) {
  switch (status) {
    case 'complete':
      return '已回写'
    case 'running':
      return '执行中'
    case 'fallback':
      return '回退探测'
    default:
      return '待执行'
  }
}

function probeRunNodeStatus(status: TheaterProbeRun['status']): TheaterStateNode['status'] {
  switch (status) {
    case 'complete':
      return 'done'
    case 'running':
      return 'active'
    case 'fallback':
      return 'warning'
    default:
      return 'idle'
  }
}

function attemptNodeStatus(status: TheaterAttempt['status']): TheaterStateNode['status'] {
  switch (status) {
    case 'succeeded':
      return 'done'
    case 'running':
      return 'active'
    case 'failed':
      return 'error'
    default:
      return 'idle'
  }
}
