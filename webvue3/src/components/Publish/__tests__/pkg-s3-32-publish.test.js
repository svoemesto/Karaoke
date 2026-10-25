// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — публикации.
//
// Запись пакета в этом файле:
//   BR-412 — тихая SSE-перезагрузка запрашивала только диапазон дат и нигде не хранила
//            активный `filterCond`: на видах UNPUBLISH / UNDONE / SKIPED фоновое событие
//            подменяло cond-выборку обычной сеткой публикаций.
//
// Запуск: cd webvue3 && node --test src/components/Publish/__tests__/pkg-s3-32-publish.test.js

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

const publish = await importStore('../store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const PUBLISH_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))

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
  // Геттеры модуля — живые (читают state в момент обращения).
  for (const [name, getter] of Object.entries(module.getters || {})) {
    Object.defineProperty(ctx.getters, name, {
      get: () => getter(state, ctx.getters, state, ctx.getters),
      enumerable: true,
    })
  }
  // Геттеры фильтра публикаций живут в другом модуле — подменяем нужные значения.
  ctx.getters.getPublishFilterDateFrom = '01.01.26'
  ctx.getters.getPublishFilterDateTo = '01.02.26'
  return { ctx, state }
}

describe('PKG-S3-32 / Публикации', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-412 — cond-вид сохраняется при фоновой перезагрузке', () => {
    test('активный filterCond запоминается вместе с дайджестом', async () => {
      const { ctx, state } = createCtx(publish)
      const load = ctx.dispatch('loadPublishDigest', { filterCond: 'unpublish' })
      FakeXhr.last((request) => request.url === '/api/publicationsdigest').respond(200, {
        publicationsDigest: [],
      })
      await withTimeout(load)
      assert.equal(state.publishFilterCond, 'unpublish', 'BR-412: cond-вид должен запоминаться')
    })

    test('тихая перезагрузка запрашивает тот же cond-вид', async () => {
      const { ctx, state } = createCtx(publish)
      state.publishFilterCond = 'unpublish'
      ctx.dispatch('reloadPublishDigestSilently')
      const request = FakeXhr.last((r) => r.url === '/api/publicationsdigest')
      assert.ok(request, 'BR-412: тихая перезагрузка должна отправлять запрос')
      assert.equal(
        request.params.filterCond,
        'unpublish',
        'BR-412: тихая перезагрузка обязана повторять cond-вид текущей выборки',
      )
    })

    test('без cond-вида параметр не передаётся', () => {
      assert.match(
        PUBLISH_STORE,
        /if \(filterCond\) params\.filterCond = filterCond/,
        'BR-412: cond-вид добавляется в запрос только когда он активен',
      )
    })
  })
})
