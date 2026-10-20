<script setup lang="ts">
const emit = defineEmits<{
  start: []
}>()

const rootRef = ref<HTMLElement | null>(null)
const visible = ref(false)

let observer: IntersectionObserver | null = null

onMounted(() => {
  observer = new IntersectionObserver(
    ([entry]) => {
      if (!entry?.isIntersecting) {
        return
      }

      visible.value = true
      observer?.disconnect()
      observer = null
    },
    {
      threshold: 0.12,
      rootMargin: '0px 0px -100px 0px'
    }
  )

  if (rootRef.value) {
    observer.observe(rootRef.value)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
})
</script>

<template>
  <section class="px-4 py-24">
    <div
      ref="rootRef"
      class="landing-nexus-fade-up mx-auto max-w-4xl text-center"
      :class="{ 'is-visible': visible }"
    >
      <h2 class="font-display mb-6 text-4xl font-bold tracking-tight text-white sm:text-5xl lg:text-6xl">
        Ready to see visibility clearly?
      </h2>
      <p class="mx-auto mb-10 max-w-2xl text-lg text-zinc-400 sm:text-xl">
        Join teams already using Traclux to track AI brand visibility. Start free, no credit card required.
      </p>

      <div class="flex flex-col items-center justify-center gap-4 sm:flex-row">
        <button
          type="button"
          class="landing-nexus-shimmer-btn flex h-14 items-center rounded-full bg-white px-8 text-base font-medium text-zinc-950 shadow-lg shadow-white/20 transition-colors duration-200 hover:bg-zinc-200"
          @click="emit('start')"
        >
          Create Brand Space
          <UIcon
            name="i-lucide-arrow-right"
            class="ml-2 h-5 w-5"
          />
        </button>

        <NuxtLink
          to="/login"
          class="flex h-14 items-center rounded-full border border-zinc-800 bg-transparent px-8 text-base font-medium text-zinc-300 transition-colors duration-200 hover:border-zinc-700 hover:bg-zinc-900 hover:text-white"
        >
          View Pricing
        </NuxtLink>
      </div>

      <p class="mt-8 text-sm text-zinc-500">
        Free for one workspace. Team plans start at $29/month.
      </p>
    </div>
  </section>
</template>
