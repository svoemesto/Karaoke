// #205: измерение задержки ввода в редакторе. Задача НИЧЕГО не решает —
// только измеряет, чтобы решение по #191/#197 опиралось на числа.
//
// Метод: Event Timing API (PerformanceObserver по entryTypes: ['event']).
// Для каждого события он даёт:
//   processingEnd - startTime  — время обработчика приложения;
//   duration                  — от события до следующей отрисовки.
// Первая версия теста ждала requestAnimationFrame и получила ровно 16.7 мс —
// это длительность кадра при 60 Гц, замер упирался в потолок и ничего не видел.

import { test } from '@playwright/test'

test.setTimeout(300000)

test('#205: задержка ввода — измерение', async ({ page }) => {
  await page.route(
    (u) => u.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })
  await page.evaluate(async () => {
    const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    s.commit(
      'updateSongsDigests',
      JSON.parse(await s.dispatch('loadSongsDigestsPromise', { filterId: '11718' })),
    )
  })
  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.locator('#waveform').waitFor({ state: 'attached' })
  await page.waitForTimeout(12000)

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
    const p = inst.proxy
    return {
      markers: p.sourceMarkers.length,
      syllables: p.sourceMarkers.filter((m) => m.markertype === 'syllables').length,
      regions: p.wsRegions ? p.wsRegions.getRegions().length : -1,
      textLines: p.sourceText.split('\n').filter((l) => l.trim() !== '').length,
      textLen: p.sourceText.length,
    }
  })
  console.log('PROFILE: ' + JSON.stringify(profile))

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
  console.log('RESULT: ' + JSON.stringify(res))
})
