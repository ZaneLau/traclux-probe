import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

export function useLazyChart(targetRef: Ref<HTMLElement | null>, rootMargin = '200px') {
  const isVisible = ref(false)
  let observer: IntersectionObserver | null = null

  onMounted(() => {
    if (!targetRef.value) {
      isVisible.value = true
      return
    }

    observer = new IntersectionObserver(
      (entries) => {
        if (entries.some(entry => entry.isIntersecting)) {
          isVisible.value = true
          observer?.disconnect()
          observer = null
        }
      },
      { rootMargin }
    )

    observer.observe(targetRef.value)
  })

  onBeforeUnmount(() => {
    observer?.disconnect()
    observer = null
  })

  return {
    isVisible
  }
}
