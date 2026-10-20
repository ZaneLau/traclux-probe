<script setup lang="ts">
type MonitoringMetric = {
  label: string
  value: string
}

type FeatureCard = {
  title: string
  description: string
  kind: 'command' | 'analytics' | 'speed' | 'security'
}

const monitoringMetrics = ref<MonitoringMetric[]>([
  { label: 'Mention', value: '74%' },
  { label: 'Rank', value: '68%' },
  { label: 'Coverage', value: '83%' },
  { label: 'Evidence', value: '61%' }
])

const featureCards: FeatureCard[] = [
  {
    title: 'Workspace Command',
    description: 'Jump between brand spaces, monitoring, evidence, and source detail with one shortcut.',
    kind: 'command'
  },
  {
    title: 'Visibility Analytics',
    description: 'Read rank movement, source concentration, and recommendation trends in one place.',
    kind: 'analytics'
  },
  {
    title: 'Fast Turnaround',
    description: 'Move from prompt signals to a working visibility view in one compact workflow.',
    kind: 'speed'
  },
  {
    title: 'Evidence Controls',
    description: 'Keep source evidence, answer snapshots, and citations attached to every finding.',
    kind: 'security'
  }
]

const rootRef = ref<HTMLElement | null>(null)
const visible = ref(false)
const monitoringDots = ref([true, true, true, false, true])
const commandPressed = ref(false)

let observer: IntersectionObserver | null = null
let dotsTimer: ReturnType<typeof setInterval> | null = null
let commandTimer: ReturnType<typeof setInterval> | null = null
let commandReleaseTimer: ReturnType<typeof setTimeout> | null = null

function isCardVisible(index: number) {
  return visible.value ? { transitionDelay: `${300 + index * 100}ms` } : undefined
}

onMounted(() => {
  monitoringMetrics.value = ['Mention', 'Rank', 'Coverage', 'Evidence'].map(label => ({
    label,
    value: `${Math.floor(Math.random() * 40 + 60)}%`
  }))

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

  dotsTimer = setInterval(() => {
    monitoringDots.value = monitoringDots.value.map(() => Math.random() > 0.2)
  }, 2000)

  commandTimer = setInterval(() => {
    commandPressed.value = true

    if (commandReleaseTimer) {
      clearTimeout(commandReleaseTimer)
    }

    commandReleaseTimer = setTimeout(() => {
      commandPressed.value = false
    }, 200)
  }, 3000)
})

onBeforeUnmount(() => {
  observer?.disconnect()

  if (dotsTimer) {
    clearInterval(dotsTimer)
  }

  if (commandTimer) {
    clearInterval(commandTimer)
  }

  if (commandReleaseTimer) {
    clearTimeout(commandReleaseTimer)
  }
})
</script>

