// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — онлайн-редактор.
//
// Записи пакета в этом файле:
//   BR-418 — `saveNow()` при `saving` делал early return: правка, пришедшая во время
//            запроса, терялась, а `saveState` становился 'saved'.
//   BR-419 — модалка ревью открывалась независимо от результата; для отсутствующего
//            задания она становилась ловушкой (кнопка «Закрыть» внутри `v-if="a"`).
//   BR-421 — цикл массового назначения не отменялся при закрытии модалки.
//   BR-422 — сбой поиска превращался в «Ничего не найдено».
//   BR-423 — у загрузки дайджеста заданий не было поколения запроса; отказ глотался.
//   BR-425 — код `song_not_found_in_local_db` не разбирался (общий текст ошибки).
//   BR-427 — `loadEditorDefaultTarget` сравнивал DTO со строкой: defaultTarget всегда
//            становился 'remote'.
//   BR-428 — локальная копия настроек редактора знала только два ключа и затирала
//            остальные настройки публичного редактора.
//   BR-430 — удержание клавиши не останавливалось при потере фокуса окна.
//   BR-431 — «Скрыть плеер» не уничтожал инстанс плеера (WebAudio продолжал работать).
//   BR-433 — ошибка загрузки дайджеста заданий глоталась; список выдавался за актуальный.
//
// Запуск: cd webvue3 && node --test src/components/SongEditor/__tests__/pkg-s3-32-songeditor.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { FakeXhr } from '../../../test-utils/fakeXhr.js'

/**
 * Подключает модуль стора ДИНАМИЧЕСКИ.
 *
 * На старом коде (до правки пакета) сторы импортировали `../../lib/utils` БЕЗ
 * расширения — такой импорт не резолвится в Node ESM, и обычный статический import
 * уронил бы весь файл с «module not found». Динамический импорт обёрнут в catch,
 * поэтому на старом коде тест падает с AssertionError (см. `assert.ok` в createCtx),
 * а не с ошибкой загрузки модуля.
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} default-экспорт модуля либо null.
 */
const importStore = async (specifier) => {
  try {
    const mod = await import(specifier)
    return mod.default || mod
  } catch (_e) {
    return null
  }
}

/**
 * Ограничивает ожидание промиса.
 *
 * На СТАРОМ коде некоторые экшены не возвращают промис (или возвращают промис,
 * который никогда не резолвится: внутри него остаётся неотвеченный запрос). Без
 * ограничения тест зависал бы вместо того, чтобы упасть с AssertionError.
 *
 * @param {Promise<any>} promise — ожидаемый промис.
 * @param {number} ms — предел ожидания, мс.
 * @returns {Promise<any>} результат промиса либо отказ по таймауту.
 */
const withTimeout = (promise, ms = 2000) =>
  Promise.race([
    promise,
    new Promise((_resolve, reject) =>
      setTimeout(() => reject(new Error(`таймаут ожидания промиса (${ms} мс)`)), ms),
    ),
  ])

const songEditor = await importStore('../store.js')
const properties = await importStore('../../Properties/store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDITOR_STORE = stripComments(
  readFileSync(new URL('../store.js', import.meta.url), 'utf-8'),
)
const SONG_EDITOR_TABLE = stripComments(
  readFileSync(new URL('../SongEditorTable.vue', import.meta.url), 'utf-8'),
)
const ASSIGN_MODAL = stripComments(
  readFileSync(new URL('../AssignModal.vue', import.meta.url), 'utf-8'),
)
const REVIEW_MODAL = stripComments(
  readFileSync(new URL('../ReviewModal.vue', import.meta.url), 'utf-8'),
)
const KARAOKE_EDITOR_MODAL = stripComments(
  readFileSync(new URL('../SongKaraokeEditorModal.vue', import.meta.url), 'utf-8'),
)
const KARAOKE_EDITOR_VIEW = stripComments(
  readFileSync(new URL('../SongKaraokeEditorView.vue', import.meta.url), 'utf-8'),
)
const REVIEW_MODAL_FORMAT = stripComments(
  readFileSync(new URL('../useReviewModalFormat.js', import.meta.url), 'utf-8'),
)

