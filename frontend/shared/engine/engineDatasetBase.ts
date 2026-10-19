import type {
  BrandPromptTopicSeed,
  BrandTrackingSeed,
  CitationCategory,
  CitationDomainSeed,
  ChatEvidenceBrandHitSeed,
  ChatEvidenceCitationSeed,
  ChatEvidenceSeed,
  EngineDataset,
  ProbeSeed,
  ProbeStatus,
  ProbeTagSeed,
  SourceDomainSeed,
  SourceType,
  SourceUrlSeed
} from '../../app/types/engine'
import { buildTheaterByBrand } from './engineTheaterMock'

// 全站唯一假数据源（SSOT）：
// - App 端与 BFF 端必须都从这里读取
// - 新页面新增字段时，仅在此扩展，再由消费端按需映射

const BRAND_OPTIONS = [
  { key: '摩恩', label: '摩恩' },
  { key: '卡贝', label: '卡贝' },
  { key: '正泰', label: '正泰' },
  { key: '美厨物语', label: '美厨物语' }
]

const PLATFORM_SCOPE_OPTIONS = [
  { key: 'ChatGPT', volume: 254 },
  { key: 'Gemini', volume: 188 },
  { key: 'Perplexity', volume: 172 },
  { key: 'DeepSeek', volume: 141 }
]

const BRAND_KEYWORD_SEEDS: Record<string, string[]> = {
  摩恩: ['摩恩', 'Moen', '摩恩花洒', '摩恩龙头'],
  卡贝: ['卡贝', 'KABEI', '卡贝五金', '卡贝卫浴'],
  正泰: ['正泰', 'CHINT', '正泰开关', '正泰断路器'],
  美厨物语: ['美厨物语', '美厨物语厨具', '美厨物语锅具', '美厨物语餐具']
}

const COMPETITOR_BY_BRAND: Record<string, string[]> = {
  摩恩: ['科勒', '九牧', '箭牌', '汉斯格雅', '恒洁'],
  卡贝: ['九牧', '箭牌', '太力', '悍高', '欧琳'],
  正泰: ['公牛', '德力西', '施耐德电气', '西门子', '罗格朗'],
  美厨物语: ['苏泊尔', '爱仕达', '炊大皇', '康巴赫', '双立人']
}

interface RawBrandPromptTopicSeed {
  topic: string
  prompts: string[]
}

