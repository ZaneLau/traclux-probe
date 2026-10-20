<script setup lang="ts">
const zhCn = {
  name: '简体中文',
  code: 'zh-CN',
  dir: 'ltr' as const,
  messages: {
    calendar: {
      nextMonth: '下个月',
      nextYear: '明年',
      prevMonth: '上个月',
      prevYear: '去年'
    },
    selectMenu: {
      create: '创建 “{label}”',
      noData: '没有数据',
      noMatch: '没有匹配的数据',
      search: '搜索…'
    },
    inputMenu: {
      create: '创建 “{label}”',
      noData: '没有数据',
      noMatch: '没有匹配的数据'
    }
  }
} as any

useHead({
  meta: [
    { name: 'viewport', content: 'width=device-width, initial-scale=1' }
  ],
  link: [
    { rel: 'icon', href: '/favicon.ico' }
  ],
  htmlAttrs: {
    lang: 'zh-CN'
  }
})

const title = 'Traclux 探镜'
const description = 'Traclux 探镜工作台，聚合品牌可见度、证据、监测与增长信号。'

useSeoMeta({
  title,
  description,
  ogTitle: title,
  ogDescription: description,
  twitterCard: 'summary_large_image'
})
</script>

<template>
  <UApp :locale="zhCn">
    <NuxtLoadingIndicator
      color="var(--ui-primary)"
      :height="3"
    />
    <NuxtLayout>
      <NuxtPage />
    </NuxtLayout>
    <WikiGeoWikiDrawer />
  </UApp>
</template>
