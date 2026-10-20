<script setup lang="ts">
const footerLinks: Record<string, string[]> = {
  Product: ['Workspace', 'Monitoring', 'Evidence', 'AI Sources', 'API'],
  Resources: ['Documentation', 'Playbooks', 'Blog', 'Community', 'Templates'],
  Company: ['About', 'Careers', 'Press', 'Partners', 'Contact'],
  Legal: ['Privacy', 'Terms', 'Security', 'Cookies', 'Licenses']
}

const currentYear = new Date().getFullYear()
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
      threshold: 0.08,
      rootMargin: '0px 0px -50px 0px'
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
  <footer
    id="docs"
    ref="rootRef"
    class="border-t border-zinc-800 bg-zinc-950"
  >
    <div class="mx-auto max-w-6xl px-4 py-16">
      <div
        class="landing-nexus-fade-up grid grid-cols-2 gap-8 md:grid-cols-5"
        :class="{ 'is-visible': visible }"
      >
        <div class="col-span-2 md:col-span-1">
          <a
            href="#"
            class="mb-4 flex items-center gap-2"
          >
            <GeoBrandMark
              tone="dark"
              size="compact"
            />
          </a>

          <p class="mb-4 text-sm text-zinc-500">
            AI Brand Visibility for teams who need monitoring, evidence, and source clarity.
          </p>

          <div class="inline-flex items-center gap-2 rounded-full border border-zinc-800 bg-zinc-900 px-3 py-1.5">
            <span class="landing-nexus-pulse-glow h-2 w-2 rounded-full bg-emerald-500" />
            <span class="text-xs text-zinc-400">Tracking Active</span>
          </div>
        </div>

        <div
          v-for="(links, title) in footerLinks"
          :id="title === 'Resources' ? 'blog' : undefined"
          :key="title"
        >
          <h4 class="mb-4 text-sm font-semibold text-white">
            {{ title }}
          </h4>
          <ul class="space-y-3">
            <li
              v-for="link in links"
              :key="link"
            >
              <a
                href="#"
                class="text-sm text-zinc-500 transition-colors duration-200 hover:text-white"
              >{{ link }}</a>
            </li>
          </ul>
        </div>
      </div>

      <div
        class="landing-nexus-fade-in mt-16 flex flex-col items-center justify-between gap-4 border-t border-zinc-800 pt-8 sm:flex-row"
        :class="{ 'is-visible': visible }"
        style="transition-delay: 300ms;"
      >
        <p class="text-sm text-zinc-500">
          &copy; {{ currentYear }} Traclux. All rights reserved.
        </p>
        <div class="flex items-center gap-6">
          <a
            href="#"
            class="text-sm text-zinc-500 transition-colors duration-200 hover:text-white"
          >Status</a>
          <a
            href="#"
            class="text-sm text-zinc-500 transition-colors duration-200 hover:text-white"
          >GitHub</a>
          <a
            href="#"
            class="text-sm text-zinc-500 transition-colors duration-200 hover:text-white"
          >Contact</a>
        </div>
      </div>
    </div>
  </footer>
</template>