const BRAND_PROMPT_TOPIC_SOURCE: Record<string, RawBrandPromptTopicSeed[]> = {
  摩恩: [
    {
      topic: '材质与做工可靠',
      prompts: [
        '摩恩龙头用三年后阀芯稳定吗',
        '摩恩花洒电镀层容易起皮吗',
        '摩恩不锈钢和黄铜件哪个更耐用',
        '摩恩五金在潮湿浴室会不会生锈',
        '摩恩高频使用场景下故障率高吗',
        '摩恩不同系列做工差异大不大'
      ]
    },
    {
      topic: '出水体验与恒温表现',
      prompts: [
        '摩恩恒温花洒温差控制稳定吗',
        '摩恩花洒在低水压楼层出水会变弱吗',
        '摩恩顶喷和手持切换是否顺滑',
        '摩恩龙头防溅效果到底怎么样',
        '摩恩抽拉龙头回弹顺不顺',
        '摩恩多档出水模式日常实用吗'
      ]
    },
    {
      topic: '安装复杂度',
      prompts: [
        '摩恩花洒老房改造需要改管吗',
        '摩恩龙头安装需要专业师傅吗',
        '摩恩非标接口适配难不难',
        '摩恩全屋更换安装工期一般多久',
        '摩恩安装后漏水问题常见吗',
        '摩恩安装说明对新手友好吗'
      ]
    },
    {
      topic: '售后响应效率',
      prompts: [
        '摩恩售后报修多久会回电',
        '摩恩配件补发周期一般几天',
        '摩恩跨平台购买售后会踢皮球吗',
        '摩恩上门检修覆盖哪些城市',
        '摩恩质保期内更换阀芯收费吗',
        '摩恩客服能否一次性定位问题'
      ]
    },
    {
      topic: '价格与预算压力',
      prompts: [
        '摩恩和九牧同档位总预算差多少',
        '摩恩高端系列溢价主要体现在什么',
        '摩恩后期配件维护成本高吗',
        '摩恩促销期入手和日常价差距大吗',
        '摩恩整套卫浴预算怎么配更稳',
        '摩恩长期使用综合成本值得吗'
      ]
    }
  ],
  卡贝: [
    {
      topic: '款式丰富与性价比',
      prompts: [
        '卡贝家装五金款式覆盖够全吗',
        '卡贝和九牧同预算谁更划算',
        '卡贝入门组合是否适合新房首装',
        '卡贝不同价位段品质差异明显吗',
        '卡贝小户型整套预算大概要多少',
        '卡贝为什么常被推荐给预算敏感用户'
      ]
    },
    {
      topic: '收纳效率与安装便捷',
      prompts: [
        '卡贝浴室收纳套装适合小户型吗',
        '卡贝免打孔安装会不会掉',
        '卡贝厨房收纳方案日常实用吗',
        '卡贝转角收纳空间利用率高吗',
        '卡贝新手自己安装难不难',
        '卡贝挂件承重在真实场景够用吗'
      ]
    },
    {
      topic: '防锈与批次稳定',
      prompts: [
        '卡贝五金在南方回南天会生锈吗',
        '卡贝黑色涂层长期使用会掉漆吗',
        '卡贝不同批次防锈表现一致吗',
        '卡贝高频开合场景耐久如何',
        '卡贝盐雾环境下寿命表现怎么样',
        '卡贝防锈问题售后处理快吗'
      ]
    },
    {
      topic: '售后时效',
      prompts: [
        '卡贝售后工单多久能闭环',
        '卡贝缺件补发一般几天',
        '卡贝跨电商平台售后口径一致吗',
        '卡贝安装问题能否远程判定',
        '卡贝退换货流程复杂吗',
        '卡贝客服高峰期响应会延迟吗'
      ]
    },
    {
      topic: '预算友好度',
      prompts: [
        '卡贝整套卫浴五金预算如何分配更省',
        '卡贝活动价和日常价差多少',
        '卡贝租房改造性价比高吗',
        '卡贝大件小件组合怎么买更省',
        '卡贝中端款和高端款是否值得升级',
        '卡贝长期维护成本会不会反超竞品'
      ]
    }
  ],
  正泰: [
    {
      topic: '安全可靠与认证体系',
      prompts: [
        '正泰断路器安全口碑怎么样',
        '正泰家装开关通过了哪些认证',
        '正泰长期运行稳定性靠谱吗',
        '正泰漏保误跳闸概率高吗',
        '正泰在高负载场景安全表现如何',
        '正泰工程项目为何常被推荐'
      ]
    },
    {
      topic: '供货稳定与工程适配',
      prompts: [
        '正泰大批量采购供货稳不稳',
        '正泰工程项目交期通常多久',
        '正泰跨区域供货一致性如何',
        '正泰项目补货响应速度快吗',
        '正泰工程配套型号覆盖全吗',
        '正泰渠道备货差异会影响交付吗'
      ]
    },
    {
      topic: '型号理解门槛',
      prompts: [
        '正泰型号命名怎么快速看懂',
        '正泰空开曲线怎么选不踩坑',
        '正泰家庭回路选型步骤是什么',
        '正泰不同系列兼容性差异大吗',
        '正泰型号太多时先看哪些参数',
        '正泰新手选型最容易错在哪'
      ]
    },
    {
      topic: '安装专业门槛',
      prompts: [
        '正泰安装必须找专业电工吗',
        '正泰老房改造安装风险高吗',
        '正泰接线标准复杂吗',
        '正泰安装说明对普通用户友好吗',
        '正泰安装错误会带来哪些后果',
        '正泰有没有降低安装门槛的方案'
      ]
    },
    {
      topic: '价格梯度认知',
      prompts: [
        '正泰和公牛同档位谁更值',
        '正泰不同系列价差对应哪些能力',
        '正泰工程采购议价空间大吗',
        '正泰活动期和常规期价格差多少',
        '正泰全屋电气预算怎么配更合理',
        '正泰长期使用综合成本高不高'
      ]
    }
  ],
  美厨物语: [
    {
      topic: '上手友好与使用体验',
      prompts: [
        '美厨物语新手做饭好上手吗',
        '美厨物语套锅日常使用顺手吗',
        '美厨物语清洗难度高不高',
        '美厨物语轻量锅具适合老人吗',
        '美厨物语厨房小白选哪套更稳',
        '美厨物语频繁使用体验会下降吗'
      ]
    },
    {
      topic: '颜值与套装搭配',
      prompts: [
        '美厨物语哪套锅具颜值最高',
        '美厨物语奶油色系列实用吗',
        '美厨物语礼赠场景推荐哪套',
        '美厨物语套装搭配是否容易重复',
        '美厨物语厨房风格统一效果好吗',
        '美厨物语拍照出片与实用性能兼顾吗'
      ]
    },
    {
      topic: '涂层耐久争议',
      prompts: [
        '美厨物语不粘涂层寿命大概多久',
        '美厨物语高温爆炒会伤涂层吗',
        '美厨物语金属铲能用吗',
        '美厨物语涂层脱落投诉多不多',
        '美厨物语不同系列耐久差距大吗',
        '美厨物语正确养护能延长多久寿命'
      ]
    },
    {
      topic: '售后口径一致性',
      prompts: [
        '美厨物语售后响应快不快',
        '美厨物语多平台购买售后一致吗',
        '美厨物语退换货流程麻烦吗',
        '美厨物语补件周期一般多久',
        '美厨物语客服能否给出明确处理时限',
        '美厨物语售后争议主要集中在哪'
      ]
    },
    {
      topic: '导热均匀表现',
      prompts: [
        '美厨物语导热均匀吗',
        '美厨物语中小火烹饪容易掌控吗',
        '美厨物语高火场景会糊锅吗',
        '美厨物语锅底受热稳定性如何',
        '美厨物语煎炒炖三种场景都适配吗',
        '美厨物语电磁炉和燃气灶表现一致吗'
      ]
    }
  ]
}

