const ADMIN_TRIGGER_LABELS: Record<string, string> = {
  scheduled_tracking: '品牌空间自动跟踪',
  scheduled_tracking_manual_acceptance: '品牌空间自动跟踪',
  brand_space_schedule: '品牌空间自动跟踪',
  create_initial_run: '品牌创建后首跑',
  brand_space_create_initial_run: '品牌创建后首跑',
  interactive_preview: '人工确认后执行',
  interactive_create: '人工创建后执行',
  internal_rerun: '后台人工补跑',
  admin_rerun: '后台人工补跑'
}

export function resolveAdminTriggerLabel(value?: string | null) {
  const normalized = String(value || '').trim()
  if (!normalized) {
    return '未记录来源'
  }
  return ADMIN_TRIGGER_LABELS[normalized] || normalized
}

export function resolveAdminTriggerNote(value?: string | null) {
  const normalized = String(value || '').trim()
  switch (normalized) {
    case 'scheduled_tracking':
    case 'scheduled_tracking_manual_acceptance':
    case 'brand_space_schedule':
      return '由每日跟踪计划自动触发'
    case 'create_initial_run':
    case 'brand_space_create_initial_run':
      return '品牌创建完成后立即首跑'
    case 'interactive_preview':
    case 'interactive_create':
      return '由人工确认后发起执行'
    case 'internal_rerun':
    case 'admin_rerun':
      return '由后台人工补跑发起'
    default:
      return '当前批次未沉淀更细的触发说明'
  }
}
