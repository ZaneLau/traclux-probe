<script setup lang="ts">
const logos = ['ChatGPT', 'Claude', 'Gemini', 'Perplexity', 'Reddit', 'YouTube', 'GitHub', 'Wikipedia']

const rootRef = ref<HTMLElement | null>(null)
const visible = ref(false)
const duplicatedLogos = computed(() => [...logos, ...logos])

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
      threshold: 0.1,
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
  <section
    ref="rootRef"
    class="overflow-hidden py-16"
  >
    <div class="mb-10 text-center">
      <p
        class="landing-nexus-fade-in text-sm font-medium uppercase tracking-wider text-zinc-500"
        :class="{ 'is-visible': visible }"
      >
        Tracked across answer surfaces
      </p>
    </div>

    <div class="relative">
      <div class="pointer-events-none absolute bottom-0 left-0 top-0 z-10 w-32 bg-gradient-to-r from-zinc-950 to-transparent" />
      <div class="pointer-events-none absolute bottom-0 right-0 top-0 z-10 w-32 bg-gradient-to-l from-zinc-950 to-transparent" />

      <div class="landing-nexus-animate-marquee flex">
        <div
          v-for="(logo, index) in duplicatedLogos"
          :key="`${logo}-${index}`"
          class="mx-8 flex h-16 min-w-[160px] items-center justify-center grayscale opacity-50 transition-all duration-300 hover:opacity-100 hover:grayscale-0"
        >
          <div class="flex items-center gap-2 text-zinc-400">
            <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-zinc-800">
              <span class="text-xs font-bold">{{ logo[0] }}</span>
            </div>
            <span class="font-heading font-medium">{{ logo }}</span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