const BRAND_PROMPT_TOPIC_SEEDS: Record<string, BrandPromptTopicSeed[]> = Object.fromEntries(
  Object.entries(BRAND_PROMPT_TOPIC_SOURCE).map(([brand, topics]) => {
    return [brand, topics.map((topicSeed, topicIndex) => ({
      topicId: `${brand}-topic-${topicIndex + 1}`,
      ...topicSeed
    }))]
  })
) as Record<string, BrandPromptTopicSeed[]>

const CITATION_DOMAIN_SEEDS_BY_BRAND: Record<string, CitationDomainSeed[]> = {
  摩恩: [
    { domain: 'baike.baidu.com', category: 'earned', icon: '百', subdomains: 8 },
    { domain: 'zhihu.com', category: 'earned', icon: '知', subdomains: 6 },
    { domain: 'pconline.com.cn', category: 'earned', icon: '评', subdomains: 3 },
    { domain: 'sina.com.cn', category: 'earned', icon: '新', subdomains: 5 },
    { domain: 'moen.cn', category: 'owned', icon: 'M', subdomains: 4 },
    { domain: 'xiaohongshu.com', category: 'social', icon: '红', subdomains: 7 },
    { domain: 'weibo.com', category: 'social', icon: '微', subdomains: 9 },
    { domain: 'kohler.com.cn', category: 'competition', icon: 'K', subdomains: 3 },
    { domain: 'jomoo.com.cn', category: 'competition', icon: '九', subdomains: 3 },
    { domain: 'arrowhome.com.cn', category: 'competition', icon: '箭', subdomains: 2 }
  ],
  卡贝: [
    { domain: 'baike.baidu.com', category: 'earned', icon: '百', subdomains: 8 },
    { domain: 'zhihu.com', category: 'earned', icon: '知', subdomains: 6 },
    { domain: 'soufun.com', category: 'earned', icon: '房', subdomains: 4 },
    { domain: 'smzdm.com', category: 'earned', icon: '值', subdomains: 5 },
    { domain: 'kabei.com', category: 'owned', icon: '卡', subdomains: 4 },
    { domain: 'xiaohongshu.com', category: 'social', icon: '红', subdomains: 7 },
    { domain: 'bilibili.com', category: 'social', icon: 'B', subdomains: 6 },
    { domain: 'jomoo.com.cn', category: 'competition', icon: '九', subdomains: 3 },
    { domain: 'arrowhome.com.cn', category: 'competition', icon: '箭', subdomains: 2 },
    { domain: 'higold.com', category: 'competition', icon: '悍', subdomains: 2 }
  ],
  正泰: [
    { domain: 'baike.baidu.com', category: 'earned', icon: '百', subdomains: 8 },
    { domain: 'zhihu.com', category: 'earned', icon: '知', subdomains: 6 },
    { domain: 'elecfans.com', category: 'earned', icon: '电', subdomains: 4 },
    { domain: 'sohu.com', category: 'earned', icon: '狐', subdomains: 5 },
    { domain: 'chint.com', category: 'owned', icon: '正', subdomains: 5 },
    { domain: 'weibo.com', category: 'social', icon: '微', subdomains: 9 },
    { domain: 'douyin.com', category: 'social', icon: '抖', subdomains: 5 },
    { domain: 'gongniu.cn', category: 'competition', icon: '公', subdomains: 3 },
    { domain: 'delixi-electric.com', category: 'competition', icon: '德', subdomains: 3 },
    { domain: 'siemens.com.cn', category: 'competition', icon: '西', subdomains: 4 }
  ],
  美厨物语: [
    { domain: 'baike.baidu.com', category: 'earned', icon: '百', subdomains: 8 },
    { domain: 'zhihu.com', category: 'earned', icon: '知', subdomains: 6 },
    { domain: 'meishi.cc', category: 'earned', icon: '食', subdomains: 3 },
    { domain: 'ifanr.com', category: 'earned', icon: '范', subdomains: 2 },
    { domain: 'meichu-story.cn', category: 'owned', icon: '厨', subdomains: 4 },
    { domain: 'xiaohongshu.com', category: 'social', icon: '红', subdomains: 7 },
    { domain: 'bilibili.com', category: 'social', icon: 'B', subdomains: 6 },
    { domain: 'supor.com.cn', category: 'competition', icon: '苏', subdomains: 3 },
    { domain: 'asd.com.cn', category: 'competition', icon: '爱', subdomains: 2 },
    { domain: 'kbh.com.cn', category: 'competition', icon: '康', subdomains: 2 }
  ]
}

