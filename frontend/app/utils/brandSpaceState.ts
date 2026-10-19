import type {
  BrandSpaceHealthStatus,
  BrandSpaceListItem,
  BrandSpaceTrackingStatus,
  RunStatus
} from '~/types/brand-space'

type StatusTone = 'status-pill--success' | 'status-pill--warning' | 'status-pill--danger' | 'status-pill--default'

export interface BrandSpaceDisplayState {
  key: string
  label: string
  tone: StatusTone
  detail: string
  contractReady: boolean
}

function normalizeUpper(value?: string | null) {
  return String(value || '').trim().toUpperCase()
}

function normalizeLower(value?: string | null) {
  return String(value || '').trim().toLowerCase()
}

function isTrackingStatus(value: string): value is BrandSpaceTrackingStatus {
  return value === 'ACTIVE' || value === 'PAUSED' || value === 'NEEDS_ATTENTION'
}

function isHealthStatus(value: string): value is BrandSpaceHealthStatus {
  return value === 'HEALTHY' || value === 'DEGRADED' || value === 'ERROR' || value === 'CONFIG_INCOMPLETE'
}

function isRunStatus(value: string): value is RunStatus {
  return ['created', 'running', 'completed', 'failed', 'cancelled', 'partial'].includes(value)
}

export function resolveBrandSpaceTrackingState(item?: BrandSpaceListItem | null): BrandSpaceDisplayState {
  const normalized = normalizeUpper(item?.trackingStatus)

  if (isTrackingStatus(normalized)) {
    if (normalized === 'ACTIVE') {
      return {
        key: normalized,
        label: '跟踪中',
        tone: 'status-pill--success',
        detail: '当前品牌空间处于持续跟踪状态。',
        contractReady: true
      }
    }

    if (normalized === 'PAUSED') {
      return {
        key: normalized,
        label: '已暂停',
        tone: 'status-pill--warning',
        detail: '当前品牌空间已暂停自动跟踪。',
        contractReady: true
      }
    }

    return {
      key: normalized,
      label: '需处理',
      tone: 'status-pill--danger',
      detail: '品牌空间需要先处理异常或配置问题，再恢复长期跟踪。',
      contractReady: true
    }
  }

  return {
    key: 'UNSPECIFIED',
    label: '状态同步中',
    tone: 'status-pill--default',
    detail: '正在同步长期跟踪状态。',
    contractReady: false
  }
}

export function resolveBrandSpaceHealthState(item?: BrandSpaceListItem | null): BrandSpaceDisplayState {
  const normalizedHealth = normalizeUpper(item?.healthStatus)

  if (isHealthStatus(normalizedHealth)) {
    if (normalizedHealth === 'HEALTHY') {
      return {
        key: normalizedHealth,
        label: '运行正常',
        tone: 'status-pill--success',
        detail: '最近一轮真实链路运行健康。',
        contractReady: true
      }
    }

    if (normalizedHealth === 'DEGRADED') {
      return {
        key: normalizedHealth,
        label: '运行降级',
        tone: 'status-pill--warning',
        detail: '链路仍可用，但结果完整性或质量需要关注。',
        contractReady: true
      }
    }

    if (normalizedHealth === 'CONFIG_INCOMPLETE') {
      return {
        key: normalizedHealth,
        label: '配置待补',
        tone: 'status-pill--warning',
        detail: '品牌空间基础配置未齐，已影响真实运行能力。',
        contractReady: true
      }
    }

    return {
      key: normalizedHealth,
      label: '运行异常',
      tone: 'status-pill--danger',
      detail: '最近一轮真实链路失败或处于异常状态。',
      contractReady: true
    }
  }

  const normalizedRunStatus = normalizeLower(item?.lastRunStatus)
  if (isRunStatus(normalizedRunStatus)) {
    if (normalizedRunStatus === 'completed' || normalizedRunStatus === 'partial') {
      return {
        key: `RUN_${normalizedRunStatus.toUpperCase()}`,
        label: normalizedRunStatus === 'completed' ? '运行正常' : '部分完成',
        tone: normalizedRunStatus === 'completed' ? 'status-pill--success' : 'status-pill--warning',
        detail: '当前先按最近一轮运行结果展示健康状态。',
        contractReady: false
      }
    }

    if (normalizedRunStatus === 'running' || normalizedRunStatus === 'created') {
      return {
        key: `RUN_${normalizedRunStatus.toUpperCase()}`,
        label: '运行中',
        tone: 'status-pill--warning',
        detail: '当前先按最近一轮运行结果展示健康状态。',
        contractReady: false
      }
    }

    return {
      key: `RUN_${normalizedRunStatus.toUpperCase()}`,
      label: '运行异常',
      tone: 'status-pill--danger',
      detail: '当前先按最近一轮运行结果展示健康状态。',
      contractReady: false
    }
  }

  const legacyStatus = normalizeLower(item?.status)
  if (item?.configReady === true && !item?.latestRunId && !item?.lastRunAt && !item?.lastGeneratedAt) {
    return {
      key: 'PENDING_INITIAL_RUN',
      label: '待首跑',
      tone: 'status-pill--warning',
      detail: '品牌空间已创建，但还没有任何真实运行记录。',
      contractReady: false
    }
  }

  if (legacyStatus === 'active') {
    return {
      key: 'LEGACY_ACTIVE',
      label: '最近运行正常',
      tone: 'status-pill--success',
      detail: '当前先按最近一次稳定运行展示。',
      contractReady: false
    }
  }

  if (legacyStatus === 'generating') {
    return {
      key: 'LEGACY_GENERATING',
      label: '首轮接通中',
      tone: 'status-pill--warning',
      detail: '品牌空间正在完成首轮接通。',
      contractReady: false
    }
  }

  if (legacyStatus === 'failed') {
    return {
      key: 'LEGACY_FAILED',
      label: '需处理',
      tone: 'status-pill--danger',
      detail: '最近一轮运行失败，建议先处理后再恢复跟踪。',
      contractReady: false
    }
  }

  return {
    key: 'UNKNOWN',
    label: '未读取',
    tone: 'status-pill--default',
    detail: '尚未读取到运行健康信息。',
    contractReady: false
  }
}

export function resolveBrandSpaceUpdatedAt(item?: BrandSpaceListItem | null) {
  return item?.lastRunAt || item?.lastGeneratedAt || null
}

export function hasBrandSpaceNeedsAttention(item?: BrandSpaceListItem | null) {
  const tracking = resolveBrandSpaceTrackingState(item)
  if (tracking.key === 'NEEDS_ATTENTION') {
    return true
  }

  const health = resolveBrandSpaceHealthState(item)
  return health.tone === 'status-pill--danger'
}
