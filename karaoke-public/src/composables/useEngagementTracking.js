import { onMounted, onBeforeUnmount } from 'vue'
import { trackPageEngagement, trackUi } from '../services/tracking'

// Трекинг вовлечённости на странице: время видимости (в секундах) + глубина скролла по вехам
// 25/50/75/100%. Подключается в setup()-компоненте: useEngagementTracking('song', () => id).
// pageName — идентификатор страницы (home|zakroma|search|song). getSongId — опциональная функция/
// значение id песни (для страницы песни).
export function useEngagementTracking(pageName, getSongId) {
  let accumulatedMs = 0
  let lastStart = null
  const scrollFlags = { 25: false, 50: false, 75: false, 100: false }
  let scrollScheduled = false

  function songId() {
    const v = typeof getSongId === 'function' ? getSongId() : getSongId
    return (v === undefined || v === null || v === '') ? undefined : v
  }

  function startTimer() { if (lastStart === null) lastStart = Date.now() }
  function stopTimer() {
    if (lastStart !== null) { accumulatedMs += Date.now() - lastStart; lastStart = null }
  }

  // Отправляет накопленное время и обнуляет счётчик (чтобы не задвоить при повторном flush на
  // pagehide после visibilitychange).
  function flush() {
    stopTimer()
    if (accumulatedMs >= 1000) trackPageEngagement(pageName, accumulatedMs / 1000, songId())
    accumulatedMs = 0
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'hidden') flush()
    else startTimer()
  }

  function computeScrollPercent() {
    const doc = document.documentElement
    const scrollable = doc.scrollHeight - window.innerHeight
    if (scrollable <= 0) return 100
    return Math.min(100, Math.round((window.scrollY / scrollable) * 100))
  }

  function onScroll() {
    if (scrollScheduled) return
    scrollScheduled = true
    // throttle через rAF — вехи стреляют максимум раз за кадр, а каждая веха один раз за жизнь
    // компонента (флаги), чтобы не заливать tbl_events шумом.
    requestAnimationFrame(() => {
      scrollScheduled = false
      const pct = computeScrollPercent()
      for (const milestone of [25, 50, 75, 100]) {
        if (pct >= milestone && !scrollFlags[milestone]) {
          scrollFlags[milestone] = true
          trackUi('scroll', `${pageName}:${milestone}`, songId())
        }
      }
    })
  }

  onMounted(() => {
    startTimer()
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('pagehide', flush)
    window.addEventListener('scroll', onScroll, { passive: true })
  })

  onBeforeUnmount(() => {
    flush()
    document.removeEventListener('visibilitychange', onVisibilityChange)
    window.removeEventListener('pagehide', flush)
    window.removeEventListener('scroll', onScroll)
  })
}
