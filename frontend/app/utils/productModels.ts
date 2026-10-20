export type ProductModelKey = 'doubao' | 'deepseek' | 'glm' | 'chatgpt' | 'unknown'
export type ProductModelDisplayMap = Record<string, string>

export interface PublicPreviewModelOption {
  key: ProductModelKey
  label: string
  description: string
  statusLabel: string
  capabilityLabel: string
  capabilityDescription: string
  selectable: boolean
  selected: boolean
}

const PUBLIC_PREVIEW_MODELS: Array<{
  key: Exclude<ProductModelKey, 'unknown'>
  label: string
  description: string
}> = [
  {
    key: 'doubao',
    label: '豆包',
    description: '当前公测已开放，可作为品牌空间的监控模型。'
  },
  {
    key: 'deepseek',
    label: 'DeepSeek',
    description: '能力入口已预留，当前公测暂未开放。'
  },
  {
    key: 'glm',
    label: '智谱',
    description: '能力入口已预留，当前公测暂未开放。'
  }
]

function normalizeRawToken(value?: string | null) {
  return String(value || '').trim().toLowerCase()
}

function formatCapitalWord(value: string) {
  if (!value) {
    return ''
  }
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function resolveVariantLabel(value?: string | null) {
  const normalized = normalizeRawToken(value)
  if (!normalized) {
    return null
  }

  const family = resolveProductModelKey(normalized)
  if (family === 'doubao') {
    const seedMatch = normalized.match(/seed-(\d+)-(\d+)(?:-(lite|pro))?/)
    if (seedMatch) {
      const [, major, minor, tier] = seedMatch
      return `Seed ${major}.${minor}${tier ? ` ${formatCapitalWord(tier)}` : ''}`
    }
    if (normalized.includes('lite')) {
      return 'Lite'
    }
    if (normalized.includes('pro')) {
      return 'Pro'
    }
  }

  if (family === 'deepseek') {
    if (normalized.includes('r1')) {
      return 'R1'
    }
    if (normalized.includes('v3')) {
      return 'V3'
    }
  }

  if (family === 'glm') {
    const glmMatch = normalized.match(/glm[-_]?(\d+(?:[-.]\d+)?)/)
    if (glmMatch?.[1]) {
      return `GLM ${glmMatch[1].replace('-', '.')}`
    }
    if (normalized.includes('air')) {
      return 'Air'
    }
    if (normalized.includes('flash')) {
      return 'Flash'
    }
  }

  if (family === 'chatgpt') {
    const gptMatch = normalized.match(/gpt[-_]?(\d+(?:[-.]\d+)?[a-z]?)/)
    if (gptMatch?.[1]) {
      return `GPT ${gptMatch[1].toUpperCase()}`
    }
  }

  return null
}

function resolveFallbackVariantLabel(value?: string | null) {
  const normalized = normalizeRawToken(value)
  if (!normalized) {
    return null
  }

  return normalized
    .split(/[-_]+/)
    .filter(token => token && !/^\d{6,8}$/.test(token))
    .slice(-3)
    .map((token) => {
      if (/^\d+$/.test(token)) {
        return token
      }
      if (token.length <= 3) {
        return token.toUpperCase()
      }
      return formatCapitalWord(token)
    })
    .join(' ')
}

export function resolveProductModelKey(value?: string | null): ProductModelKey {
  const normalized = normalizeRawToken(value)
  if (!normalized) {
    return 'unknown'
  }
  if (
    normalized === '豆包'
    || normalized === '火山'
    || normalized.startsWith('火山')
    || normalized.startsWith('doubao')
    || normalized.startsWith('ark')
    || normalized.includes('volcengine')
    || normalized.startsWith('ep-')
    || normalized.includes('seed-1')
  ) {
    return 'doubao'
  }
  if (normalized === 'deepseek' || normalized.startsWith('deepseek')) {
    return 'deepseek'
  }
  if (
    normalized === '智谱'
    || normalized.startsWith('glm')
    || normalized.startsWith('zhipu')
  ) {
    return 'glm'
  }
  if (
    normalized === 'openai'
    || normalized === 'chatgpt'
    || normalized.startsWith('gpt')
    || normalized.includes('chatgpt')
    || normalized.includes('openai')
  ) {
    return 'chatgpt'
  }
  return 'unknown'
}

export function resolveProductModelLabel(value?: string | null) {
  const key = resolveProductModelKey(value)
  if (key === 'doubao') return '豆包'
  if (key === 'deepseek') return 'DeepSeek'
  if (key === 'glm') return '智谱'
  if (key === 'chatgpt') return 'ChatGPT'

  return value ? '其他模型' : '未标注模型'
}

export function buildProductModelDisplayMap(values: Array<string | null | undefined>): ProductModelDisplayMap {
  const familyMembers = new Map<ProductModelKey, Set<string>>()
  const orderedTokens: string[] = []

  for (const value of values) {
    const normalized = normalizeRawToken(value)
    if (!normalized) {
      continue
    }
    orderedTokens.push(normalized)
    const key = resolveProductModelKey(normalized)
    const current = familyMembers.get(key) || new Set<string>()
    current.add(normalized)
    familyMembers.set(key, current)
  }

  const displayMap: ProductModelDisplayMap = {}
  for (const normalized of orderedTokens) {
    if (displayMap[normalized]) {
      continue
    }

    const family = resolveProductModelKey(normalized)
    const familyLabel = resolveProductModelLabel(normalized)
    const collisionCount = familyMembers.get(family)?.size || 0

    if (collisionCount <= 1 || family === 'unknown') {
      displayMap[normalized] = familyLabel
      continue
    }

    const variant = resolveVariantLabel(normalized) || resolveFallbackVariantLabel(normalized)
    displayMap[normalized] = variant ? `${familyLabel}（${variant}）` : familyLabel
  }

  return displayMap
}

export function resolveProductModelDisplayLabel(
  value?: string | null,
  displayMap?: ProductModelDisplayMap | null
) {
  const normalized = normalizeRawToken(value)
  if (normalized && displayMap?.[normalized]) {
    return displayMap[normalized]
  }
  return resolveProductModelLabel(value)
}

export function buildPublicPreviewModelOptions(
  enabledModelCount?: number | null,
  selectedModelKeys?: string[] | null
): PublicPreviewModelOption[] {
  const normalizedSelected = new Set(
    (selectedModelKeys || [])
      .map(resolveProductModelKey)
      .filter(key => key !== 'unknown')
  )
  const selectableCount = typeof enabledModelCount === 'number' && enabledModelCount > 0
    ? enabledModelCount
    : 1

  return PUBLIC_PREVIEW_MODELS.map((model, index) => {
    const selectable = model.key === 'doubao' && selectableCount >= 1
    const selected = normalizedSelected.size
      ? normalizedSelected.has(model.key)
      : model.key === 'doubao'

    return {
      key: model.key,
      label: model.label,
      description: model.description,
      statusLabel: selectable
        ? (selected ? '当前已选' : '当前可选')
        : '暂未开放',
      capabilityLabel: '支持联网',
      capabilityDescription: '默认开启实时联网增强，会优先吸收最新网页事实，也意味着更高运行成本。',
      selectable,
      selected: selectable && selected && index < selectableCount
    }
  })
}