const PROBE_TAGS: ProbeTagSeed[] = [
  { id: 'brand', label: '品牌词', color: '#dbeafe' },
  { id: 'comparison', label: '竞品对比', color: '#ede9fe' },
  { id: 'price', label: '价格预算', color: '#ffedd5' },
  { id: 'experience', label: '体验', color: '#dcfce7' },
  { id: 'install', label: '安装', color: '#fee2e2' },
  { id: 'after-sale', label: '售后', color: '#fef3c7' },
  { id: 'reliability', label: '可靠性', color: '#e0e7ff' },
  { id: 'recommendation', label: '推荐', color: '#cffafe' }
]

const BRAND_DOMAIN_MAP: Record<string, string> = {
  摩恩: 'moen.cn',
  卡贝: 'kabei.com',
  正泰: 'chint.com',
  美厨物语: 'meichu-story.cn'
}

const MODEL_ROTATION = ['ChatGPT', 'Gemini', 'Perplexity', 'DeepSeek'] as const
const ENGINE_DATASET_META = {
  dataVersion: 'reverse-p0-2026-04-09',
  generatedAt: '2026-04-09T21:00:00+08:00',
  seed: 'geoprobe-engine-ssot-v1'
} as const

function normalizeEntityToken(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9\u4e00-\u9fa5]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function buildTaskId(brand: string) {
  return `task-${normalizeEntityToken(brand)}-p0`
}

function buildInvocationId(brand: string, probeId: string) {
  return `inv-${normalizeEntityToken(brand)}-${normalizeEntityToken(probeId)}`
}

function hashValue(input: string): number {
  let hash = 2166136261
  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return Math.abs(hash >>> 0)
}

function clamp(value: number, min: number, max: number) {
  return Math.max(min, Math.min(max, value))
}

function round1(value: number) {
  return Math.round(value * 10) / 10
}

function normalizeTopicToken(topic: string) {
  return topic.replace(/\s+/g, '').replace(/[、/]/g, '-')
}

