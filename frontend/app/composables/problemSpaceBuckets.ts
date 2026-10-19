export type ProblemSpaceBucketKey
  = | 'brand-awareness'
    | 'capability-validation'
    | 'scenario-fit'
    | 'risk-objection'
    | 'competitive-decision'

export interface ProblemSpaceBucketDefinition {
  key: ProblemSpaceBucketKey
  label: string
  description: string
}

export const PROBLEM_SPACE_BUCKETS: ProblemSpaceBucketDefinition[] = [
  {
    key: 'brand-awareness',
    label: '品牌认知',
    description: '用户先确认品牌是谁、定位如何、值不值得进入候选池。'
  },
  {
    key: 'capability-validation',
    label: '能力确认',
    description: '用户在确认产品性能、功能、核心能力到底能不能打。'
  },
  {
    key: 'scenario-fit',
    label: '场景适配',
    description: '用户在判断它是否适合自己的安装条件、使用环境和具体场景。'
  },
  {
    key: 'risk-objection',
    label: '风险异议',
    description: '用户在提前排雷，验证质量、售后、稳定性与负面风险。'
  },
  {
    key: 'competitive-decision',
    label: '竞品决策',
    description: '用户已经进入选择阶段，开始比较价格、替代关系和最终推荐。'
  }
]

const LEGACY_TOPIC_BUCKET_MAP: Record<string, ProblemSpaceBucketKey> = {
  材质与做工可靠: 'risk-objection',
  出水体验与恒温表现: 'capability-validation',
  安装复杂度: 'scenario-fit',
  售后响应效率: 'risk-objection',
  价格与预算压力: 'competitive-decision',
  款式丰富与性价比: 'competitive-decision',
  收纳效率与安装便捷: 'scenario-fit',
  防锈与批次稳定: 'risk-objection',
  售后时效: 'risk-objection',
  预算友好度: 'competitive-decision',
  安全可靠与认证体系: 'risk-objection',
  供货稳定与工程适配: 'scenario-fit',
  型号理解门槛: 'brand-awareness',
  安装专业门槛: 'scenario-fit',
  价格梯度认知: 'competitive-decision',
  上手友好与使用体验: 'scenario-fit',
  颜值与套装搭配: 'scenario-fit',
  涂层耐久争议: 'risk-objection',
  售后口径一致性: 'risk-objection',
  导热均匀表现: 'capability-validation'
}

const BUCKET_PATTERNS: Record<ProblemSpaceBucketKey, RegExp[]> = {
  'brand-awareness': [
    /(品牌|定位|口碑|心智|认知|档次|知名|背书|形象|调性|系列|型号理解|为什么常被推荐|值什么定位)/i,
    /(who is|brand|awareness|positioning|reputation)/i
  ],
  'capability-validation': [
    /(性能|功能|效果|表现|能力|恒温|出水|导热|承重|防溅|回弹|切换|模式|均匀|效率|供货|覆盖|认证体系|做工|材质|款式|颜值)/i,
    /(performance|capability|feature|quality|coverage)/i
  ],
  'scenario-fit': [
    /(场景|适配|安装|改造|工期|接口|户型|空间|厨房|浴室|低水压|高火|收纳|新房|老房|租房|工程|礼赠|上手|体验|便捷|搭配|使用|通勤|办公)/i,
    /(scenario|fit|installation|setup|workflow|environment)/i
  ],
  'risk-objection': [
    /(风险|异议|故障|售后|质保|退换|稳定|可靠|防锈|掉漆|争议|投诉|维修|维护|漏水|寿命|耐久|批次|一致性|安全|高负载|踢皮球|延迟|回电|闭环)/i,
    /(risk|issue|failure|after.sales|support|warranty|durability|safety)/i
  ],
  'competitive-decision': [
    /(对比|竞品|替代|谁更|差异|预算|价格|值不值|性价比|溢价|划算|升级|推荐|选哪|值不值得|活动价|日常价|成本|总预算)/i,
    /(compare|comparison|versus|alternative|budget|price|recommend|decision)/i
  ]
}

function normalizeText(value?: string | null) {
  return value?.trim().toLowerCase() || ''
}

function scoreBucket(text: string, bucket: ProblemSpaceBucketKey) {
  return BUCKET_PATTERNS[bucket].reduce((score, pattern) => score + (pattern.test(text) ? 1 : 0), 0)
}

export function resolveProblemSpaceBucket(topic?: string | null, prompt?: string | null): ProblemSpaceBucketKey {
  const topicText = topic?.trim() || ''
  const directMatch = LEGACY_TOPIC_BUCKET_MAP[topicText]
  if (directMatch) {
    return directMatch
  }

  const text = normalizeText(`${topic || ''} ${prompt || ''}`)
  const scores = PROBLEM_SPACE_BUCKETS.map(bucket => ({
    key: bucket.key,
    score: scoreBucket(text, bucket.key)
  }))
  scores.sort((left, right) => right.score - left.score)

  if (scores[0]?.score) {
    return scores[0].key
  }

  return 'capability-validation'
}

export function getProblemSpaceBucketLabel(key: ProblemSpaceBucketKey) {
  return PROBLEM_SPACE_BUCKETS.find(bucket => bucket.key === key)?.label || '能力确认'
}
