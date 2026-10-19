<template>
  <main class="min-h-screen bg-[var(--traclux-surface-canvas)] text-[var(--traclux-text-primary)]">
    <div class="min-h-screen bg-[radial-gradient(circle_at_top_left,rgba(56,189,248,0.08),transparent_24%),radial-gradient(circle_at_bottom_right,rgba(74,130,125,0.07),transparent_20%),linear-gradient(180deg,#f8fafc_0%,#f3f4f6_100%)]">
      <div class="mx-auto flex min-h-screen max-w-[1700px] gap-5 px-3 py-3 lg:px-5">
        <AppSidebar />
        <div class="min-w-0 flex-1 overflow-y-auto pr-1">
          <slot />
        </div>
      </div>
    </div>
  </main>
</template>