function inferTags(topic: string, prompt: string): string[] {
  const text = `${topic}${prompt}`
  const tags = new Set<string>()

  if (/(品牌|定位|心智|口碑)/.test(text)) tags.add('brand')
  if (/(对比|替代|差异|谁更|竞品)/.test(text)) tags.add('comparison')
  if (/(价格|预算|成本|溢价|价差|性价比)/.test(text)) tags.add('price')
  if (/(体验|顺滑|实用|上手|导热|出水)/.test(text)) tags.add('experience')
  if (/(安装|改造|接线|适配|工期)/.test(text)) tags.add('install')
  if (/(售后|客服|报修|补发|质保)/.test(text)) tags.add('after-sale')
  if (/(稳定|耐用|故障|风险|防锈|可靠)/.test(text)) tags.add('reliability')
  if (/(推荐|选型|怎么选|值得)/.test(text)) tags.add('recommendation')

  if (!tags.size) {
    tags.add('brand')
    tags.add('recommendation')
  }

  return Array.from(tags).slice(0, 3)
}

function resolveProbeStatus(seed: number, index: number): ProbeStatus {
  if (index % 9 === 0) return 'inactive'
  if ((seed + index) % 4 === 0) return 'suggested'
  return 'active'
}

function resolveSourceType(category: CitationCategory, index: number): SourceType {
  if (category === 'owned') return 'you'
  if (category === 'social') return 'ugc'
  if (category === 'competition') return 'corporate'
  return index % 3 === 0 ? 'reference' : 'editorial'
}

function buildProbeSeedsByBrand(): Record<string, ProbeSeed[]> {
  return Object.fromEntries(
    Object.entries(BRAND_PROMPT_TOPIC_SEEDS).map(([brand, topics]) => {
      const rows: ProbeSeed[] = []
      const taskId = buildTaskId(brand)

      topics.forEach((topicSeed, topicIndex) => {
        topicSeed.prompts.forEach((prompt, promptIndex) => {
          const seed = hashValue(`${brand}-${topicSeed.topic}-${prompt}-${topicIndex}-${promptIndex}`)
          const visibility = Math.round(clamp(46 + Math.sin(seed * 0.00047 + topicIndex * 0.41) * 28, 0, 100))
          const sentiment = Math.round(clamp(52 + Math.cos(seed * 0.00051 + promptIndex * 0.37) * 23, 0, 100))
          const position = round1(clamp(1.2 + ((seed % 35) / 10), 1, 6.5))
          const mentions = Math.round(clamp(3 + (seed % 17), 0, 28))
          const volume = Math.round(clamp(80 + (seed % 980), 40, 1800))
          const createdDay = (seed % 25) + 1
          const probeId = `${brand}-${normalizeTopicToken(topicSeed.topic)}-${promptIndex + 1}`
          const tags = inferTags(topicSeed.topic, prompt)
          const visibilityTrend = Array.from({ length: 8 }, (_, trendIndex) => {
            return round1(clamp(
              visibility - 11 + trendIndex * 1.7 + Math.sin(seed * 0.00031 + trendIndex * 0.74) * 5.4,
              0,
              100
            ))
          })

          rows.push({
            id: probeId,
            brand,
            topicId: topicSeed.topicId,
            topic: topicSeed.topic,
            prompt,
            tags,
            status: resolveProbeStatus(seed, rows.length),
            location: 'CN',
            visibility,
            sentiment,
            position,
            mentions,
            volume,
            visibilityTrend,
            createdAt: `2026-03-${String(createdDay).padStart(2, '0')}T10:00:00+08:00`,
            refs: {
              taskId,
              probeId,
              invocationId: buildInvocationId(brand, probeId),
              topicId: topicSeed.topicId,
              tagIds: tags
            }
          })
        })
      })

      return [brand, rows]
    })
  )
}

