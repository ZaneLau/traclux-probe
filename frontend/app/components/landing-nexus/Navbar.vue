<script setup lang="ts">
import type { ComponentPublicInstance } from 'vue'

type NavItem = {
  label: string
  href: string
}

const emit = defineEmits<{
  start: []
}>()

const navItems: NavItem[] = [
  { label: 'Workspace', href: '#features' },
  { label: 'Monitoring', href: '#pricing' },
  { label: 'Evidence', href: '#docs' },
  { label: 'Resources', href: '#blog' }
]

const mobileMenuOpen = ref(false)
const entered = ref(false)
const navRailRef = ref<HTMLElement | null>(null)
const navItemRefs = ref<Array<HTMLElement | null>>([])
const hoverPill = ref({
  width: '0px',
  height: '0px',
  transform: 'translate(0px, 0px)',
  opacity: 0
})

function setNavItemRef(element: Element | ComponentPublicInstance | null, index: number) {
  navItemRefs.value[index] = element instanceof HTMLElement ? element : null
}

function setNavItemRefAt(index: number) {
  return (element: Element | ComponentPublicInstance | null) => {
    setNavItemRef(element, index)
  }
}

function updateHover(index: number | null) {
  if (index === null || !navRailRef.value) {
    hoverPill.value.opacity = 0
    return
  }

  const target = navItemRefs.value[index]

  if (!target) {
    hoverPill.value.opacity = 0
    return
  }

  const railRect = navRailRef.value.getBoundingClientRect()
  const itemRect = target.getBoundingClientRect()

  hoverPill.value = {
    width: `${itemRect.width}px`,
    height: `${itemRect.height}px`,
    transform: `translate(${itemRect.left - railRect.left}px, ${itemRect.top - railRect.top}px)`,
    opacity: 1
  }
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function triggerStart() {
  mobileMenuOpen.value = false
  emit('start')
}

onMounted(() => {
  requestAnimationFrame(() => {
    entered.value = true
  })
})
</script>

<template>
  <header
    class="fixed left-1/2 top-4 z-50 w-[calc(100%-2rem)] max-w-3xl -translate-x-1/2 transition-all duration-[600ms] ease-[cubic-bezier(0.22,1,0.36,1)]"
    :class="entered ? 'translate-y-0 opacity-100' : '-translate-y-24 opacity-0'"
  >
    <nav class="relative flex items-center justify-between rounded-full border border-zinc-800 bg-zinc-900/40 px-4 py-3 backdrop-blur-md">
      <a
        href="#"
        class="flex items-center gap-2"
      >
        <GeoBrandMark
          tone="dark"
          size="compact"
        />
      </a>

      <div
        ref="navRailRef"
        class="relative hidden items-center gap-1 md:flex"
        @mouseleave="updateHover(null)"
      >
        <span
          class="pointer-events-none absolute rounded-full bg-zinc-800 transition-all duration-300 ease-[cubic-bezier(0.22,1,0.36,1)]"
          :style="hoverPill"
        />

        <a
          v-for="(item, index) in navItems"
          :ref="setNavItemRefAt(index)"
          :key="item.label"
          :href="item.href"
          class="relative px-4 py-2 text-sm text-zinc-400 transition-colors duration-200 hover:text-white"
          @mouseenter="updateHover(index)"
        >
          <span class="relative z-10">{{ item.label }}</span>
        </a>
      </div>

      <div class="hidden items-center gap-3 md:flex">
        <NuxtLink
          to="/login"
          class="rounded-full px-3 py-2 text-sm text-zinc-400 transition-colors duration-200 hover:bg-zinc-800 hover:text-white"
        >
          Log In
        </NuxtLink>
        <button
          type="button"
          class="landing-nexus-shimmer-btn rounded-full bg-white px-4 py-2 text-sm font-medium text-zinc-950 transition-colors duration-200 hover:bg-zinc-200"
          @click="triggerStart"
        >
          Create Brand Space
        </button>
      </div>

      <button
        type="button"
        class="p-2 text-zinc-400 transition-colors duration-200 hover:text-white md:hidden"
        aria-label="Toggle menu"
        @click="mobileMenuOpen = !mobileMenuOpen"
      >
        <UIcon
          v-if="mobileMenuOpen"
          name="i-lucide-x"
          class="h-5 w-5"
        />
        <UIcon
          v-else
          name="i-lucide-menu"
          class="h-5 w-5"
        />
      </button>
    </nav>

    <Transition name="landing-nexus-mobile-menu">
      <div
        v-if="mobileMenuOpen"
        class="absolute left-0 right-0 top-full mt-2 rounded-2xl border border-zinc-800 bg-zinc-900/95 p-4 backdrop-blur-md md:hidden"
      >
        <div class="flex flex-col gap-2">
          <a
            v-for="item in navItems"
            :key="item.label"
            :href="item.href"
            class="rounded-lg px-4 py-3 text-sm text-zinc-400 transition-colors duration-200 hover:bg-zinc-800 hover:text-white"
            @click="closeMobileMenu"
          >
            {{ item.label }}
          </a>

          <hr class="my-2 border-zinc-800">

          <NuxtLink
            to="/login"
            class="rounded-lg px-4 py-3 text-sm text-zinc-400 transition-colors duration-200 hover:bg-zinc-800 hover:text-white"
            @click="closeMobileMenu"
          >
            Log In
          </NuxtLink>

          <button
            type="button"
            class="landing-nexus-shimmer-btn rounded-full bg-white px-4 py-3 text-sm font-medium text-zinc-950 transition-colors duration-200 hover:bg-zinc-200"
            @click="triggerStart"
          >
            Create Brand Space
          </button>
        </div>
      </div>
    </Transition>
  </header>
</template>

<style scoped>
.landing-nexus-mobile-menu-enter-active,
.landing-nexus-mobile-menu-leave-active {
  transition:
    opacity 0.2s ease,
    transform 0.2s ease;
}

.landing-nexus-mobile-menu-enter-from,
.landing-nexus-mobile-menu-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
