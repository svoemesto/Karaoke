// BR-304 / BR-305 / BR-306 / BR-307 / BR-308 / BR-309 / BR-310 / BR-311
// (аудит #336, пакет PKG-S3-27, OpenProject #399): инварианты публичного плеера.
//
// ЧТО БЫЛО.
//   - BR-304: catch в init() писал e.message в `#kp-loading` через общий querySelector —
//     то есть в элемент СЛЕДУЮЩЕГО трека; отменённая загрузка давала ложную ошибку.
//   - BR-305: пауза в pre-roll снимала `_isPrerolling`, и `_getDisplayTime()` возвращал
//     `_preroll + pausedAt` (5.0 с) — время прыгало, splash/silent offset пропускались.
//   - BR-306: отложенное seek-событие читало `this.songId` в момент срабатывания таймера.
//   - BR-307: `.then` динамического import('wavesurfer.js') создавал инстансы даже после
//     destroy()/playSong() — дубли canvas и утечка.
//   - BR-308: транспонирование крутило SoundTouch по всем сэмплам без уступки потоку.
//   - BR-309: текстура 4096×4096 (≈64 МиБ) генерировалась на каждый init().
//   - BR-310: чтение localStorage в _restoreTranspose() не было в try/catch.
//   - BR-311: `new OffscreenCanvas` и `ctx.roundRect` — без feature-detection; тело кадра без try/catch.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  INIT_ERROR,
  TRANSPOSE_CHUNK,
  classifyInitError,
  createBackgroundCanvas,
  createSeekTracker,
  hasRoundRect,
  isAbortError,
  isStaleGeneration,
  prerollDisplayTime,
  readStoredNumber,
  roundRectPath,
  shouldGenerateStarfield,
  transposeYieldDue,
} from '../playerGuards.js'
import { readCode, readSource } from '../../utils/__tests__/sourceContract.js'

describe('BR-304: ошибка init() отменённой загрузки не показывается', () => {
  test('AbortError — игнорировать', () => {
    const err = new Error('The user aborted a request')
    err.name = 'AbortError'
    assert.equal(classifyInitError(err), INIT_ERROR.IGNORE)
  })

  test('DOMException с кодом 20 (ABORT_ERR) — игнорировать', () => {
    const err = new Error('aborted')
    err.code = 20
    assert.equal(classifyInitError(err), INIT_ERROR.IGNORE)
  })

  test('флаг aborted (сигнал отменён) — игнорировать', () => {
    assert.equal(classifyInitError(new Error('boom'), { aborted: true }), INIT_ERROR.IGNORE)
  })

  test('устаревшая загрузка (поколение другое) — игнорировать', () => {
    assert.equal(classifyInitError(new Error('boom'), { stale: true }), INIT_ERROR.IGNORE)
  })

  test('настоящая ошибка — показать', () => {
    assert.equal(classifyInitError(new Error('HTTP 500')), INIT_ERROR.SHOW)
  })

  test('isAbortError различает отмену и обычную ошибку', () => {
    assert.equal(isAbortError(new Error('AbortError')), true)
    assert.equal(isAbortError(new Error('HTTP 500')), false)
    assert.equal(isAbortError(null), false)
  })
})

describe('BR-305: пауза в pre-roll не сбивает время', () => {
  test('на паузе отсчёт заморожен — возвращается сохранённая позиция', () => {
    const value = prerollDisplayTime({
      prerolling: true,
      paused: true,
      dtPaused: 2.5,
      elapsedMs: 3000,
    })
    assert.equal(value, 2.5, 'после паузы время не должно прыгать на _preroll')
  })

  test('воспроизведение — позиция растёт от сохранённой', () => {
    const value = prerollDisplayTime({
      prerolling: true,
      paused: false,
      dtPaused: 2,
      elapsedMs: 1000,
    })
    assert.equal(value, 3)
  })

  test('вне pre-roll — null', () => {
    assert.equal(
      prerollDisplayTime({ prerolling: false, paused: false, dtPaused: 0, elapsedMs: 0 }),
      null,
    )
  })
})

describe('BR-306: seek-событие несёт id песни момента перемотки', () => {
  test('id песни фиксируется в момент планирования', async () => {
    const seen = []
    const tracker = createSeekTracker((songId, positionMs) => seen.push([songId, positionMs]))
    tracker.schedule(100, 12.4, 1)
    // Песня сменилась ДО срабатывания таймера.
    tracker.schedule(200, 30.7, 1)
    await new Promise((r) => setTimeout(r, 20))
    assert.equal(seen.length, 1)
    assert.equal(seen[0][0], 200, 'id должен быть из последней перемотки, а не из текущего трека')
    assert.equal(seen[0][1], 31)
  })

  test('cancel() не даёт отправить событие', async () => {
    const seen = []
    const tracker = createSeekTracker((songId) => seen.push(songId))
    tracker.schedule(100, 1, 5)
    tracker.cancel()
    await new Promise((r) => setTimeout(r, 30))
    assert.equal(seen.length, 0)
  })

  test('повторный schedule не плодит события', async () => {
    const seen = []
    const tracker = createSeekTracker((songId) => seen.push(songId))
    tracker.schedule(1, 1, 5)
    tracker.schedule(1, 2, 5)
    tracker.schedule(1, 3, 5)
    await new Promise((r) => setTimeout(r, 30))
    assert.equal(seen.length, 1)
  })
})