function buildSourceDomainsByBrand(): Record<string, SourceDomainSeed[]> {
  return Object.fromEntries(
    Object.entries(CITATION_DOMAIN_SEEDS_BY_BRAND).map(([brand, seeds]) => {
      const taskId = buildTaskId(brand)
      const rows = seeds.map((seed, index) => {
        const hash = hashValue(`${brand}-${seed.domain}-${seed.category}-${index}`)
        const used = Math.round(clamp(22 + Math.cos(hash * 0.00039 + index * 0.47) * 46, 8, 98))
        const avgCitations = round1(clamp(0.1 + (hash % 25) / 10, 0.1, 3.6))
        const trend = Array.from({ length: 7 }, (_, trendIndex) => {
          return Math.round(clamp(used - 16 + trendIndex * 3 + Math.sin(hash * 0.00021 + trendIndex) * 8, 0, 100))
        })
        const sourceDomainId = `${brand}-source-domain-${index + 1}`
        const share = round1(clamp(used * 0.2, 1, 100))
        const delta = round1((trend[trend.length - 1] || used) - (trend[0] || used))

        return {
          id: sourceDomainId,
          brand,
          domain: seed.domain,
          type: resolveSourceType(seed.category, index),
          used,
          avgCitations,
          share,
          delta,
          trend,
          refs: {
            taskId,
            sourceDomainId
          }
        }
      })

      return [brand, rows]
    })
  )
}

function buildSourceUrlsByBrand(probeSeedsByBrand: Record<string, ProbeSeed[]>, domainsByBrand: Record<string, SourceDomainSeed[]>) {
  return Object.fromEntries(
    Object.entries(domainsByBrand).map(([brand, domainRows]) => {
      const probes = probeSeedsByBrand[brand] || []
      const taskId = buildTaskId(brand)
      const urls: SourceUrlSeed[] = []

      domainRows.forEach((domain, domainIndex) => {
        const probeA = probes[(domainIndex * 2) % Math.max(1, probes.length)]
        const probeB = probes[(domainIndex * 2 + 1) % Math.max(1, probes.length)]

        const linkedProbes = [probeA, probeB].filter(Boolean) as ProbeSeed[]
        linkedProbes.forEach((probe, probeIndex) => {
          const hash = hashValue(`${brand}-${domain.domain}-${probe.id}-${probeIndex}`)
          const sourceUrlId = `${domain.id}-url-${probeIndex + 1}`
          const share = round1(clamp((domain.used - 12 + (hash % 23)) * 0.11, 0.6, 100))
          const delta = round1(clamp(Math.sin(hash * 0.00057 + probeIndex) * 2.8, -9.8, 9.8))
          urls.push({
            id: sourceUrlId,
            brand,
            domainId: domain.id,
            url: `https://${domain.domain}/insight/${encodeURIComponent(probe.topic)}-${(hash % 90) + 10}`,
            type: domain.type,
            used: Math.round(clamp(domain.used - 12 + (hash % 23), 4, 100)),
            avgCitations: round1(clamp(domain.avgCitations - 0.3 + (hash % 9) * 0.11, 0, 4.2)),
            share,
            delta,
            refs: {
              taskId,
              probeId: probe.id,
              topicId: probe.topicId,
              sourceDomainId: domain.id,
              sourceUrlId
            }
          })
        })
      })

      return [brand, urls]
    })
  )
}

function buildBrandTrackingByWorkspaceBrand(): Record<string, BrandTrackingSeed[]> {
  return Object.fromEntries(
    BRAND_OPTIONS.map((brandOption) => {
      const workspaceBrand = brandOption.key
      const competitors = COMPETITOR_BY_BRAND[workspaceBrand] || []
      const tracked: BrandTrackingSeed[] = []
      const taskId = buildTaskId(workspaceBrand)

      const primarySeed = hashValue(`${workspaceBrand}-tracked-primary`)
      tracked.push({
        id: `${workspaceBrand}-tracked-primary`,
        workspaceBrand,
        brand: workspaceBrand,
        domain: BRAND_DOMAIN_MAP[workspaceBrand] || `${workspaceBrand}.com`,
        mentions: Math.round(clamp(14 + (primarySeed % 18), 6, 42)),
        visibility: Math.round(clamp(56 + Math.sin(primarySeed * 0.00037) * 22, 30, 100)),
        sentiment: Math.round(clamp(58 + Math.cos(primarySeed * 0.00041) * 18, 20, 99)),
        position: round1(clamp(1.2 + ((primarySeed % 28) / 10), 1, 5.6)),
        status: 'tracked',
        refs: {
          taskId,
          brandTrackingId: `${workspaceBrand}-tracked-primary`
        }
      })

      competitors.forEach((competitor, index) => {
        const seed = hashValue(`${workspaceBrand}-${competitor}-${index}`)
        const brandTrackingId = `${workspaceBrand}-competitor-${index + 1}`
        tracked.push({
          id: brandTrackingId,
          workspaceBrand,
          brand: competitor,
          domain: `${encodeURIComponent(competitor)}.cn`.toLowerCase(),
          mentions: Math.round(clamp(4 + (seed % 9), 1, 18)),
          visibility: Math.round(clamp(30 + Math.sin(seed * 0.00029 + index * 0.43) * 25, 8, 90)),
          sentiment: Math.round(clamp(46 + Math.cos(seed * 0.00035 + index * 0.27) * 22, 10, 92)),
          position: round1(clamp(1.5 + ((seed % 38) / 10), 1, 7.2)),
          status: index < 3 ? 'suggested' : (index === 3 ? 'tracked' : 'rejected'),
          refs: {
            taskId,
            brandTrackingId
          }
        })
      })

      return [workspaceBrand, tracked]
    })
  )
}

