// Регрессионный тест на «призрачные маркеры» и падение редактора.
// Тикет #190 (жалоба владельца) + #192 (первопричина), карта wayfinder #186.
//
// НАСТОЯЩАЯ ПРИЧИНА (проверено чтением исходника wavesurfer.js 7.12.1,
// node_modules/wavesurfer.js/dist/plugins/regions.esm.js):
//
//   addRegion(options) {
//     const d = this.wavesurfer.getDuration()
//     const r = new Region(options, d, ...)      // this.start = clampPosition(options.start)
//     return d ? this.saveRegion(r)
//              : this.wavesurfer.once('ready', t => { r._setTotalDuration(t); this.saveRegion(r) })
//   }
//   clampPosition(t) { return Math.max(0, Math.min(this.totalDuration, t)) }
//   _setTotalDuration(t) { this.totalDuration = t; this.renderPosition() }   // start НЕ пересчитывает
//
// При getDuration() === 0 clampPosition даёт 0 для ЛЮБОГО start, а на 'ready'
// start уже не восстанавливается — регион навсегда остаётся с start = 0.
// Это и есть «призрачный маркер на нулевой позиции». Кластер фиксировали трижды
// (2be96451, 093a6972, 2ac3e45f, затем 1f2b20b5, bc576b6f) — каждый фикс добавлял
// ещё один проход «создать регионы», то есть ещё одну порцию призраков.
//
// Проверяем на живой песне 11718 «Говорит Деметра» (2170 маркеров, 604 с).
// Прогон детерминирован: при каждом запуске проверяется и падение, и позиции.
//
// Запуск: E2E_BASE_URL=http://localhost:7906 npm run test:e2e -- ghost-markers.spec.js

import { expect, test } from '@playwright/test'

const SONG_ID = 11718

async function openSubsEdit(page) {
  // Список песен тянет POST /api/songs (~81 МБ) — обрываем, он не нужен.
  // ВАЖНО: сравнение по pathname, а не glob: `**/api/songs**` матчит ещё и
  // `/api/songsdigests` (song + s + digests), из-за чего обрывался нужный запрос.
  await page.route(
    (url) => url.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })

  // Песни приходят только с фильтром. Именно loadSongsDigestsPromise — обычный
  // loadSongsDigests промис не возвращает, и await после него не ждёт ничего.
  await page.evaluate(async (id) => {
    const store =
      document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    const raw = await store.dispatch('loadSongsDigestsPromise', { filterId: String(id) })
    store.commit('updateSongsDigests', JSON.parse(raw))
  }, SONG_ID)

  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.locator('#waveform').waitFor({ state: 'attached' })
  // Даём отработать отложенным проходам: decode + redrawMarkers.
  await page.waitForTimeout(8000)
}

// Проверяем по ДАННЫМ плагина wavesurfer, а не по DOM.
//
// Почему не по DOM: в локальной сборке стоит wavesurfer.js 7.12.1, который
// не рендерит регионы в light DOM (`#waveform` остаётся почти пустым) — это
// наблюдалось и ДО фикса, то есть не связано с правкой и не является дефектом
// приложения. Контейнер же собран на более старой версии, где рендеринг есть,
// и из-за этого измерения в двух средах несопоставимы. Данные плагина
// (getRegions() → Region.start) — источник истины для позиции, он одинаков
// в любой версии.
function readRegions(page) {
  return page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let inst = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const n = v.component?.type
      if (v.component && n && (n.name || n.__name) === 'SubsEdit') inst = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    if (!inst) return { err: 'экземпляр SubsEdit не найден' }
    const p = inst.proxy
    const markers = p.sourceMarkers
    const regions = p.wsRegions ? p.wsRegions.getRegions() : []
    const zeroStart = regions.filter((r) => r.start === 0)
    return {
      duration: p.duration,
      markers: markers.length,
      // Сколько маркеров реально имеют time === 0. Это контроль: если в данных
      // таких нет, то и в регионах их быть не должно.
      markersAtZero: markers.filter((m) => m.time === 0).length,
      regions: regions.length,
      regionsAtZero: zeroStart.length,
      maxStart: regions.reduce((m, r) => Math.max(m, r.start), 0),
      markersWithNullRegion: markers.filter((m) => m.region === null).length,
    }
  })
}

test.describe('призрачные маркеры и падение редактора (#190, #192)', () => {
  test('редактор открывается без TypeError', async ({ page }) => {
    test.setTimeout(120_000)
    const errors = []
    page.on('pageerror', (e) => errors.push('pageerror: ' + e.message))
    page.on('console', (m) => {
      if (m.type() === 'error' && !/MIDI/i.test(m.text())) errors.push('console: ' + m.text())
    })

    await openSubsEdit(page)

    // Именно это падение наблюдалось до фикса:
    // TypeError: Cannot read properties of undefined (reading 'setContent')
    // на currentMarker.region при currentMarkersIndex === -1.
    expect(errors, 'редактор не должен падать при инициализации').toEqual([])
  })

  test('регионов ровно столько же, сколько маркеров, и ни один не в нуле', async ({ page }) => {
    test.setTimeout(120_000)
    await openSubsEdit(page)

    const r = await readRegions(page)
    console.log(`REGIONS: ${JSON.stringify(r)}`)

    expect(r.err).toBeUndefined()
    expect(r.duration, 'аудио должно быть декодировано').toBeGreaterThan(0)
    expect(r.markers, 'маркеры должны быть загружены').toBeGreaterThan(100)

    // ГЛАВНОЕ. До фикса: 4761 регион на 2170 маркеров (4340 = 2 × 2170) —
    // два прохода создания до 'ready' плюс компенсирующий redrawMarkers.
    // Теперь создание ровно одно, поэтому регион на маркер один.
    expect(r.regions, 'регион на каждый маркер, без двойного создания').toBe(r.markers)

    // И ни одного региона в нуле: если в данных таких маркеров нет,
    // то нулевые регионы — это ровно те «призраки» из жалобы владельца.
    expect(r.markersAtZero, 'в данных нет маркеров с time === 0').toBe(0)
    expect(r.regionsAtZero, 'призрачных маркеров быть не должно').toBe(0)

    // Позиции реально разнесены по шкале времени.
    expect(r.maxStart, 'позиции маркеров должны быть распределены').toBeGreaterThan(100)

    // Инвариант из #192: ключ `region` есть у каждого маркера, значение может
    // быть null только у скрытых типов. Здесь все типы видимы — значит null не быть.
    expect(r.markersWithNullRegion, 'у всех видимых маркеров должен быть region').toBe(0)
  })
})