/**
 * Минимальный Vuex-контекст: state/commit/dispatch, без зависимости от vuex.
 *
 * @param {Object} module — модуль стора.
 * @returns {{ctx: Object, state: Object}} контекст и state.
 */
function createCtx(module) {
  assert.ok(module, 'модуль стора должен быть подключаем (иначе тест не имеет смысла)')
  const state = structuredClone(module.state)
  const ctx = { state, getters: {} }
  ctx.commit = (type, payload) => {
    const mutation = module.mutations[type]
    assert.ok(mutation, `неизвестная мутация ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => {
    const action = module.actions[type]
    if (!action) return Promise.resolve(undefined)
    return action(ctx, payload)
  }
  return { ctx, state }
}

/**
 * Контекст SongEditor + action `getPropertyValuePromise` из стора настроек
 * (он живёт в другом модуле).
 *
 * @param {Object} module — модуль стора.
 * @returns {{ctx: Object, state: Object}} контекст и state.
 */
function createCtxWithProperties(module) {
  const { ctx, state } = createCtx(module)
  const originalDispatch = ctx.dispatch
  ctx.dispatch = (type, payload) => {
    if (type === 'getPropertyValuePromise') {
      return properties.actions.getPropertyValuePromise(ctx, payload)
    }
    return originalDispatch(type, payload)
  }
  return { ctx, state }
}

describe('PKG-S3-32 / Онлайн-редактор', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-418 — правка во время сохранения не теряется', () => {
    test('повторный вызов saveNow помечает необходимость ещё одного сохранения', () => {
      assert.match(
        KARAOKE_EDITOR_MODAL,
        /if \(this\.saving\)[\s\S]*this\.saveRequested = true/,
        'BR-418: вызов saveNow во время сохранения обязан пометить повторное сохранение',
      )
      assert.match(
        KARAOKE_EDITOR_MODAL,
        /finally\s*\{[\s\S]*if \(this\.saveRequested\)[\s\S]*this\.saveNow\(\)/,
        'BR-418: после запроса сохранение должно быть перезапущено',
      )
    })

    test('статус saved ставится только если новых правок не было', () => {
      assert.match(
        KARAOKE_EDITOR_MODAL,
        /const hasNewChanges = this\.changeCounter !== changesAtStart/,
        'BR-418: нужно знать, были ли правки после старта запроса',
      )
      assert.match(
        KARAOKE_EDITOR_MODAL,
        /this\.saveState = ok \? \(hasNewChanges \? 'idle' : 'saved'\) : 'error'/,
        "BR-418: 'saved' ставится только если после старта запроса правок не было",
      )
    })
  })

  describe('BR-419 — модалка ревью не ловушка', () => {
    test('результат загрузки различает «найдено» и «не найдено»', async () => {
      const { ctx, state } = createCtx(songEditor)
      const load = ctx.dispatch('loadAssignmentById', 5)
      FakeXhr.last((request) => request.url === '/api/songeditor/byId').respond(200, 'null')
      const result = await withTimeout(load)
      assert.equal(result.found, false, 'BR-419: отсутствующее задание должно быть распознано')
      assert.equal(state.assignmentCurrent, null)
    })

    test('таблица не открывает модалку и показывает сообщение', () => {
      assert.match(
        SONG_EDITOR_TABLE,
        /openReview\(id\)[\s\S]*if \(!result \|\| !result\.found\)[\s\S]*reviewError/,
        'BR-419: при отсутствии задания модалка не открывается, показывается сообщение',
      )
      assert.match(SONG_EDITOR_TABLE, /catch\s*\([\s\S]*reviewError/, 'BR-419: отказ тоже виден')
    })

    test('кнопка «Закрыть» доступна и без загруженного задания', () => {
      const closeIndex = REVIEW_MODAL.indexOf("$emit('close')")
      assert.ok(closeIndex > 0, 'BR-419: кнопка «Закрыть» не найдена')
      assert.match(
        REVIEW_MODAL,
        /v-if="!a"[\s\S]*\$emit\('close'\)/,
        'BR-419: кнопка «Закрыть» должна быть доступна вне `v-if="a"`',
      )
    })
  })

  describe('BR-421 — цикл назначения останавливается при закрытии', () => {
    test('закрытие запрещено, пока идёт назначение', () => {
      assert.match(
        ASSIGN_MODAL,
        /closeModal\(\)\s*\{[\s\S]*if \(this\.busy\)[\s\S]*return/,
        'BR-421: закрытие во время назначения должно быть запрещено',
      )
    })

    test('цикл прерывается при размонтировании', () => {
      assert.match(
        ASSIGN_MODAL,
        /for \(const s of toAssign\)[\s\S]*if \(this\.unmounted\) break/,
        'BR-421: цикл назначения должен прерываться при размонтировании',
      )
      assert.match(
        ASSIGN_MODAL,
        /beforeUnmount\(\)[\s\S]*this\.unmounted = true[\s\S]*notifyAssigned\(\)/,
        'BR-421: при размонтировании родитель должен быть уведомлён о назначенных песнях',
      )
    })
  })

  describe('BR-422 — сбой поиска не выглядит как пустой результат', () => {
    test('есть отдельное состояние ошибки поиска', () => {
      assert.match(ASSIGN_MODAL, /searchError/, 'BR-422: нужен отдельный текст ошибки поиска')
      assert.match(
        ASSIGN_MODAL,
        /catch\s*\(e\)[\s\S]*this\.searchError = `Не удалось выполнить поиск/,
        'BR-422: сбой поиска должен давать понятный текст',
      )
      assert.match(
        ASSIGN_MODAL,
        /v-else-if="searchError"/,
        'BR-422: ошибка должна показываться вместо «Ничего не найдено»',
      )
    })
  })

  describe('BR-423 / BR-433 — загрузка дайджеста заданий', () => {
    test('у загрузки есть поколение запроса', () => {
      assert.match(
        SONG_EDITOR_STORE,
        /assignmentsDigestGeneration/,
        'BR-423: у загрузки дайджеста должно быть поколение запроса',
      )
      assert.match(
        SONG_EDITOR_STORE,
        /loadListWith\(/,
        'BR-423: загрузка должна идти через общий помощник (поколение + finally + ошибка)',
      )
    })

    test('ошибка загрузки попадает в state и показывается в таблице', async () => {
      const { ctx, state } = createCtx(songEditor)
      const load = ctx.dispatch('loadAssignmentsDigest', {})
      FakeXhr.last((request) => request.url === '/api/songeditor/digest').respond(500, '')
      await withTimeout(load)
      assert.ok(state.assignmentsDigestError, 'BR-433: ошибка должна быть в state')
      assert.equal(state.assignmentsIsLoading, false)
      assert.match(SONG_EDITOR_TABLE, /getAssignmentsDigestError/, 'BR-433: ошибка видна в таблице')
    })

    test('устаревший ответ не применяется', async () => {
      const { ctx, state } = createCtx(songEditor)
      const first = ctx.dispatch('loadAssignmentsDigest', { filterStatus: 'assigned' })
      const firstRequest = FakeXhr.last((request) => request.url === '/api/songeditor/digest')
      const second = ctx.dispatch('loadAssignmentsDigest', { filterStatus: 'approved' })
      const secondRequest = FakeXhr.last((request) => request.url === '/api/songeditor/digest')

      secondRequest.respond(200, { songAssignmentsDigest: [{ id: 2 }] })
      await withTimeout(second)
      firstRequest.respond(200, { songAssignmentsDigest: [{ id: 1 }] })
      await withTimeout(first)

      assert.deepEqual(
        state.assignmentsDigest.map((a) => a.id),
        [2],
        'BR-423: применяется ответ последнего запроса',
      )
    })
  })

  describe('BR-425 — код ошибки загрузки разбирается', () => {
    test('модалка использует общий разбор кодов ошибок', () => {
      assert.match(
        KARAOKE_EDITOR_MODAL,
        /describeSongEditorLoadError/,
        'BR-425: модалка должна разбирать код ошибки',
      )
      assert.doesNotMatch(
        KARAOKE_EDITOR_MODAL,
        /this\.loadError = 'Не удалось загрузить данные для редактирования\.'/,
        'BR-425: общий текст не должен быть единственным',
      )
    })
  })

  describe('BR-427 — defaultTarget читается из значения свойства', () => {
    test('читается prop.value, а не сам DTO', async () => {
      const { ctx, state } = createCtxWithProperties(songEditor)
      const load = ctx.dispatch('loadEditorDefaultTarget')
      FakeXhr.last((request) => request.url === '/api/properties/getproperty').respond(
        200,
        JSON.stringify({ property: { key: 'editorAssignmentDefaultTarget', value: 'local' } }),
      )
      await withTimeout(load)
      assert.equal(state.defaultTarget, 'local', 'BR-427: значение свойства обязано читаться')
      assert.equal(state.assignmentsTarget, 'local')
    })

    test('пустое значение даёт remote', async () => {
      const { ctx, state } = createCtxWithProperties(songEditor)
      const load = ctx.dispatch('loadEditorDefaultTarget')
      FakeXhr.last((request) => request.url === '/api/properties/getproperty').respond(
        200,
        JSON.stringify({ property: { key: 'editorAssignmentDefaultTarget', value: '' } }),
      )
      await withTimeout(load)
      assert.equal(state.defaultTarget, 'remote', 'BR-427: при отсутствии значения — remote')
    })
  })

  describe('BR-428 — настройки редактора не затираются', () => {
    test('локальная копия знает полный набор ключей', () => {
      for (const key of [
        'textFontSize',
        'previewFontSize',
        'volume',
        'playbackRate',
        'zoom',
        'activeSound',
        'showKeyboard',
      ]) {
        assert.match(
          REVIEW_MODAL_FORMAT,
          new RegExp(`${key}:`),
          `BR-428: в настройках редактора должен быть ключ ${key}`,
        )
      }
    })

    test('запись мержит partial в полный сохранённый объект', () => {
      assert.match(
        REVIEW_MODAL_FORMAT,
        /saveEditorSettings\(partial\)[\s\S]*\{ \.\.\.stored, \.\.\.partial \}/,
        'BR-428: partial должен мержиться в полный сохранённый объект',
      )
    })
  })

  describe('BR-430 — потеря фокуса останавливает удержание клавиш', () => {
    test('есть подписка на blur/visibilitychange', () => {
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /addEventListener\('blur', this\.onWindowBlur\)/,
        'BR-430: нужна подписка на потерю фокуса окна',
      )
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /addEventListener\('visibilitychange', this\.onVisibilityChange\)/,
        'BR-430: нужна подписка на уход вкладки в фон',
      )
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /releaseAllHeldKeys\(\)[\s\S]*stopScrub\(code\)/,
        'BR-430: все таймеры удержания должны останавливаться',
      )
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /releaseAllHeldKeys\(\)[\s\S]*setPlaybackRate\(this\.playbackRate\)/,
        'BR-430: playbackRate должен восстанавливаться',
      )
    })
  })

  describe('BR-431 — скрытие плеера уничтожает инстанс', () => {
    test('togglePlayer уничтожает плеер при скрытии', () => {
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /if \(this\.showPlayer\)[\s\S]*this\.destroyPlayer\(\)/,
        'BR-431: при скрытии плеер должен уничтожаться',
      )
      assert.match(
        KARAOKE_EDITOR_VIEW,
        /destroyPlayer\(\)[\s\S]*this\.kpPlayer\.destroy\(\)[\s\S]*this\.kpPlayer = null/,
        'BR-431: destroy() должен вызываться, инстанс — обнуляться',
      )
    })
  })
})