<template>
  <section
    id="features"
    class="px-4 py-24"
  >
    <div class="mx-auto max-w-6xl">
      <div
        class="landing-nexus-fade-up mb-16 text-center"
        :class="{ 'is-visible': visible }"
      >
        <h2 class="font-heading mb-4 text-3xl font-bold text-white sm:text-4xl">
          Everything you need to respond
        </h2>
        <p class="mx-auto max-w-2xl text-zinc-400">
          Built for modern AEO teams. Powerful surfaces that help you inspect, track, and act faster.
        </p>
      </div>

      <div
        ref="rootRef"
        class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
      >
        <article
          class="landing-nexus-fade-up group relative overflow-hidden rounded-2xl border border-zinc-800 bg-zinc-900 p-6 transition-all duration-300 hover:scale-[1.02] hover:border-zinc-600 md:col-span-2"
          :class="{ 'is-visible': visible }"
          style="transition-delay: 200ms;"
        >
          <div class="mb-8 flex items-start justify-between">
            <div>
              <div class="mb-4 flex w-fit rounded-lg bg-zinc-800 p-2">
                <UIcon
                  name="i-lucide-activity"
                  class="h-5 w-5 text-zinc-400"
                />
              </div>
              <h3 class="mb-2 text-xl font-semibold text-white">
                AI Visibility Monitoring
              </h3>
              <p class="text-sm text-zinc-400">
                Track brand mentions, ranking movement, answer coverage, and evidence shifts across every tracking cycle.
              </p>
            </div>

            <div class="flex items-center gap-2">
              <span
                v-for="(active, index) in monitoringDots"
                :key="index"
                class="h-2 w-2 rounded-full transition-colors duration-300"
                :class="active ? 'landing-nexus-pulse-glow bg-emerald-500' : 'bg-zinc-700'"
              />
            </div>
          </div>

          <div class="grid grid-cols-4 gap-4">
            <div
              v-for="metric in monitoringMetrics"
              :key="metric.label"
              class="text-center"
            >
              <div class="mb-1 text-2xl font-bold text-white">
                {{ metric.value }}
              </div>
              <div class="text-xs text-zinc-500">
                {{ metric.label }}
              </div>
            </div>
          </div>
        </article>

        <article
          v-for="(card, index) in featureCards"
          :key="card.title"
          class="landing-nexus-fade-up group relative rounded-2xl border border-zinc-800 bg-zinc-900 p-6 transition-all duration-300 hover:scale-[1.02] hover:border-zinc-600"
          :class="{ 'is-visible': visible }"
          :style="isCardVisible(index)"
        >
          <div class="mb-4 flex w-fit rounded-lg bg-zinc-800 p-2">
            <UIcon
              v-if="card.kind === 'command'"
              name="i-lucide-command"
              class="h-5 w-5 text-zinc-400"
            />
            <UIcon
              v-else-if="card.kind === 'analytics'"
              name="i-lucide-bar-chart-3"
              class="h-5 w-5 text-zinc-400"
            />
            <UIcon
              v-else-if="card.kind === 'speed'"
              name="i-lucide-zap"
              class="h-5 w-5 text-zinc-400"
            />
            <UIcon
              v-else
              name="i-lucide-shield"
              class="h-5 w-5 text-zinc-400"
            />
          </div>

          <h3 class="mb-2 text-lg font-semibold text-white">
            {{ card.title }}
          </h3>
          <p
            class="text-sm text-zinc-400"
            :class="card.kind === 'analytics' ? 'mb-4' : 'mb-6'"
          >
            {{ card.description }}
          </p>

          <div
            v-if="card.kind === 'command'"
            class="flex items-center gap-1"
          >
            <kbd
              class="rounded border border-zinc-700 bg-zinc-800 px-2 py-1 font-mono text-xs text-zinc-300 transition-transform duration-200"
              :class="commandPressed ? 'translate-y-[2px] scale-95' : ''"
            >
              ⌘
            </kbd>
            <kbd
              class="rounded border border-zinc-700 bg-zinc-800 px-2 py-1 font-mono text-xs text-zinc-300 transition-transform delay-75 duration-200"
              :class="commandPressed ? 'translate-y-[2px] scale-95' : ''"
            >
              K
            </kbd>
          </div>

          <svg
            v-else-if="card.kind === 'analytics'"
            viewBox="0 0 100 70"
            class="h-24 w-full"
          >
            <defs>
              <linearGradient
                id="landing-nexus-chart-gradient"
                x1="0"
                y1="0"
                x2="0"
                y2="1"
              >
                <stop
                  offset="0%"
                  stop-color="rgb(255,255,255)"
                  stop-opacity="0.2"
                />
                <stop
                  offset="100%"
                  stop-color="rgb(255,255,255)"
                  stop-opacity="0"
                />
              </linearGradient>
            </defs>
            <path
              d="M 0 60 L 20 45 L 40 55 L 60 30 L 80 40 L 100 15 L 100 70 L 0 70 Z"
              fill="url(#landing-nexus-chart-gradient)"
              class="opacity-50"
            />
            <path
              d="M 0 60 L 20 45 L 40 55 L 60 30 L 80 40 L 100 15"
              fill="none"
              stroke="white"
              stroke-width="2"
              stroke-linecap="round"
              class="landing-nexus-draw-line"
            />
          </svg>

          <div
            v-else-if="card.kind === 'speed'"
            class="flex items-center gap-2 text-sm text-emerald-500"
          >
            <span class="font-mono">~32s</span>
            <span class="text-zinc-500">avg refresh</span>
          </div>

          <div
            v-else
            class="flex items-center gap-2"
          >
            <span class="rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-400">Links</span>
            <span class="rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-400">Citations</span>
            <span class="rounded bg-zinc-800 px-2 py-1 text-xs text-zinc-400">Snapshots</span>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>
