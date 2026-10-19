import type { WikiObjectAuthoringDefinition } from '~/types/wiki'

export const brandsWikiObjects = [
  {
    objectId: 'object.brand_space',
    displayName: '品牌空间',
    type: 'object',
    status: 'active',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌管理', '问题分析', '引用来源', '品牌表现'],
    sourceOfTruth: [
      { kind: 'backend-contract', ref: '/api/v1/brand-spaces', note: '品牌空间正式接口合同' }
    ],
    userSourceLabel: '产品定义与品牌空间接口',
    summary: '品牌空间是探镜中用于承载一个品牌长期跟踪与分析的核心业务对象。',
    contexts: [
      {
        pageContext: 'brands.index',
        definition: '品牌空间不只是一个品牌名片。它同时保存品牌配置、跟踪状态、运行结果，以及后续各分析页的绑定关系。',
        importance: '如果没有先进入正确的品牌空间，后面的总览、品牌表现、引用来源和问题分析就不会落到同一个分析对象上。',
        readingGuide: '先在品牌空间列表确认当前品牌、跟踪状态和运行健康，再进入各分析页查看结果。',
        rules: [
          '所有工作台结果都建立在品牌空间上下文之上。'
        ],
        boundaries: [
          '品牌空间不是单纯的品牌标签。',
          '它是后续分析页共同绑定的工作单元。'
        ],
        commonMisreads: ['不要把品牌空间理解成一个只用来存名字的配置项。']
      }
    ],
    nextSteps: [
      { label: '新增品牌空间后，再进入总览查看首轮结果', route: '/brands/new' },
      { label: '进入品牌表现查看这个品牌空间的分析结果', route: '/engine' }
    ]
  },
  {
    objectId: 'page.brands',
    displayName: '品牌管理',
    type: 'page',
    status: 'review',
    conceptState: 'stable',
    updatedAt: '2026-05-18',
    owner: 'wiki-5.0.3',
    scope: ['品牌管理'],
    sourceOfTruth: [
      { kind: 'route', ref: '/brands', note: '品牌管理正式路由' }
    ],
    summary: '品牌管理页用于查看、创建和维护当前账号下的品牌空间。',
    contexts: [
      {
        pageContext: 'brands.page',
        definition: '品牌管理页负责把当前账号下的品牌空间组织起来，方便你继续进入运行记录和各条正式分析页。',
        importance: '它是工作流入口，但当前阶段先不进入第一批正式 Wiki 入口。',
        readingGuide: '先确认品牌空间状态，再决定是新增、恢复跟踪，还是进入后续分析页。',
        boundaries: ['当前阶段保留治理位，不挂正式 Wiki 入口。'],
        commonMisreads: ['不要把它当成最终的分析结果页。']
      }
    ],
    nextSteps: [
      { label: '优先查看品牌空间对象本身，再进入对应分析页' }
    ]
  }
] satisfies WikiObjectAuthoringDefinition[]
