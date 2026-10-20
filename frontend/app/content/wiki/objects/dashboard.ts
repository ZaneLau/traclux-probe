import type { WikiObjectAuthoringDefinition } from '~/types/wiki'

export const dashboardWikiObjects = [
  {
    objectId: 'page.dashboard',
    displayName: '可见度总览',
    type: 'page',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现', '引用来源'],
    sourceOfTruth: [
      { kind: 'backend-contract', ref: '/api/v1/workbench/overview', note: '总览正式数据合同' },
      { kind: 'route', ref: '/dashboard', note: '总览页正式路由' }
    ],
    userSourceLabel: '产品定义与总览页接口',
    aliases: ['AI 可见度总览'],
    summary: '可见度总览用于快速判断品牌当前在模型回答中的整体表现。',
    contexts: [
      {
        pageContext: 'dashboard.page',
        definition: '可见度总览是工作台的主入口。它把当前整体表现、变化方向、引用结构和平台差异放在同一页，帮助你先判断问题出在哪里。',
        importance: '当你需要先做整体判断时，这一页比逐个模块翻看更高效，因为它先告诉你结果是整体偏弱、正在改善，还是集中卡在某一环。',
        readingGuide: '先看 AI 可见度和三项拆分指标，再看趋势、引用来源摘要和模型表现摘要，最后决定去品牌表现还是引用来源继续下钻。',
        rules: [
          '这页适合先做整体判断，再决定去哪个页面继续拆解。',
          '同一时间窗口和同一筛选条件下比较，判断会更稳定。'
        ],
        boundaries: [
          '这页回答的是“整体怎样”，不是把所有细节一次展开。',
          '页面名和 AI 可见度这个单一指标不是同一件事。'
        ],
        commonMisreads: ['不要把“总览页”直接理解成一个指标卡。']
      }
    ],
    nextSteps: [
      { label: '去品牌表现拆开看主题、平台和情绪', route: '/engine' },
      { label: '去引用来源确认哪些站点在支撑结果', route: '/sources' }
    ]
  },
  {
    objectId: 'metric.ai_visibility',
    displayName: 'AI 可见度',
    type: 'metric',
    status: 'active',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'visibilityScore', note: 'AI 可见度正式投影字段' },
      { kind: 'backend-contract', ref: '/api/v1/workbench/overview', note: '总览指标合同' }
    ],
    userSourceLabel: '产品定义与工作台指标口径',
    summary: 'AI 可见度用于概括品牌进入回答、被推荐和站在前排的整体表现。',
    contexts: [
      {
        pageContext: 'dashboard.summary',
        definition: 'AI 可见度是探镜用于衡量品牌当前可见程度的综合指标，回答的是品牌是否稳定进入模型回答、是否进入推荐位，以及是否站在更靠前的位置。',
        importance: '当你需要先判断整体强弱时，它比单看某一项更稳，因为它把进入回答、被推荐和站位三个维度放在了同一口径下。',
        readingGuide: '先看当前分数，再看提及率、推荐率和 Top3 出现率分别是谁在拉高或拉低结果。',
        formula: 'AI 可见度 = 提及率 × 35% + 推荐率 × 40% + Top3 出现率 × 25%。',
        interpretation: [
          {
            label: '分数更高时',
            text: '通常意味着品牌不仅更容易进入回答，也更容易进入推荐位和前排位置。'
          },
          {
            label: '分数更低时',
            text: '通常意味着进入回答、被推荐或站位至少有一项明显偏弱。'
          },
          {
            label: '判断重点',
            text: '它适合先看整体，真正定位原因还要回到三项拆分指标。'
          }
        ],
        rules: [
          '三项基础指标应在同一时间窗口、同一筛选条件下解读。',
          '如果拆分指标缺失，AI 可见度不能被当成完整结论。'
        ],
        boundaries: [
          '它不是 SEO 排名，也不是流量或曝光量。',
          '它强调的是“有没有进入回答”和“出现得是否足够靠前”。'
        ],
        commonMisreads: ['不要把 AI 可见度理解成单纯的被提到次数。']
      }
    ],
    nextSteps: [
      { label: '查看三项拆分指标，确认是提及、推荐还是位次在拖累结果' },
      { label: '去品牌表现按主题和平台拆开看差异', route: '/engine' }
    ]
  },
  {
    objectId: 'metric.mention_rate',
    displayName: '提及率',
    type: 'metric',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'mentionRate', note: '提及率正式投影字段' }
    ],
    userSourceLabel: '工作台指标口径',
    summary: '提及率用于衡量品牌在当前样本中被模型提到的覆盖程度。',
    contexts: [
      {
        pageContext: 'dashboard.summary',
        definition: '提及率表示在当前有效样本中，有多少比例的问题会把该品牌带进回答。',
        importance: '如果提及率本身偏低，后面的推荐率和前排表现通常也很难稳定。',
        readingGuide: '先把它理解成“能不能被想起来”，再结合推荐率判断模型提到你之后是否愿意明确推荐你。',
        formula: '提及率 = 提到该品牌的问题数 / 当前有效样本数 × 100%。',
        interpretation: [
          {
            label: '更高时',
            text: '说明品牌进入回答的覆盖面更广，更容易先被模型纳入候选范围。'
          },
          {
            label: '更低时',
            text: '说明很多相关问题里，品牌还没有进入模型的回答视野。'
          }
        ],
        rules: [
          '提及率适合先判断进入回答的广度。',
          '横向比较时，应尽量保持时间窗口和筛选条件一致。'
        ],
        boundaries: [
          '它不区分提到的语气强弱。',
          '被提到不等于被推荐。'
        ],
        commonMisreads: ['提及率高，不代表品牌已经进入推荐位。']
      }
    ],
    nextSteps: [
      { label: '去品牌表现看哪些主题或平台最难被提到', route: '/engine' }
    ]
  },
  {
    objectId: 'metric.recommend_rate',
    displayName: '推荐率',
    type: 'metric',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'recommendRate', note: '推荐率正式投影字段' }
    ],
    userSourceLabel: '工作台指标口径',
    summary: '推荐率用于衡量品牌在当前样本中被模型明确推荐的比例。',
    contexts: [
      {
        pageContext: 'dashboard.summary',
        definition: '推荐率表示在当前有效样本中，有多少比例的问题会把该品牌明确放入推荐答案。',
        importance: '它通常比单纯被提到更接近业务价值，因为用户更在意“有没有被选中”，而不只是“有没有被顺带提到”。',
        readingGuide: '先和提及率对照看；如果提及率不低、推荐率却偏弱，说明模型认识你，但还没有稳定把你推到用户面前。',
        formula: '推荐率 = 明确推荐该品牌的问题数 / 当前有效样本数 × 100%。',
        interpretation: [
          {
            label: '更高时',
            text: '说明品牌不仅进入回答，还更容易被模型作为可选对象明确提出。'
          },
          {
            label: '更低时',
            text: '说明品牌即使被提到，也未必能转化为推荐位。'
          }
        ],
        rules: [
          '推荐率应与提及率一起看，才能判断问题出在“进不去”还是“进去了但没被选中”。',
          '推荐率高低不直接代表最终转化结果。'
        ],
        boundaries: [
          '它不是销量或转化结果。',
          '推荐率高，也要再看 Top3 出现率确认位置是否足够靠前。'
        ],
        commonMisreads: ['被提到很多次，不代表推荐率也一定高。']
      }
    ],
    nextSteps: [
      { label: '继续看 Top3 出现率，确认推荐有没有真正进入前列' },
      { label: '去品牌表现按主题和平台定位推荐短板', route: '/engine' }
    ]
  },
  {
    objectId: 'metric.top3_rate',
    displayName: 'Top3 出现率',
    type: 'metric',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'top3Rate', note: 'Top3 出现率正式投影字段' }
    ],
    userSourceLabel: '工作台指标口径',
    summary: 'Top3 出现率用于衡量品牌进入回答前列的稳定程度。',
    contexts: [
      {
        pageContext: 'dashboard.summary',
        definition: 'Top3 出现率表示在当前有效样本中，有多少比例的问题会让品牌进入回答的前三位置。',
        importance: '很多业务判断不只关心有没有被提到，更关心品牌是否出现在用户最先看到的位置。',
        readingGuide: '把它理解成“前排稳定度”。如果推荐率已经不低，但 Top3 仍偏弱，说明品牌开始进榜，却还没有稳住前排。',
        formula: 'Top3 出现率 = 品牌进入回答前三的问题数 / 当前有效样本数 × 100%。',
        interpretation: [
          {
            label: '更高时',
            text: '说明品牌更常出现在用户最容易先看到的位置。'
          },
          {
            label: '更低时',
            text: '说明品牌即使出现，也更可能停留在回答中后段。'
          }
        ],
        rules: [
          '它适合和推荐率一起读，用来区分“被推荐”与“站在前排”是不是同样稳定。',
          '比较时应优先使用同一窗口下的数据。'
        ],
        boundaries: [
          '它强调位置，不单独评价推荐语气。',
          'Top3 高，也要结合样本量和趋势一起看。'
        ],
        commonMisreads: ['Top3 出现率不是一个单独的推荐率替身。']
      }
    ],
    nextSteps: [
      { label: '去品牌表现看哪些主题已经开始进前三，哪些还在后排', route: '/engine' }
    ]
  },
  {
    objectId: 'module.dashboard.trend',
    displayName: '变化趋势',
    type: 'module',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览'],
    sourceOfTruth: [
      { kind: 'field', ref: 'overview.trend', note: '总览趋势数据结构' }
    ],
    userSourceLabel: '总览趋势口径',
    summary: '变化趋势用于观察品牌表现是在改善、回落还是持平。',
    contexts: [
      {
        pageContext: 'dashboard.trend',
        definition: '变化趋势把 AI 可见度、推荐率、提及率和 Top3 出现率放进同一时间轴，帮助你判断方向，而不只看某一个瞬时结果。',
        importance: '单轮结果容易受窗口和样本影响，趋势更能帮助你判断改善是否连续、回落是否偶发。',
        readingGuide: '先看主线方向，再看是哪一条指标在带动变化；如果总览方向和拆分指标不一致，优先检查那条异常线。',
        rules: [
          '趋势优先回答方向，不代替单轮详情。',
          '样本较少时，更适合把趋势当作方向参考，而不是唯一结论。'
        ],
        boundaries: [
          '趋势强调连续变化，不代替单轮详情。',
          '样本不足时，趋势只能当方向参考。'
        ],
        commonMisreads: ['不要只盯某一个时间点的波动下结论。']
      }
    ],
    nextSteps: [
      { label: '去品牌表现看是哪一个主题或平台在带动变化', route: '/engine' }
    ]
  },
  {
    objectId: 'module.dashboard.priorities',
    displayName: '优先关注',
    type: 'module',
    status: 'review',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览'],
    sourceOfTruth: [
      { kind: 'field', ref: 'overview.priorities', note: '总览洞察结论结构' }
    ],
    summary: '优先关注用于承接当前轮次最值得先处理的变化结论。',
    contexts: [
      {
        pageContext: 'dashboard.priorities',
        definition: '优先关注会从最新一轮结果里提炼“现在先抓什么”。',
        importance: '它更接近洞察容器，不是稳定业务对象，所以当前只保留治理位，不开放正式 Wiki 入口。',
        readingGuide: '如果这块后续开放，也应当先解释结论来自哪里，再解释建议要怎么落地。',
        boundaries: ['当前阶段它不是正式 Wiki 对象。'],
        commonMisreads: ['不要把这块当成稳定指标。']
      }
    ],
    nextSteps: [
      { label: '先去品牌表现和引用来源确认结论背后的事实', route: '/engine' }
    ]
  },
  {
    objectId: 'module.dashboard.competitor_rank',
    displayName: '竞品排名',
    type: 'module',
    status: 'draft',
    conceptState: 'contested',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览'],
    sourceOfTruth: [
      { kind: 'field', ref: 'overview.competitor_rank', note: '竞品对照摘要结构' }
    ],
    summary: '竞品排名当前仍是争议概念，暂不开放正式 Wiki。',
    contexts: [
      {
        pageContext: 'dashboard.competitor-rank',
        definition: '这块意在表达相对对照，而不是绝对品牌榜单。',
        importance: '由于边界还没完全收口，当前先不把它包装成稳定概念。',
        readingGuide: '后续若继续保留，必须先把“相对对照窗口”讲清楚。',
        boundaries: ['当前阶段禁止挂正式 Wiki 入口。'],
        commonMisreads: ['不要把它理解成全局排行榜。']
      }
    ],
    nextSteps: [
      { label: '暂不开放正式下一步' }
    ]
  },
  {
    objectId: 'module.dashboard.source_citations',
    displayName: '引用来源摘要',
    type: 'module',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '引用来源'],
    sourceOfTruth: [
      { kind: 'field', ref: 'overview.source_citations', note: '引用来源摘要结构' },
      { kind: 'route', ref: '/sources', note: '引用来源正式路由' }
    ],
    userSourceLabel: '总览来源摘要口径',
    summary: '引用来源摘要用于快速识别当前是谁在支撑模型回答。',
    contexts: [
      {
        pageContext: 'dashboard.source-citations',
        definition: '引用来源摘要把模型回答最常吸收的来源结构压缩到总览页，帮助你先判断自有内容、媒体内容和第三方内容谁更有影响力。',
        importance: '当可见度或推荐表现出现变化时，来源结构往往最能解释变化来自哪里。',
        readingGuide: '先看哪一类来源占比最高，再决定是否去引用来源页继续拆到域名和具体页面。',
        rules: [
          '总览里的来源摘要适合先看结构，不替代域名级和页面级核验。'
        ],
        boundaries: [
          '这里先给结构摘要，不代替域名级和 URL 级核验。',
          '来源被引用，不等于来源本身一定支持品牌。'
        ],
        commonMisreads: ['不要把摘要占比直接当成单一域名的全部贡献。']
      }
    ],
    nextSteps: [
      { label: '去引用来源页查看具体域名和页面', route: '/sources' }
    ]
  },
  {
    objectId: 'module.dashboard.model_performance',
    displayName: '模型表现',
    type: 'module',
    status: 'active',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['AI 可见度总览', '品牌表现'],
    sourceOfTruth: [
      { kind: 'field', ref: 'overview.model_performance', note: '模型表现摘要结构' },
      { kind: 'route', ref: '/engine?tab=platforms', note: '平台维度正式路由' }
    ],
    userSourceLabel: '平台维度摘要口径',
    summary: '模型表现用于概览同一品牌在不同模型平台上的表现差异。',
    contexts: [
      {
        pageContext: 'dashboard.model-performance',
        definition: '模型表现摘要先回答当前哪个模型平台表现更强，哪个平台明显偏弱。',
        importance: '如果不同平台差异很大，优化优先级通常不该平均分配，而应先追最值得补的那一侧。',
        readingGuide: '先看领先和落后平台，再切换到具体指标确认差异是否稳定存在。',
        rules: [
          '平台之间最好使用同一指标、同一窗口直接对照。',
          '总览里的模型表现只是摘要入口，深读仍应回到平台维度页。'
        ],
        boundaries: [
          '这里说的是平台差异，不是全部品牌表现的总和。',
          '首页摘要只是入口，细节仍要去平台维度页看。'
        ],
        commonMisreads: ['不要把总览里的“模型表现”当成独立于平台维度的另一套概念。']
      }
    ],
    nextSteps: [
      { label: '去品牌表现的“平台”维度继续查看', route: '/engine', query: { tab: 'platforms' } }
    ]
  }
] satisfies WikiObjectAuthoringDefinition[]
