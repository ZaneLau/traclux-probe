<script setup lang="ts">
const emit = defineEmits<{
  start: []
}>()

const entered = ref(false)

const avatars = [
  '/landing-nexus/professional-headshot-1.png',
  '/landing-nexus/professional-headshot-2.png',
  '/landing-nexus/professional-headshot-3.png',
  '/landing-nexus/professional-headshot-4.png',
  '/landing-nexus/professional-headshot-5.png'
]

onMounted(() => {
  requestAnimationFrame(() => {
    entered.value = true
  })
})
</script>

<template>
  <section class="relative flex min-h-screen flex-col items-center justify-center overflow-hidden px-4 pb-16 pt-24">
    <div class="pointer-events-none absolute inset-0 bg-gradient-to-b from-zinc-950 via-zinc-950 to-zinc-900" />
    <div class="pointer-events-none absolute left-1/2 top-1/4 h-[600px] w-[800px] -translate-x-1/2 rounded-full bg-zinc-800/20 blur-3xl" />

    <div class="relative z-10 mx-auto max-w-5xl text-center">
      <div
        class="landing-nexus-fade-up mb-8 inline-flex items-center gap-2 rounded-full border border-zinc-800 bg-zinc-900 px-4 py-2"
        :class="{ 'is-visible': entered }"
        style="transition-delay: 0.2s;"
      >
        <span class="landing-nexus-pulse-glow h-2 w-2 rounded-full bg-emerald-500" />
        <span class="text-sm text-zinc-400">AI Visibility in Public Beta</span>
      </div>

      <h1 class="font-display mb-6 text-5xl font-bold tracking-tight text-white sm:text-6xl lg:text-7xl">
        <span class="block overflow-hidden">
          <span
            class="landing-nexus-hero-line block"
            :class="{ 'is-visible': entered }"
          >See clearly.</span>
        </span>
        <span class="block overflow-hidden">
          <span
            class="landing-nexus-hero-line landing-nexus-hero-line-delay block text-zinc-500"
            :class="{ 'is-visible': entered }"
          >Track smarter.</span>
        </span>
      </h1>

      <p
        class="landing-nexus-fade-up mx-auto mb-10 max-w-2xl text-lg leading-relaxed text-zinc-400 sm:text-xl"
        :class="{ 'is-visible': entered }"
        style="transition-delay: 0.5s;"
      >
        The modern platform for teams who need AI Brand Visibility. Built for monitoring, evidence, and steady
        improvement. Everything you need to measure, explain, and prove.
      </p>

      <div
        class="landing-nexus-fade-up mb-16 flex flex-col items-center justify-center gap-4 sm:flex-row"
        :class="{ 'is-visible': entered }"
        style="transition-delay: 0.6s;"
      >
        <button
          type="button"
          class="landing-nexus-shimmer-btn flex h-12 items-center rounded-full bg-white px-8 text-base font-medium text-zinc-950 shadow-lg shadow-white/10 transition-colors duration-200 hover:bg-zinc-200"
          @click="emit('start')"
        >
          Create Brand Space
          <UIcon
            name="i-lucide-arrow-right"
            class="ml-2 h-4 w-4"
          />
        </button>

        <NuxtLink
          to="/"
          class="flex h-12 items-center rounded-full border border-zinc-800 bg-transparent px-8 text-base font-medium text-zinc-300 transition-colors duration-200 hover:border-zinc-700 hover:bg-zinc-900 hover:text-white"
        >
          See Platform
        </NuxtLink>
      </div>

      <div
        class="landing-nexus-fade-up flex flex-col items-center gap-4"
        :class="{ 'is-visible': entered }"
        style="transition-delay: 0.7s;"
      >
        <div class="flex items-center -space-x-3">
          <div
            v-for="(avatar, index) in avatars"
            :key="avatar"
            class="landing-nexus-avatar-reveal relative"
            :class="{ 'is-visible': entered }"
            :style="{ transitionDelay: `${0.8 + index * 0.1}s` }"
          >
            <img
              :src="avatar"
              alt=""
              class="h-10 w-10 rounded-full border-2 border-zinc-950 object-cover"
            >
          </div>
        </div>

        <p class="text-sm text-zinc-500">
          Trusted by <span class="font-medium text-zinc-300">2,000+</span> AEO, SEO, and brand teams worldwide
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.landing-nexus-hero-line {
  transform: translateY(100%);
  transition: transform 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

.landing-nexus-hero-line.is-visible {
  transform: translateY(0);
}

.landing-nexus-hero-line-delay {
  transition-delay: 0.1s;
}

.landing-nexus-avatar-reveal {
  opacity: 0;
  transform: translate3d(-20px, 0, 0) scale(0.5);
  transition:
    opacity 0.4s ease,
    transform 0.4s ease;
}

.landing-nexus-avatar-reveal.is-visible {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale(1);
}
</style>