describe('BR-307: поколение загрузки отсекает запоздавшие колбэки', () => {
  test('то же поколение — актуально', () => {
    assert.equal(isStaleGeneration(3, 3), false)
  })

  test('поколение сменилось — устарело', () => {
    assert.equal(isStaleGeneration(4, 3), true)
  })
})

describe('BR-308: транспонирование уступает главный поток', () => {
  test('в начале уступки нет', () => {
    assert.equal(transposeYieldDue(0), false)
  })

  test('после каждых N чанков — уступка', () => {
    const stride = TRANSPOSE_CHUNK * 8
    assert.equal(transposeYieldDue(stride), true)
    assert.equal(transposeYieldDue(stride * 2), true)
    assert.equal(transposeYieldDue(stride + TRANSPOSE_CHUNK), false)
  })

  test('на 5-минутной песне уступок достаточно (шаг не реже 8 чанков)', () => {
    const frames = 300 * 44100
    let yields = 0
    for (let w = TRANSPOSE_CHUNK; w <= frames; w += TRANSPOSE_CHUNK) {
      if (transposeYieldDue(w)) yields += 1
    }
    assert.ok(yields > 100, `должно быть много уступок, а не ${yields}`)
  })
})

describe('BR-309: фоновая текстура генерируется один раз на инстанс', () => {
  test('текстуры нет — генерировать', () => {
    assert.equal(shouldGenerateStarfield(null), true)
    assert.equal(shouldGenerateStarfield(undefined), true)
  })

  test('текстура уже есть — НЕ генерировать', () => {
    assert.equal(shouldGenerateStarfield({ width: 4096 }), false)
  })
})

describe('BR-310: localStorage читается безопасно', () => {
  test('хранилище недоступно (SecurityError) — null, без исключения', () => {
    const broken = {
      getItem() {
        throw new Error("Failed to read the 'localStorage' property")
      },
    }
    assert.equal(readStoredNumber(broken, 'kp_transpose_1'), null)
  })

  test('хранилища нет — null', () => {
    assert.equal(readStoredNumber(null, 'kp_transpose_1'), null)
  })

  test('значения нет — null', () => {
    assert.equal(readStoredNumber({ getItem: () => null }, 'k'), null)
  })

  test('корректное значение читается', () => {
    assert.equal(readStoredNumber({ getItem: () => '-3' }, 'k'), -3)
  })

  test('нечисловое значение — null (не NaN)', () => {
    assert.equal(readStoredNumber({ getItem: () => 'abc' }, 'k'), null)
  })
})

describe('BR-311: feature-detection Canvas API', () => {
  test('OffscreenCanvas есть — используется он', () => {
    let used = null
    class Fake {
      constructor(w, h) {
        this.w = w
        this.h = h
        used = 'offscreen'
      }
    }
    const canvas = createBackgroundCanvas(10, 20, { OffscreenCanvas: Fake })
    assert.equal(used, 'offscreen')
    assert.equal(canvas.w, 10)
  })

  test('OffscreenCanvas нет — fallback на <canvas>', () => {
    const doc = {
      createElement(tag) {
        return { tag, width: 0, height: 0 }
      },
    }
    const canvas = createBackgroundCanvas(10, 20, { OffscreenCanvas: undefined, document: doc })
    assert.equal(canvas.tag, 'canvas')
    assert.equal(canvas.width, 10)
    assert.equal(canvas.height, 20)
  })

  test('нет ни OffscreenCanvas, ни document — null (без исключения)', () => {
    assert.equal(createBackgroundCanvas(1, 1, { OffscreenCanvas: undefined, document: null }), null)
  })

  test('hasRoundRect различает браузеры с roundRect и без', () => {
    assert.equal(hasRoundRect({ roundRect() {} }), true)
    assert.equal(hasRoundRect({}), false)
    assert.equal(hasRoundRect(null), false)
  })

  test('roundRectPath использует нативный roundRect, когда он есть', () => {
    const calls = []
    const ctx = {
      roundRect(...args) {
        calls.push(['native', ...args])
      },
      beginPath() {
        calls.push(['beginPath'])
      },
    }
    roundRectPath(ctx, 1, 2, 3, 4, 2)
    assert.deepEqual(calls, [['native', 1, 2, 3, 4, 2]])
  })

  test('roundRectPath рисует прямоугольник с дугами, когда roundRect нет', () => {
    const calls = []
    const ctx = {
      beginPath() {
        calls.push('beginPath')
      },
      moveTo() {
        calls.push('moveTo')
      },
      lineTo() {
        calls.push('lineTo')
      },
      arcTo() {
        calls.push('arcTo')
      },
      closePath() {
        calls.push('closePath')
      },
    }
    roundRectPath(ctx, 0, 0, 10, 10, 4)
    assert.ok(calls.includes('arcTo'), 'скругление должно рисоваться через arcTo')
    assert.ok(calls.includes('closePath'))
  })
})

