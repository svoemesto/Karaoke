// Единое форматирование дат в публичном SPA. Источник — epoch ms (Long),
// реальный момент времени (см. docs/features/guest-share-link.md). Пояс
// отображения — таймзона устройства (FR-011), не серверная МСК.

export function formatDate(epochMs) {
  if (epochMs == null || epochMs === 0 || Number.isNaN(epochMs)) return ''
  const d = new Date(epochMs)
  if (Number.isNaN(d.getTime())) return ''
  // Ручной формат dd.MM.yyyy HH:mm — без запятой между датой и временем
  // (toLocaleString с day/month/year/hour/minute в ru-RU ставит запятую).
  // getDate/getMonth/getHours/getMinutes читают системную TZ (V8/браузер),
  // поэтому гость во Владивостоке видит 16:57, владелец в МСК — 09:57.
  const pad = (n) => n.toString().padStart(2, '0')
  return `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
