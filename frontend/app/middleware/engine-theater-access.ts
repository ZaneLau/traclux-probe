export default defineNuxtRouteMiddleware(() => {
  const { isEngineTheaterEnabled } = useFeatureAvailability()

  if (isEngineTheaterEnabled.value) {
    return
  }

  throw createError({
    statusCode: 404,
    statusMessage: 'Not Found'
  })
})
