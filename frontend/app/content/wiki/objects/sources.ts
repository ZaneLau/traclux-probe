import type { WikiObjectAuthoringDefinition } from '~/types/wiki'

export const sourcesWikiObjects = [
  {
    objectId: 'page.sources',
    displayName: '引用来源',
    type: 'page',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['引用来源', 'AI 可见度总览', '问题分析'],
    sourceOfTruth: [
      { kind: 'backend-contract', ref: '/api/v1/workbench/citation-sources', note: '引用来源正式数据合同' },
      { kind: 'route', ref: '/sources', note: '引用来源正式路由' }
    ],
    userSourceLabel: '产品定义与引用来源页接口',
    aliases: ['引用信源', '来源'],
    summary: '引用来源页用于回答模型回答正在吸收哪些站点和页面。',
    contexts: [
      {
        pageContext: 'sources.page',
        definition: '引用来源页把总览中的来源结构继续拆开，帮助你确认模型回答到底吸收了哪些站点和页面。',
        importance: '当总览告诉你结果变了，这一页往往最能回答“为什么会这样”。',
        readingGuide: '先看来源结构和域名类型，再看域名列表，最后按需要下钻到具体页面。',
        rules: [
          '这页适合先判断证据结构，再决定是否继续核对单篇页面。'
        ],
        boundaries: [
          '这页看的是证据来源，不直接评价内容质量高低。',
          '页面名和单一域名对象不是同一个层级。'
        ],
        commonMisreads: ['不要把引用来源页和某一个具体域名对象混成一个层次。']
      }
    ],
    nextSteps: [
      { label: '回总览对照引用来源摘要', route: '/dashboard' },
      { label: '去问题分析核对哪些真实问题挂上了这些证据', route: '/probe-analysis' }
    ]
  },
  {
    objectId: 'object.source_domain',
    displayName: '来源域名',
    type: 'object',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['引用来源'],
    sourceOfTruth: [
      { kind: 'field', ref: 'sourceRows.domain', note: '来源域名正式读模型' }
    ],
    userSourceLabel: '引用来源对象口径',
    summary: '来源域名表示被模型反复吸收内容的站点层级对象。',
    contexts: [
      {
        pageContext: 'sources.list',
        definition: '来源域名把具体页面向上归到站点层级，帮助你先判断哪些网站正在稳定影响当前结果。',
        importance: '先看域名层级，比直接读一长串 URL 更容易找到真正有影响力的来源结构。',
        readingGuide: '先看哪些域名最常出现、占比最高，再决定要不要继续展开到具体页面。',
        rules: [
          '域名层级适合先判断结构，再决定是否下钻到页面明细。'
        ],
        boundaries: [
          '域名层级先看结构，不代替 URL 级核验。',
          '域名被引用，不代表该站所有页面都同样重要。'
        ],
        commonMisreads: ['不要把单个 URL 的偶发出现，当成整个域名都已经稳定生效。']
      }
    ],
    nextSteps: [
      { label: '展开具体页面，确认到底是哪几篇内容在支撑结果' }
    ]
  },
  {
    objectId: 'object.source_url',
    displayName: '证据 URL',
    type: 'object',
    status: 'review',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['引用来源'],
    sourceOfTruth: [
      { kind: 'field', ref: 'sourceRows.url', note: 'URL 级证据投影' }
    ],
    summary: '证据 URL 用于确认真正被模型吸收的是哪一条具体页面。',
    contexts: [
      {
        pageContext: 'sources.focus',
        definition: 'URL 层级把域名继续拆到具体页面，帮助你定位真实支撑结果的内容落点。',
        importance: '当前阶段先保留对象位，后续若正式开放，需要和域名层级保持清楚分工。',
        readingGuide: '如果某个域名已经明显抬头，再继续看 URL 层级确认到底是哪几篇页面有效。',
        boundaries: ['当前阶段不作为第一批正式入口对象。'],
        commonMisreads: ['不要在没有域名层级判断前直接淹没在 URL 明细里。']
      }
    ],
    nextSteps: [
      { label: '先在域名层级确定重点来源，再继续拆 URL' }
    ]
  }
] satisfies WikiObjectAuthoringDefinition[]
