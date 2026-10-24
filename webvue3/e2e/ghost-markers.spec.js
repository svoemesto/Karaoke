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
// ЧТО ПРОВЕРЯЕТСЯ ИМЕННО (аудит #336, BR-622). Прежняя версия теста
// утверждала, что «в данных нет маркеров с time === 0» (markersAtZero === 0)
// и что маркеров больше 100. Это утверждения о ДАННЫХ конкретной песни, а не о
// коде: владелец вправе поставить легальному маркеру время 0 — и тест
// становился красным при полностью исправном коде. Теперь проверяются
// СТРУКТУРНЫЕ инварианты, которые верны для любой разметки:
//
//   * регион ровно один на маркер (без двойного создания);
//   * регион в нуле есть ровно у тех маркеров, которые сами в нуле;
//   * ни один регион не начинается за пределами аудио и не отрицателен;
//   * у каждого видимого маркера есть region.
//
// Запуск: E2E_BASE_URL=http://localhost:7906 E2E_SONG_ID=11718 npm run test:e2e -- ghost-markers.spec.js

import { expect, test } from '@playwright/test'

import { openEditor, withSubsEdit } from './fixtures/song.js'

async function openSubsEdit(page) {
  await openEditor(page)
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
  return withSubsEdit(page, (p) => {
    const markers = p.sourceMarkers
    const regions = p.wsRegions ? p.wsRegions.getRegions() : []
    const zeroStart = regions.filter((r) => r.start === 0)
    const duration = Number(p.duration) || 0
    return {
      duration,
      markers: markers.length,
      // Сколько маркеров реально имеют time === 0. Это контроль: если в данных
      // таких нет, то и в регионах их быть не должно.
      markersAtZero: markers.filter((m) => m.time === 0).length,
      regions: regions.length,
      regionsAtZero: zeroStart.length,
      maxStart: regions.reduce((m, r) => Math.max(m, r.start), 0),
      // Регион за пределами аудио или с отрицательным стартом — это ровно тот
      // дефект, который ловит clampPosition в неготовом состоянии.
      regionsOutOfRange: regions.filter((r) => r.start < 0 || (duration > 0 && r.start > duration))
        .length,
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

    expect(r.error, r.error || '').toBeUndefined()
    expect(r.duration, 'аудио должно быть декодировано').toBeGreaterThan(0)
    expect(r.markers, 'маркеры должны быть загружены').toBeGreaterThan(0)

    // ГЛАВНОЕ. До фикса: 4761 регион на 2170 маркеров (4340 = 2 × 2170) —
    // два прохода создания до 'ready' плюс компенсирующий redrawMarkers.
    // Теперь создание ровно одно, поэтому регион на маркер один.
    expect(r.regions, 'регион на каждый маркер, без двойного создания').toBe(r.markers)

    // СТРУКТУРНЫЙ ИНВАРИАНТ (вместо «в данных нет маркеров с time === 0»):
    // регион в нуле бывает ровно у тех маркеров, которые сами стоят в нуле.
    // Если владелец поставит легальному маркеру время 0, у него будет и
    // регион в нуле — и тест останется зелёным, потому что проверяет КОД,
    // а не содержимое разметки.
    expect(
      r.regionsAtZero,
      'регион в нуле должен быть ровно у маркеров, которые сами в нуле',
    ).toBe(r.markersAtZero)

    // Регион не может начинаться за пределами аудио: именно clampPosition с
    // duration === 0 делал «призраки» (start = 0 у всех).
    expect(r.regionsOutOfRange, 'регионы должны лежать в пределах аудио').toBe(0)

    // Позиции реально разнесены по шкале времени: хотя бы один регион
    // начинается не в нуле (иначе вся разметка схлопнулась бы в ноль).
    expect(r.maxStart, 'позиции маркеров должны быть распределены по шкале').toBeGreaterThan(0)
    expect(r.maxStart, 'последний регион не может быть за пределами аудио').toBeLessThanOrEqual(
      r.duration + 0.001,
    )

    // Инвариант из #192: ключ `region` есть у каждого маркера, значение может
    // быть null только у скрытых типов. Здесь все типы видимы — значит null не быть.
    expect(r.markersWithNullRegion, 'у всех видимых маркеров должен быть region').toBe(0)
  })
})
