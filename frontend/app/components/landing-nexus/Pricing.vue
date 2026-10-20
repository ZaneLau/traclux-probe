<script setup lang="ts">
type BillingCycle = 'monthly' | 'yearly'

type PricingPlan = {
  name: string
  description: string
  price: Record<BillingCycle, number>
  features: string[]
  cta: string
  highlighted: boolean
}

const emit = defineEmits<{
  start: []
}>()

const plans: PricingPlan[] = [
  {
    name: 'Starter',
    description: 'Perfect for one brand and a weekly visibility loop',
    price: { monthly: 0, yearly: 0 },
    features: ['1 brand workspace', '10 guided runs per month', 'Basic tracking', 'Workspace summaries', '1GB evidence storage'],
    cta: 'Get Started',
    highlighted: false
  },
  {
    name: 'Pro',
    description: 'For growing teams that need continuous visibility',
    price: { monthly: 29, yearly: 24 },
    features: [
      'Unlimited team members',
      'Unlimited guided runs',
      'Advanced analytics',
      'Priority support',
      '100GB evidence storage',
      'Source evidence exports',
      'API access'
    ],
    cta: 'Start Free Trial',
    highlighted: true
  },
  {
    name: 'Enterprise',
    description: 'For organizations with governance and collaboration needs',
    price: { monthly: 99, yearly: 79 },
    features: [
      'Everything in Pro',
      'SSO & SAML',
      'Dedicated support',
      'SLA guarantee',
      'Unlimited storage',
      'Custom connectors',
      'Audit logs'
    ],
    cta: 'Contact Sales',
    highlighted: false
  }
]

const rootRef = ref<HTMLElement | null>(null)
const visible = ref(false)
const billingCycle = ref<BillingCycle>('monthly')

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
  <section
    id="pricing"
    class="px-4 py-24"
  >
    <div class="mx-auto max-w-6xl">
      <div
        class="landing-nexus-fade-up mb-12 text-center"
        :class="{ 'is-visible': visible }"
      >
        <h2 class="font-heading mb-4 text-3xl font-bold text-white sm:text-4xl">
          Simple, transparent pricing
        </h2>
        <p class="mx-auto mb-8 max-w-2xl text-zinc-400">
          Start with one brand space, scale into continuous monitoring. No hidden fees, no surprises.
        </p>

        <div class="inline-flex items-center rounded-full border border-zinc-800 bg-zinc-900 p-1">
          <button
            type="button"
            class="relative rounded-full px-4 py-2 text-sm font-medium transition-colors"
            :class="billingCycle === 'monthly' ? 'text-white' : 'text-zinc-400'"
            @click="billingCycle = 'monthly'"
          >
            <span
              v-if="billingCycle === 'monthly'"
              class="absolute inset-0 rounded-full bg-zinc-800"
            />
            <span class="relative z-10">Monthly</span>
          </button>
          <button
            type="button"
            class="relative rounded-full px-4 py-2 text-sm font-medium transition-colors"
            :class="billingCycle === 'yearly' ? 'text-white' : 'text-zinc-400'"
            @click="billingCycle = 'yearly'"
          >
            <span
              v-if="billingCycle === 'yearly'"
              class="absolute inset-0 rounded-full bg-zinc-800"
            />
            <span class="relative z-10">Yearly</span>
            <span class="relative z-10 ml-2 rounded-full bg-emerald-500/20 px-2 py-0.5 text-xs text-emerald-400">-20%</span>
          </button>
        </div>
      </div>

      <div
        ref="rootRef"
        class="grid grid-cols-1 gap-6 md:grid-cols-3"
      >
        <article
          v-for="(plan, index) in plans"
          :key="plan.name"
          class="landing-nexus-fade-up relative rounded-2xl border p-6 transition-all duration-300 hover:scale-[1.02]"
          :class="[
            visible ? 'is-visible' : '',
            plan.highlighted ? 'border-zinc-700 bg-zinc-900' : 'border-zinc-800 bg-zinc-900/50 hover:border-zinc-600'
          ]"
          :style="visible ? { transitionDelay: `${300 + index * 100}ms` } : undefined"
        >
          <div
            v-if="plan.highlighted"
            class="pointer-events-none absolute inset-0 overflow-hidden rounded-2xl"
          >
            <div
              class="landing-nexus-border-beam absolute h-24 w-24 bg-white/20 blur-xl"
              style="offset-path: rect(0 100% 100% 0 round 16px);"
            />
          </div>

          <div
            v-if="plan.highlighted"
            class="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-white px-3 py-1 text-xs font-medium text-zinc-950"
          >
            Most Popular
          </div>

          <div class="mb-6">
            <h3 class="text-xl font-semibold text-white">
              {{ plan.name }}
            </h3>
            <p class="mt-2 text-sm text-zinc-400">
              {{ plan.description }}
            </p>
          </div>

          <div class="mb-6">
            <div class="flex items-baseline gap-1">
              <span class="text-4xl font-bold text-white">${{ plan.price[billingCycle] }}</span>
              <span
                v-if="plan.price.monthly > 0"
                class="text-sm text-zinc-400"
              >/month</span>
            </div>
            <p
              v-if="billingCycle === 'yearly' && plan.price.yearly > 0"
              class="mt-1 text-xs text-zinc-500"
            >
              Billed annually (${{ plan.price.yearly * 12 }}/year)
            </p>
          </div>

          <ul class="mb-8 space-y-3">
            <li
              v-for="feature in plan.features"
              :key="feature"
              class="flex items-center gap-3 text-sm text-zinc-300"
            >
              <UIcon
                name="i-lucide-check"
                class="h-4 w-4 shrink-0 text-emerald-500"
              />
              {{ feature }}
            </li>
          </ul>

          <button
            type="button"
            class="w-full rounded-full px-4 py-3 text-sm font-medium"
            :class="plan.highlighted ? 'landing-nexus-shimmer-btn bg-white text-zinc-950 hover:bg-zinc-200' : 'border border-zinc-700 bg-zinc-800 text-white hover:bg-zinc-700'"
            @click="emit('start')"
          >
            {{ plan.cta }}
          </button>
        </article>
      </div>
    </div>
  </section>
</template>
