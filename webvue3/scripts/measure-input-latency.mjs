// Измеритель задержки ввода в редакторе (#205).
//
// ЗАЧЕМ ЭТО НЕ ТЕСТ (аудит #336, BR-615). Файл прежде лежал в каталоге e2e и
// назывался `measure-input-latency.spec.js`, но НЕ содержал ни одного
// утверждения: только замер и console.log. Он попадал в общий прогон
// `npm run test:e2e` и показывал «зелёный» результат там, где задержка ввода
// не проверялась ничем: упавший редактор, сплошные ошибки в консоли и
// зависший wavesurfer давали тот же «зелёный» статус.
//
// Теперь это измерялка (npm-скрипт `measure:input-latency`), а не спецификация
// набора. Она ничего не «проверяет» и потому ничего не обещает: выводит
// числа, на которые опирается решение по #191/#197.
//
// Метод: Event Timing API (PerformanceObserver по entryTypes: ['event']).
// Для каждого события он даёт:
//   processingEnd - startTime  — время обработчика приложения;
//   duration                  — от события до следующей отрисовки.
// Первая версия измерялки ждала requestAnimationFrame и получила ровно 16.7 мс —
// это длительность кадра при 60 Гц, замер упирался в потолок и ничего не видел.
//
// Запуск:
//   E2E_BASE_URL=http://localhost:7906 npm run measure:input-latency

import { chromium } from '@playwright/test'

import { openEditor, SONG_ID } from '../e2e/fixtures/song.js'

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:7906'

const browser = await chromium.launch({ args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ baseURL: BASE_URL })
const page = await context.newPage()

try {
  await openEditor(page)

  const profile = await page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let inst = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const t = v.component?.type
      if (v.component && t && (t.name || t.__name) === 'SubsEdit') inst = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    if (!inst) throw new Error('экземпляр SubsEdit не найден')
    const p = inst.proxy
    return {
      markers: p.sourceMarkers.length,
      syllables: p.sourceMarkers.filter((m) => m.markertype === 'syllables').length,
      regions: p.wsRegions ? p.wsRegions.getRegions().length : -1,
      textLines: p.sourceText.split('\n').filter((l) => l.trim() !== '').length,
      textLen: p.sourceText.length,
    }
  })
  console.log('ПЕСНЯ: id=' + SONG_ID + ' ' + JSON.stringify(profile))

  // Наблюдатели поднимаем ДО печати.
  await page.evaluate(() => {
    window.__ev = []
    window.__lt = []
    window.__rd = 0
    try {
      new PerformanceObserver((l) => {
        for (const e of l.getEntries()) {
          if (e.name === 'input' || e.name === 'keydown') {
            window.__ev.push({
              name: e.name,
              handler: +(e.processingEnd - e.processingStart).toFixed(2),
              toPaint: +e.duration.toFixed(2),
            })
          }
        }
      }).observe({ type: 'event', buffered: true, durationThreshold: 0 })
    } catch (e) {
      window.__evUnsupported = true
    }
    try {
      new PerformanceObserver((l) => {
        for (const e of l.getEntries()) window.__lt.push(+e.duration.toFixed(1))
      }).observe({ entryTypes: ['longtask'] })
    } catch (e) {
      window.__ltUnsupported = true
    }
  })

  // Настоящий ввод: клик мышью, затем реальные нажатия клавиатуры.
  const box = await page.locator('#editor').boundingBox()
  await page.mouse.click(box.x + 60, box.y + 12)
  await page.waitForTimeout(600)
  await page.keyboard.type('абвгдеёжзиклмноп', { delay: 120 })
  await page.waitForTimeout(1500)

  const res = await page.evaluate(() => {
    const ev = window.__ev
    const lt = window.__lt
    const stat = (a) => {
      if (!a.length) return null
      const s = a.slice().sort((x, y) => x - y)
      return {
        n: a.length,
        min: s[0],
        p50: s[Math.floor(s.length * 0.5)],
        p90: s[Math.floor(s.length * 0.9)],
        max: s[s.length - 1],
      }
    }
    return {
      unsupported: !!window.__evUnsupported,
      handler: stat(ev.map((e) => e.handler)),
      toPaint: stat(ev.map((e) => e.toPaint)),
      longtasks: { n: lt.length, max: lt.length ? Math.max(...lt) : 0, all: lt.slice(0, 15) },
    }
  })
  console.log('РЕЗУЛЬТАТ: ' + JSON.stringify(res))
  console.log(
    'Бюджет задержки ввода — 16 мс (один кадр), см. ' +
      'knowledge/system/frontend/subs-edit-admin-editor.md',
  )
  if (!res.handler) {
    console.error('ЗАМЕР НЕ ПОЛУЧЕН: браузер не отдал Event Timing API')
    process.exitCode = 1
  }
} catch (e) {
  console.error('ИЗМЕРЕНИЕ НЕ УДАЛОСЬ: ' + (e && e.message ? e.message : e))
  process.exitCode = 1
} finally {
  await browser.close()
}
