// Регрессии PKG-S2-18 (аудит #336) по плееру (офлайн-рендер MP4).
//
//   BR-483 — анимация стрелок DEMO-экрана шла по `Date.now()`: кадры офлайн-рендера
//            снимаются не в реальном времени, поэтому один и тот же dt давал разный
//            кадр, а повторный рендер — другой файл (нарушение детерминизма);
//   BR-484 — в режиме `inlineData` (превью редактора) `this.songId` не задавался,
//            и «Сохранить файл» уходило на `/api/song/undefined/playerfile`, ошибка
//            проглатывалась в console;
//   BR-485 — карта подписей версии на сплэше не покрывала CHORDS/TABS, поэтому
//            третий блок сплэша в MP4 для этих версий не рисовался.
//
// Метод `_renderDemoEndScreen` вызывается на фейковом canvas-контексте: проверяется
// именно позиция стрелки, а не наличие строки в исходнике.

import { test, describe, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import KaraokePlayer from '../KaraokePlayer.js'

const SOURCE = readFileSync(new URL('../KaraokePlayer.js', import.meta.url), 'utf-8')

const originalGlobals = {
  window: globalThis.window,
  localStorage: globalThis.localStorage,
}

before(() => {
  // В Node нет браузерных глобалов, к которым обращается конструктор плеера.
  globalThis.window = { self: {}, top: {}, location: { search: '' } }
  globalThis.localStorage = { getItem: () => null, setItem: () => {} }
})

after(() => {
  if (originalGlobals.window === undefined) delete globalThis.window
  else globalThis.window = originalGlobals.window
  if (originalGlobals.localStorage === undefined) delete globalThis.localStorage
  else globalThis.localStorage = originalGlobals.localStorage
})

/**
 * Минимальный 2d-контекст: запоминает координаты нарисованных стрелок.
 *
 * @returns {{ctx: Object, arrows: Array}}
 */
const createContext = () => {
  const arrows = []
  const ctx = {
    save() {},
    restore() {},
    measureText: () => ({ width: 40 }),
    fillText(text, x, y) {
      if (text === '▼') arrows.push({ x, y })
    },
  }
  return { ctx, arrows }
}

/**
 * Плеер с минимальным состоянием, достаточным для `_renderDemoEndScreen`.
 *
 * @returns {Object}
 */
const createDemoPlayer = () =>
  Object.assign(Object.create(KaraokePlayer.prototype), {
    _renderVersion: 'DEMO',
    _logoImg: null,
  })

describe('BR-483: DEMO-стрелки позиционируются по времени кадра', () => {
  test('один и тот же dt даёт один и тот же кадр', () => {
    const player = createDemoPlayer()
    const first = createContext()
    const second = createContext()
    player._renderDemoEndScreen(first.ctx, 200, 200, 1, 123.456)
    player._renderDemoEndScreen(second.ctx, 200, 200, 1, 123.456)
    assert.equal(first.arrows.length, 2, 'на экране две стрелки')
    assert.deepEqual(first.arrows, second.arrows, 'кадр должен быть воспроизводим по dt')
  })

  test('разное время кадра даёт разную фазу анимации', () => {
    const player = createDemoPlayer()
    const zero = createContext()
    const shifted = createContext()
    player._renderDemoEndScreen(zero.ctx, 200, 200, 1, 0)
    player._renderDemoEndScreen(shifted.ctx, 200, 200, 1, 0.47)
    assert.notEqual(zero.arrows[0].y, shifted.arrows[0].y, 'фаза должна зависеть от времени кадра')
  })

  test('в методе нет обращения к стенным часам', () => {
    const start = SOURCE.indexOf('_renderDemoEndScreen(ctx, W, H, alpha')
    assert.ok(start > 0, 'метод должен существовать')
    const body = SOURCE.slice(start, SOURCE.indexOf('_renderLoadingIndicator', start))
    assert.doesNotMatch(
      body,
      /Date\.now\(\)\s*\/\s*300/,
      'Date.now() делает офлайн-кадр недетерминированным',
    )
    assert.match(body, /Math\.sin\(timeSec \/ 0\.3\)/)
  })

  test('вызов из кадра передаёт dt', () => {
    assert.match(SOURCE, /this\._renderDemoEndScreen\(ctx, W, H, fadeIn \* fadeOut, dt\)/)
  })
})

describe('BR-484: сохранение файла в inline-режиме знает id песни', () => {
  test('конструктор в ветке inlineData берёт id из данных', () => {
    const player = new KaraokePlayer({}, { inlineData: { id: 42 } }, '/api')
    assert.equal(player.songId, 42, 'без songId меню «Сохранить файл» не работает')
    assert.equal(player._getPlayerFileUrl(), '/api/song/42/playerfile')
  })

  test('без id URL не собирается, а ошибка показывается пользователю', () => {
    const player = new KaraokePlayer({}, { inlineData: {} }, '/api')
    assert.equal(player.songId, null)
    assert.equal(player._getPlayerFileUrl(), null, 'нельзя собирать /song/undefined/playerfile')
    assert.match(SOURCE, /this\._notifyMenuError\('Не удалось сохранить файл'\)/)
    assert.match(SOURCE, /if \(!fileUrl\) throw new Error\('song id is unknown'\)/)
  })
})

describe('BR-485: подписи версии на сплэше покрывают весь RenderVersion', () => {
  // Значения бэкендового enum RenderVersion (PlayerMp4RenderService.kt).
  const BACKEND_VERSIONS = ['LYRICS', 'KARAOKE', 'CHORDS', 'TABS', 'DEMO']

  test('карта содержит все версии с непустыми label/comment', () => {
    const map = KaraokePlayer.RENDER_VERSION_SPLASH
    assert.ok(map, 'статическая карта должна существовать')
    for (const version of BACKEND_VERSIONS) {
      assert.ok(map[version], `нет подписи для версии ${version}`)
      assert.equal(typeof map[version].label, 'string')
      assert.equal(typeof map[version].comment, 'string')
      assert.ok(map[version].label.length > 0)
    }
    assert.equal(map.CHORDS.label, 'Chords')
    assert.equal(map.TABS.label, 'Tabs')
  })

  test('сплэш использует общую карту, а не локальную копию', () => {
    assert.match(SOURCE, /KaraokePlayer\.RENDER_VERSION_SPLASH\[this\._renderVersion\]/)
  })
})