describe('BR-304…BR-311: контракт исходников (падает на старом коде)', () => {
  test('init(): catch не показывает отменённую загрузку', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    assert.ok(src.includes('classifyInitError'), 'нужна классификация ошибки init()')
    assert.ok(
      /classifyInitError\(e, \{ aborted: controller\.signal\.aborted, stale \}\)/.test(src),
      'catch должен учитывать aborted/stale',
    )
    assert.ok(
      src.includes("loadingEl.style.display = 'none'"),
      'успешный init должен скрывать #kp-loading',
    )
  })

  test('pre-roll: пауза не сбрасывает _isPrerolling', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    const pause = src.slice(src.indexOf('  _pause() {'), src.indexOf('  _stopSources()'))
    assert.ok(
      src.includes('_prerollPaused = true'),
      'нужен отдельный флаг паузы в pre-roll',
    )
    assert.ok(
      !/this\._isPrerolling = false/.test(pause),
      'в _pause() _isPrerolling не должен сбрасываться (иначе время прыгает)',
    )
  })

  test('seek-событие: id фиксируется в момент перемотки', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    assert.ok(src.includes('createSeekTracker'), 'нужен трекер seek-события')
    assert.ok(
      /this\._seekTracker\.schedule\(this\.songId, dt, 400\)/.test(src),
      'songId должен передаваться в момент перемотки',
    )
    assert.ok(
      !/trackPlayerSeek\(this\.songId/.test(src),
      'songId не должен читаться в колбэке таймера',
    )
  })

  test('WaveSurfer: поколение загрузки проверяется перед созданием', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    assert.ok(src.includes('isStaleGeneration'), 'нужна проверка поколения')
    assert.ok(
      /const myGeneration = this\._loadGeneration/.test(src),
      'поколение должно фиксироваться перед import()',
    )
  })

  test('транспонирование асинхронное (чанки с уступкой)', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    assert.ok(src.includes('async _transposeBuffer('), 'транспонирование должно быть async')
    assert.ok(
      src.includes('transposeYieldDue(written, CHUNK)'),
      'между чанками нужна уступка главного потока',
    )
  })

  test('фоновая текстура не генерируется на каждый init()', () => {
    const src = readCode('src/player/KaraokePlayer.js')
    assert.ok(src.includes('shouldGenerateStarfield'), 'нужна проверка «текстура уже есть»')
    assert.ok(
      /if \(shouldGenerateStarfield\(this\._bgCanvas\)\) \{[\s\S]{0,120}this\._bgCanvas = this\._generateStarfield\(\)/.test(
        src,
      ),
      'генерация текстуры должна быть под guard-ом (один раз на инстанс)',
    )
  })

  test('localStorage в _restoreTranspose читается безопасно', () => {
    const src = readSource('src/player/KaraokePlayer.js')
    assert.ok(
      src.includes("readStoredNumber(localStorage, 'kp_transpose_'"),
      'чтение должно идти через безопасный хелпер',
    )
    assert.ok(
      !/const saved = localStorage\.getItem\('kp_transpose_'/.test(src),
      'прямое чтение localStorage без try/catch — старый баг',
    )
  })

  test('Canvas: feature-detection и try/catch в рендер-цикле', () => {
    const src = readCode('src/player/KaraokePlayer.js')
    assert.ok(src.includes('createBackgroundCanvas'), 'нужен fallback вместо OffscreenCanvas')
    assert.ok(!/new OffscreenCanvas\(/.test(src), 'прямой вызов OffscreenCanvas без проверки — старый баг')
    assert.ok(src.includes('roundRectPath'), 'roundRect должен идти через helper с fallback')
    assert.ok(!/ctx\.roundRect\(/.test(src), 'прямые вызовы ctx.roundRect — старый баг')
    const loop = src.slice(src.indexOf('  _startRenderLoop() {'), src.indexOf('// ─── Background'))
    assert.ok(
      /try\s*\{[\s\S]{0,200}this\._renderFrame\(\)[\s\S]{0,200}catch/.test(loop),
      'рендер-цикл должен переживать ошибку кадра',
    )
  })
})
