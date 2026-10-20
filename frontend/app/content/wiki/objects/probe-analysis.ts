import type { WikiObjectAuthoringDefinition } from '~/types/wiki'

export const probeAnalysisWikiObjects = [
  {
    objectId: 'page.probe_analysis',
    displayName: '问题分析',
    type: 'page',
    status: 'active',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['问题分析', '品牌表现', '引用来源'],
    sourceOfTruth: [
      { kind: 'backend-contract', ref: '/api/v1/workbench/problem-analysis', note: '问题分析正式数据合同' },
      { kind: 'route', ref: '/probe-analysis', note: '问题分析正式路由' }
    ],
    userSourceLabel: '产品定义与问题分析页接口',
    aliases: ['白箱问题空间'],
    summary: '问题分析把结果落回到当前品牌空间持续跟踪的具体问法、回答与证据。',
    contexts: [
      {
        pageContext: 'probe.page',
        definition: '问题分析页用于解释结果为何形成。它从具体问法出发，把回答表现和对应证据放回同一处查看。',
        importance: '当你已经知道哪里变差时，这一页负责回答“到底是哪类问题把结果拖下去了”。',
        readingGuide: '先看问题结构和筛选条件，再看具体问法与记录，最后继续打开证据确认发生了什么。',
        rules: [
          '这页适合做原因追查，不适合替代总览页做整体判断。'
        ],
        boundaries: [
          '这页强调具体问题与证据，不代替总览和品牌表现。',
          '页面里的问法来自当前品牌空间的跟踪范围，不等同于全网所有自然提问。'
        ],
        commonMisreads: ['不要把这页理解成又一个汇总页。']
      }
    ],
    nextSteps: [
      { label: '回品牌表现确认问题集中在哪个主题或平台', route: '/engine' },
      { label: '回引用来源确认这些问题吸收了哪些站点', route: '/sources' }
    ]
  },
  {
    objectId: 'object.problem_cluster',
    displayName: '问题簇',
    type: 'object',
    status: 'draft',
    conceptState: 'contested',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['问题分析'],
    sourceOfTruth: [
      { kind: 'manual', ref: 'problem-cluster', note: '问题簇定义仍在收口' }
    ],
    summary: '问题簇当前仍是争议对象，暂不开放正式 Wiki。',
    contexts: [
      {
        pageContext: 'probe.records',
        definition: '它意图表达一组相近问题的聚合结果，但边界和稳定性仍未完全收口。',
        importance: '为了避免把概念债正式固化，当前阶段不挂正式入口。',
        readingGuide: '如果后续继续保留，必须先把聚类规则和用户价值讲明白。',
        boundaries: ['当前阶段禁止挂正式 Wiki 入口。'],
        commonMisreads: ['不要把它直接当成“主题”的另一个名字。']
      }
    ],
    nextSteps: [
      { label: '暂不开放正式下一步' }
    ]
  },
  {
    objectId: 'object.prompt',
    displayName: '真实问题',
    type: 'object',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['问题分析'],
    sourceOfTruth: [
      { kind: 'field', ref: 'promptRows[]', note: '真实问题正式投影' }
    ],
    userSourceLabel: '问题分析对象口径',
    summary: '真实问题表示当前品牌空间持续跟踪的一条具体问法。',
    contexts: [
      {
        pageContext: 'probe.records',
        definition: '这里的真实问题指当前品牌空间下的一条具体问法。围绕这条问法，你可以继续查看回答、位次和引用证据。',
        importance: '只有回到具体问法本身，你才能判断品牌为什么会被提到、没有被提到，或者被谁抢走了推荐位。',
        readingGuide: '先看问题本身，再看对应平台、位次和证据，不要只看上层汇总标签做判断。',
        rules: [
          '单条真实问题适合做细节核对，不代替主题层的结构判断。'
        ],
        boundaries: [
          '真实问题是单条具体问法，不等于主题。',
          '同一个主题下面可以有很多条不同问法。'
        ],
        commonMisreads: ['不要把一条真实问题当成整个问题空间。']
      }
    ],
    nextSteps: [
      { label: '继续查看这条问题对应的回答和引用证据', route: '/probe-analysis' }
    ]
  },
  {
    objectId: 'object.probe',
    displayName: '探针',
    type: 'object',
    status: 'review',
    conceptState: 'mapped',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['问题分析'],
    sourceOfTruth: [
      { kind: 'manual', ref: 'probe', note: 'Probe 运行定义' }
    ],
    summary: '探针是系统内部用来组织一组探测任务的运行对象，当前仍先保留治理位。',
    contexts: [
      {
        pageContext: 'probe.records',
        definition: '探针更接近系统内部的运行对象，不是面向普通用户的前台分析概念。',
        importance: '为了避免把内部链路感直接暴露给用户，当前阶段先不做首页级强解释。',
        readingGuide: '如果你看到这个词，更安全的做法是先回到真实问题和证据层判断。',
        boundaries: ['当前阶段不作为第一批正式入口对象。'],
        commonMisreads: ['不要把探针直接理解成用户问题本身。']
      }
    ],
    nextSteps: [
      { label: '优先看真实问题和证据，而不是先看探针定义' }
    ]
  },
  {
    objectId: 'object.evidence',
    displayName: '证据',
    type: 'object',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['问题分析', '引用来源'],
    sourceOfTruth: [
      { kind: 'field', ref: 'evidence', note: '证据 / 回答 / 来源正式投影' }
    ],
    userSourceLabel: '问题分析与引用来源对象口径',
    summary: '证据是支撑当前判断的回答片段、引用信息和来源落点。',
    contexts: [
      {
        pageContext: 'probe.records',
        definition: '证据不是抽象说明，而是当前结论背后实际对应的回答内容和引用来源。',
        importance: '如果没有证据，就很难判断当前结论是稳定事实，还是一条暂时看起来合理的表面结果。',
        readingGuide: '先看这条问题有没有引用，再点开证据确认具体回答和来源。',
        rules: [
          '证据适合用来核对结论是否站得住，而不是单独替代业务判断。'
        ],
        boundaries: [
          '证据为 0 不一定代表问题无效，也可能意味着当前轮次还没有形成完整引用。',
          '证据描述的是支撑材料，不等于最终业务结论。'
        ],
        commonMisreads: ['不要把“没有证据”直接理解成“这条问题没有任何意义”。']
      }
    ],
    nextSteps: [
      { label: '去引用来源页继续核对这些证据来自哪些站点', route: '/sources' }
    ]
  }
] satisfies WikiObjectAuthoringDefinition[]
