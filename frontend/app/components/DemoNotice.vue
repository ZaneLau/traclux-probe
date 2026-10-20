<script setup lang="ts">
const toast = useToast()
let lastNotice = 0
onMounted(() => {
  const protectWrite = (event: Event) => {
    const target = event.target instanceof Element ? event.target.closest('button, input[type="submit"]') : null
    if (event.target instanceof Element && event.target.closest('.provenance-value-trigger')) {
      event.preventDefault()
      event.stopImmediatePropagation()
      toast.add({ title: '演示未包含这项明细', description: '可先查看问题分析中的问答记录，或到品牌管理查看运行详情。', color: 'neutral' })
      return
    }
    if (!target || !/^(保存|创建|新增|添加|归档|恢复|删除|执行|立即|重新生成|确认|编辑|移出|开始生成|生成方案|暂停|继续追踪|更新)/.test(target.textContent?.trim() || '')) return
    event.preventDefault()
    event.stopImmediatePropagation()
    if (Date.now() - lastNotice > 1000) {
      toast.add({ title: '只读演示', description: '自行部署后即可生成、执行和保存。', color: 'neutral' })
      lastNotice = Date.now()
    }
  }
  document.addEventListener('click', protectWrite, true)
  onBeforeUnmount(() => document.removeEventListener('click', protectWrite, true))
})
</script>

<template>
  <div class="demo-notice" role="region" aria-label="只读演示">
    <div class="demo-notice__label"><UBadge color="neutral" variant="solid" size="sm">只读演示</UBadge><span>Anker · 2026-10-20 · 5 条探针 · 未联网 · 回答为摘录</span></div>
    <div class="demo-notice__actions">
      <UButton to="https://zanelau.github.io/traclux-probe/demo/case-study/" target="_blank" size="xs" color="neutral" variant="link">CSSM-HI 生成实例</UButton>
      <UButton to="https://github.com/ZaneLau/traclux-probe/blob/main/docs/community/known-limitations.md" target="_blank" size="xs" color="neutral" variant="link">已知限制</UButton>
      <UButton to="https://github.com/ZaneLau/traclux-probe#start-locally" target="_blank" size="xs" color="neutral" variant="solid" icon="i-lucide-github">部署 / Star</UButton>
    </div>
  </div>
</template>

<style scoped>
.demo-notice { position: fixed; z-index: 100; inset: 0 0 auto; height: 48px; background: #f0fdfa; border-bottom: 1px solid #99c9c1; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 20px; font: 12px var(--font-sans); color: #164e47; }
.demo-notice__label, .demo-notice__actions { display: flex; align-items: center; gap: 12px; }
</style>
