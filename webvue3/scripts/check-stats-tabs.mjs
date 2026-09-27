#!/usr/bin/env node
// Проверка вкладок админ-раздела «Статистика» (OP #184, spec 478).
//
// Что проверяет:
//   1. каждая из 8 вкладок при переключении запрашивает СВОИ endpoint'ы;
//   2. вкладка не остаётся в состоянии вечной загрузки (спиннер обязан исчезнуть);
//   3. в консоли браузера нет необработанных ошибок (pageerror).
//
// Зачем: спека 362 (фикс #79) сделала вкладки ленивыми, но её приёмка была
// «Implemented (code-level)» — вкладки никто не прокликал, и регресс (все
// вкладки, кроме KPI, пустые) прожил 17 дней. Этот скрипт — та самая
// воспроизводимая проверка (spec 478, FR-008).
//
// Запуск (админка должна быть доступна и обслуживать /api):
//   cd webvue3
//   node scripts/check-stats-tabs.mjs [BASE_URL]
//
// BASE_URL по умолчанию http://localhost:7906 (контейнер karaoke-webvue3).
// Для dev-сервера нужен прокси /api → karaoke-app (см. spec 478, «Проверка»).
//
// Требует playwright (devDependency webvue3).

import { chromium } from 'playwright'

const BASE = process.argv[2] || process.env.BASE || 'http://localhost:7906'

// Индекс вкладки → подстроки URL, которые обязаны быть запрошены при её открытии.
const TAB_EXPECTATIONS = [
  { title: 'KPI', expect: ['/api/stats/summary'] },
  { title: 'Монетизация', expect: ['/api/stats/monetization'] },
  { title: 'Динамика', expect: ['/api/stats/timeseries'] },
  { title: 'Разбивки', expect: ['/api/stats/by-type', '/api/stats/channels', '/api/stats/by-detail'] },
  { title: 'География', expect: ['/api/stats/referrers'] },
  { title: 'Пользователи', expect: ['/api/stats/top-users'] },
  { title: 'Слушают', expect: ['/api/stats/top-listened'] },
  { title: 'События', expect: ['/api/stats/by-song', '/api/webevents'] },
]

const TAB_WAIT_MS = Number(process.env.TAB_WAIT_MS || 20000)

async function main() {
  const browser = await chromium.launch()
  const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } })
  const requested = []
  const pageErrors = []
  page.on('response', (r) => {
    if (r.url().includes('/api/')) requested.push(r.url())
  })
  page.on('pageerror', (e) => pageErrors.push(String(e)))

  const failures = []
  await page.goto(`${BASE}/stats`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  await page.waitForTimeout(8000)

  // Активная вкладка KPI грузится на mounted — до первого клика, поэтому для неё
  // базовой отметкой считаем ВСЕ запросы с момента открытия страницы.
  for (const [index, tab] of TAB_EXPECTATIONS.entries()) {
    const before = index === 0 ? 0 : requested.length
    await page.click(`.stats-nav .nav-link:has-text("${tab.title}")`, { timeout: 5000 })

    // Фаза 1: ждём, пока вкладка запросит свои endpoint'ы. Медленный endpoint
    // (например, /api/stats/countries) не должен «вешать» вкладку, но и его
    // отсутствие — не повод падать сразу: ждём до TAB_WAIT_MS.
    const missing = new Set(tab.expect)
    const deadline = Date.now() + TAB_WAIT_MS
    while (missing.size > 0 && Date.now() < deadline) {
      for (const url of requested.slice(before)) {
        for (const exp of [...missing]) {
          if (url.includes(exp)) missing.delete(exp)
        }
      }
      if (missing.size === 0) break
      await page.waitForTimeout(500)
    }

    // Фаза 2: активная панель обязана выйти из состояния загрузки. Считаем
    // спиннеры только в активной панели: BTabs держит в DOM все посещённые
    // панели, и спиннер соседней вкладки — не провал этой.
    const activeSpinners = () =>
      page.evaluate(() => document.querySelectorAll('.tab-pane.active .spinner-border').length)
    let spinners = await activeSpinners()
    const spinnerDeadline = Date.now() + TAB_WAIT_MS
    while (spinners > 0 && Date.now() < spinnerDeadline) {
      await page.waitForTimeout(500)
      spinners = await activeSpinners()
    }

    const seen = tab.expect.filter((e) => requested.slice(before).some((u) => u.includes(e)))
    const status = missing.size === 0 ? 'OK' : `FAIL (нет запросов: ${[...missing].join(', ')})`
    console.log(
      `[${index}] ${tab.title}: ${status}; запросы: ${seen.join(', ') || '(none)'}; спиннеров в активной панели: ${spinners}`,
    )
    if (missing.size > 0) failures.push(`«${tab.title}» не запросила: ${[...missing].join(', ')}`)
    if (spinners > 0) failures.push(`«${tab.title}» осталась в состоянии загрузки (спиннер)`)
  }

  if (pageErrors.length > 0) failures.push(`pageerror: ${pageErrors.join(' | ')}`)

  await browser.close()

  if (failures.length > 0) {
    console.error('\nПРОВАЛ:')
    for (const f of failures) console.error(`  - ${f}`)
    process.exit(1)
  }
  console.log('\nOK: все вкладки «Статистики» запрашивают свои данные и выходят из загрузки.')
}

main().catch((e) => {
  console.error('FATAL:', e)
  process.exit(1)
})