function buildChatEvidenceByBrand(
  probeSeedsByBrand: Record<string, ProbeSeed[]>,
  sourceUrlsByBrand: Record<string, SourceUrlSeed[]>,
  brandTrackingByWorkspaceBrand: Record<string, BrandTrackingSeed[]>
): Record<string, ChatEvidenceSeed[]> {
  return Object.fromEntries(
    BRAND_OPTIONS.map((brandOption) => {
      const brand = brandOption.key
      const probes = probeSeedsByBrand[brand] || []
      const urls = sourceUrlsByBrand[brand] || []
      const trackedBrands = (brandTrackingByWorkspaceBrand[brand] || []).filter(item => item.status !== 'rejected')
      const chats: ChatEvidenceSeed[] = []
      const taskId = buildTaskId(brand)

      probes.slice(0, 20).forEach((probe, index) => {
        const seed = hashValue(`${brand}-${probe.id}-chat-${index}`)
        const citationCount = Math.min(4, Math.max(2, 2 + (seed % 3)))
        const chatId = `${brand}-chat-${index + 1}`
        const evidenceId = `evidence-${normalizeEntityToken(brand)}-${index + 1}`
        const citations: ChatEvidenceCitationSeed[] = Array.from({ length: citationCount }, (_, citationIndex) => {
          const url = urls.length > 0
            ? urls[(index * 2 + citationIndex) % urls.length]
            : undefined
          const fallbackUrl = `https://${encodeURIComponent(brand).toLowerCase()}.cn/insight/${probe.id}/${citationIndex + 1}`
          const citationId = `${chatId}-citation-${citationIndex + 1}`
          return {
            id: citationId,
            sourceId: url?.id || `${brand}-source-fallback-${citationIndex + 1}`,
            title: `${probe.topic}：${probe.prompt.slice(0, 16)}…`,
            url: url?.url || fallbackUrl,
            refs: {
              taskId,
              probeId: probe.id,
              evidenceId,
              topicId: probe.topicId,
              sourceDomainId: url?.domainId,
              sourceUrlId: url?.id,
              chatId,
              citationId
            }
          }
        })
        const summaryDomains = Array.from(new Set(
          citations.map(item => item.url.replace(/^https?:\/\//, '').split('/')[0])
        )).slice(0, 2)

        const rival = trackedBrands[(index + 1) % Math.max(1, trackedBrands.length)]
        const brandHits: ChatEvidenceBrandHitSeed[] = [
          {
            brand,
            score: Math.round(clamp(probe.sentiment + Math.sin(seed * 0.00037) * 8, 45, 98))
          },
          {
            brand: rival?.brand || brand,
            score: Math.round(clamp(probe.sentiment - 11 + Math.cos(seed * 0.00031) * 9, 20, 88))
          }
        ]

        chats.push({
          id: chatId,
          brand,
          probeId: probe.id,
          model: MODEL_ROTATION[index % MODEL_ROTATION.length] || 'ChatGPT-4.1',
          query: probe.prompt,
          answer: `围绕「${probe.topic}」问题，当前模型更偏向将 ${brand} 描述为 ${probe.sentiment >= 60 ? '稳定、可选' : '有待验证'} 的方案，并建议结合预算与安装条件做进一步筛选。`,
          summary: [
            `${brand} 在该问题下命中可见度 ${probe.visibility}%`,
            `主要引用源集中在 ${summaryDomains.join('、') || '暂无'}`,
            probe.position <= 2.5 ? '回答排序处于前列，可继续放大该探针覆盖' : '回答排序仍有提升空间，建议补充证据页'
          ],
          createdAt: `2026-04-${String((index % 8) + 1).padStart(2, '0')}T${String(9 + (index % 10)).padStart(2, '0')}:30:00+08:00`,
          citations,
          brandHits,
          refs: {
            taskId,
            probeId: probe.id,
            evidenceId,
            invocationId: buildInvocationId(brand, probe.id),
            topicId: probe.topicId,
            chatId
          }
        })
      })

      return [brand, chats]
    })
  )
}

const PROBE_SEEDS_BY_BRAND = buildProbeSeedsByBrand()
const SOURCE_DOMAINS_BY_BRAND = buildSourceDomainsByBrand()
const SOURCE_URLS_BY_BRAND = buildSourceUrlsByBrand(PROBE_SEEDS_BY_BRAND, SOURCE_DOMAINS_BY_BRAND)
const BRAND_TRACKING_BY_WORKSPACE_BRAND = buildBrandTrackingByWorkspaceBrand()
const CHAT_EVIDENCE_BY_BRAND = buildChatEvidenceByBrand(PROBE_SEEDS_BY_BRAND, SOURCE_URLS_BY_BRAND, BRAND_TRACKING_BY_WORKSPACE_BRAND)
const THEATER_BY_BRAND = buildTheaterByBrand({
  brandOptions: BRAND_OPTIONS,
  competitorByBrand: COMPETITOR_BY_BRAND,
  brandPromptTopicSeeds: BRAND_PROMPT_TOPIC_SEEDS,
  probeSeedsByBrand: PROBE_SEEDS_BY_BRAND,
  sourceDomainsByBrand: SOURCE_DOMAINS_BY_BRAND,
  sourceUrlsByBrand: SOURCE_URLS_BY_BRAND,
  chatEvidenceByBrand: CHAT_EVIDENCE_BY_BRAND,
  brandTrackingByWorkspaceBrand: BRAND_TRACKING_BY_WORKSPACE_BRAND
})

export const ENGINE_DATASET_BASE: EngineDataset = {
  meta: {
    dataVersion: ENGINE_DATASET_META.dataVersion,
    generatedAt: ENGINE_DATASET_META.generatedAt,
    seed: ENGINE_DATASET_META.seed
  },
  brandOptions: BRAND_OPTIONS,
  platformScopeOptions: PLATFORM_SCOPE_OPTIONS,
  brandKeywordSeeds: BRAND_KEYWORD_SEEDS,
  competitorByBrand: COMPETITOR_BY_BRAND,
  brandPromptTopicSeeds: BRAND_PROMPT_TOPIC_SEEDS,
  citationDomainSeedsByBrand: CITATION_DOMAIN_SEEDS_BY_BRAND,
  probeTags: PROBE_TAGS,
  probeSeedsByBrand: PROBE_SEEDS_BY_BRAND,
  sourceDomainsByBrand: SOURCE_DOMAINS_BY_BRAND,
  sourceUrlsByBrand: SOURCE_URLS_BY_BRAND,
  chatEvidenceByBrand: CHAT_EVIDENCE_BY_BRAND,
  brandTrackingByWorkspaceBrand: BRAND_TRACKING_BY_WORKSPACE_BRAND,
  theaterByBrand: THEATER_BY_BRAND
}

export function createEmptyEngineDataset(): EngineDataset {
  return {
    meta: {
      dataVersion: '',
      generatedAt: '',
      seed: ''
    },
    brandOptions: [],
    platformScopeOptions: [],
    brandKeywordSeeds: {},
    competitorByBrand: {},
    brandPromptTopicSeeds: {},
    citationDomainSeedsByBrand: {},
    probeTags: [],
    probeSeedsByBrand: {},
    sourceDomainsByBrand: {},
    sourceUrlsByBrand: {},
    chatEvidenceByBrand: {},
    brandTrackingByWorkspaceBrand: {},
    theaterByBrand: {}
  }
}

export function cloneEngineDataset(): EngineDataset {
  return JSON.parse(JSON.stringify(ENGINE_DATASET_BASE)) as EngineDataset
}
