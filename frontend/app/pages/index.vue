<script setup lang="ts">
definePageMeta({
  layout: false
})

await navigateTo(useRuntimeConfig().public.demoMode === true ? '/dashboard' : '/login', {
  replace: true,
  redirectCode: 302
})
</script>

<template>
  <div />
</template>
