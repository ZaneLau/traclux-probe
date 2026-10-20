import type { WikiObjectAuthoringDefinition } from '~/types/wiki'

export const engineWikiObjects = [
  {
    objectId: 'page.engine',
    displayName: '品牌表现',
    type: 'page',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现', 'AI 可见度总览'],
    sourceOfTruth: [
      { kind: 'backend-contract', ref: '/api/v1/workbench/brand-performance', note: '品牌表现正式数据合同' },
      { kind: 'route', ref: '/engine', note: '品牌表现正式路由' }
    ],
    userSourceLabel: '产品定义与品牌表现页接口',
    summary: '品牌表现页把总览结果进一步拆到主题、平台和情绪三个维度。',
    contexts: [
      {
        pageContext: 'engine.page',
        definition: '品牌表现页用于拆解品牌结果来自哪里。它把整体表现按主题、平台和情绪三个维度展开，帮助你定位问题发生在何处。',
        importance: '当总览告诉你结果正在变化时，这一页负责回答变化是由哪个维度带出来的。',
        readingGuide: '按主题看问题空间，按平台看模型差异，按情绪看回答倾向；不要把三个维度混成一个总分来读。',
        rules: [
          '这页适合做归因判断：先确认是哪个维度出问题，再进入更细的对象层。'
        ],
        boundaries: [
          '页面名大于单一指标，不能和 AI 可见度混为一体。'
        ],
        commonMisreads: ['不要把品牌表现理解成“所有品牌数据的全集”。']
      }
    ],
    nextSteps: [
      { label: '去主题维度看哪些问题空间最弱', route: '/engine', query: { tab: 'topics' } },
      { label: '去平台维度看哪个模型平台最值得优先补', route: '/engine', query: { tab: 'platforms' } }
    ]
  },
  {
    objectId: 'dimension.topic',
    displayName: '主题',
    type: 'dimension',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现', '问题分析'],
    sourceOfTruth: [
      { kind: 'field', ref: 'topicSummaryRows', note: '主题维度正式投影' }
    ],
    userSourceLabel: '品牌表现主题维度口径',
    summary: '主题维度用于把品牌面对的问题空间按稳定话题拆开看。',
    contexts: [
      {
        pageContext: 'engine.topics',
        definition: '主题表示品牌相关问题中反复出现、可以持续跟踪的一类稳定话题，而不是某一条单独问法。',
        importance: '当你知道哪个主题长期偏弱，就能更快判断问题更像出在内容、口碑还是产品信息覆盖上。',
        readingGuide: '先看样本量，再看 AI 可见度、提及率、推荐率和 Top3 出现率，不要只根据一条问题标题下结论。',
        rules: [
          '主题更适合做结构判断，具体原因仍要回到问题分析里的单条问法和证据。'
        ],
        boundaries: [
          '主题不是单条真实问题。',
          '一个主题下面可以包含多条具体问法。'
        ],
        commonMisreads: ['不要把主题、真实问题和问题簇当成一个层次。']
      }
    ],
    nextSteps: [
      { label: '去问题分析看这个主题下具体都在问什么', route: '/probe-analysis' }
    ]
  },
  {
    objectId: 'dimension.platform',
    displayName: '平台',
    type: 'dimension',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现', 'AI 可见度总览'],
    sourceOfTruth: [
      { kind: 'field', ref: 'modelPerformance', note: '平台维度正式投影' }
    ],
    userSourceLabel: '品牌表现平台维度口径',
    summary: '平台维度用于比较同一品牌在不同模型平台上的表现差异。',
    contexts: [
      {
        pageContext: 'engine.platforms',
        definition: '平台维度把不同模型平台拆开看，帮助你判断问题是普遍存在，还是集中发生在某一个平台生态里。',
        importance: '如果平台差异明显，后续优化优先级通常不该平均分配。',
        readingGuide: '先看平台总览，再切换到单项指标比较强弱，最后找出最值得优先补的平台。',
        rules: [
          '平台之间应优先用同一指标、同一窗口直接对照。'
        ],
        boundaries: [
          '总览中的“模型表现”只是这一维度的摘要入口。',
          '平台差异不直接说明主题差异。'
        ],
        commonMisreads: ['不要把平台维度和总览摘要里的入口名称读成两套不同概念。']
      }
    ],
    nextSteps: [
      { label: '回总览查看模型表现摘要', route: '/dashboard' },
      { label: '继续按平台对照当前四项核心指标', route: '/engine', query: { tab: 'platforms' } }
    ]
  },
  {
    objectId: 'dimension.sentiment',
    displayName: '情绪',
    type: 'dimension',
    status: 'active',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'sentiment', note: '情绪维度正式投影' }
    ],
    userSourceLabel: '品牌表现情绪口径',
    aliases: ['情感', 'sentiment'],
    summary: '情绪维度用于观察品牌相关回答整体更偏正向还是偏非正向。',
    contexts: [
      {
        pageContext: 'engine.sentiment',
        definition: '情绪维度用于观察品牌相关回答的整体倾向，帮助你判断当前回答是更偏正向支持，还是仍被非正向信息拖住。',
        importance: '即使提及率不低，如果回答倾向长期偏弱，也会拖累推荐结果和用户最终印象。',
        readingGuide: '先看正向率，再看竞品对比和排名，最后确认非正向部分是不是集中在某一类压力上。',
        formula: '当前页主读口径是正向率：正向率 = 正向回答数 / 当前有效样本数 × 100%；非正向 = 100% - 正向率。',
        interpretation: [
          {
            label: '正向率更高时',
            text: '说明品牌相关回答更常以支持、认可或可选对象的方式出现。'
          },
          {
            label: '正向率更低时',
            text: '说明回答里仍有更多保留、否定或弱支持倾向，需要继续回到问题和证据层排查。'
          }
        ],
        rules: [
          '当前页围绕正向率展开解读，适合先看总体倾向，再回到具体问题追原因。'
        ],
        boundaries: [
          '情绪不是品牌口碑的唯一结论。',
          '情绪结果仍应结合主题、平台和证据一起判断。'
        ],
        commonMisreads: ['不要把情绪高低直接等同于所有主题都表现一致。']
      }
    ],
    nextSteps: [
      { label: '去问题分析看哪些真实问题在拉低回答倾向', route: '/probe-analysis' }
    ]
  },
  {
    objectId: 'metric.average_position',
    displayName: '平均位次',
    type: 'metric',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现', '问题分析'],
    sourceOfTruth: [
      { kind: 'field', ref: 'averagePosition', note: '平均位次正式投影字段' }
    ],
    userSourceLabel: '工作台指标口径',
    summary: '平均位次用于衡量品牌通常出现在回答的什么位置。',
    contexts: [
      {
        pageContext: 'engine.topics',
        definition: '平均位次表示品牌进入回答之后，通常出现在更靠前、中段还是更靠后的位置。',
        importance: '被提到不等于站位足够好。平均位次能帮助你判断品牌虽然进了回答，但是否仍停留在不容易先被用户看到的位置。',
        readingGuide: '先和 AI 可见度或 Top3 出现率一起看；如果品牌已经能进入回答，但平均位次仍偏后，说明它还没有真正站稳前排。',
        formula: '平均位次 = 当前样本中品牌已进入回答的位次总和 / 已形成位次的样本数。',
        interpretation: [
          {
            label: '数值更小',
            text: '通常表示品牌出现得更靠前，用户更容易第一时间看到。'
          },
          {
            label: '数值更大',
            text: '通常表示品牌更多停留在回答中后段，虽然出现了，但不够显眼。'
          }
        ],
        rules: [
          '只有品牌已进入回答并形成位次时，这个指标才有意义。',
          '它更适合同一维度内的横向比较。'
        ],
        boundaries: [
          '平均位次只在形成可计算样本时有意义。',
          '它描述的是位置，不描述推荐语气。'
        ],
        commonMisreads: ['不要把位次靠后直接理解成完全没有被提到。']
      }
    ],
    nextSteps: [
      { label: '继续看 Top3 出现率，确认前排稳定度' },
      { label: '去问题分析看哪些真实问题里位置最差', route: '/probe-analysis' }
    ]
  },
  {
    objectId: 'metric.citation_share',
    displayName: '引用占比',
    type: 'metric',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现', '引用来源', '问题分析'],
    sourceOfTruth: [
      { kind: 'field', ref: 'citationShare', note: '引用占比正式投影字段' }
    ],
    userSourceLabel: '工作台指标口径',
    summary: '引用占比用于衡量当前对象在证据集合里占了多大份额。',
    contexts: [
      {
        pageContext: 'sources.list',
        definition: '引用占比表示在当前窗口的证据集合里，某个对象占用了多少引用份额。',
        importance: '它能帮助你判断哪类来源、哪个域名或哪类问题正在更强地支撑当前结果。',
        readingGuide: '先看占比高低，再判断这个占比来自少数强势来源，还是来自更分散的证据结构。',
        formula: '引用占比 = 当前对象的引用数 / 当前窗口总引用数 × 100%。',
        interpretation: [
          {
            label: '占比更高时',
            text: '说明当前结果更依赖这一类来源或对象。'
          },
          {
            label: '占比更低时',
            text: '说明支撑结果的证据更分散，或主要来自其他对象。'
          }
        ],
        rules: [
          '引用占比应在同一时间窗口和同一对象层级下比较。',
          '它适合判断证据结构，不单独代表内容质量。'
        ],
        boundaries: [
          '引用占比不是流量份额。',
          '占比高，也不等于内容质量一定更好。'
        ],
        commonMisreads: ['不要把引用占比和提及率混成同一个指标。']
      }
    ],
    nextSteps: [
      { label: '去引用来源页确认高占比来源具体是谁', route: '/sources' }
    ]
  },
  {
    objectId: 'metric.share_of_voice',
    displayName: '声量占比',
    type: 'metric',
    status: 'review',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'shareOfVoice', note: '声量占比正式投影字段' }
    ],
    summary: '声量占比当前仍属于延后对象，先保留治理位。',
    contexts: [
      {
        pageContext: 'engine.platforms',
        definition: '它意图表达当前对象在对话或来源里的相对份额。',
        importance: '因为用户容易和提及率、引用占比混淆，当前先不开放正式 Wiki 入口。',
        readingGuide: '后续若启用，必须先把它和提及率、引用占比的边界讲清楚。',
        boundaries: ['当前阶段不进入正式页面解释入口。'],
        commonMisreads: ['不要提前把它包装成稳定营销术语。']
      }
    ],
    nextSteps: [
      { label: '暂不开放正式下一步' }
    ]
  }
] satisfies WikiObjectAuthoringDefinition[]
