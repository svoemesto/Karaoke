// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — плейлисты сайта.
//
// Записи пакета в этом файле:
//   BR-414 — ответ `byId` коммитился в общий `sitePlaylistDetail` без сверки с
//            последним запрошенным id; при отказе модалка навсегда показывала
//            «Загрузка...».
//   BR-415 — дайджест плейлистов общий для таблицы и модалки: данные другого
//            пользователя показывались под именем текущего.
//
// Запуск: cd webvue3 && node --test src/components/SitePlaylists/__tests__/pkg-s3-32-siteplaylists.test.js

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

const sitePlaylists = await importStore('../store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const DETAIL_MODAL = stripComments(
  readFileSync(new URL('../SitePlaylistDetailModal.vue', import.meta.url), 'utf-8'),
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

describe('PKG-S3-32 / Плейлисты сайта', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-414 — detail плейлиста принадлежит последнему запросу', () => {
    test('ответ для чужого id не применяется', async () => {
      const { ctx, state } = createCtx(sitePlaylists)
      const load = ctx.dispatch('loadSitePlaylistDetail', 42)
      // Пока запрос в полёте, открыли другой плейлист.
      state.sitePlaylistDetailId = 43
      FakeXhr.last((request) => request.url === '/api/siteplaylists/byId').respond(200, {
        id: 42,
        name: 'чужой',
      })
      await withTimeout(load)
      assert.equal(state.sitePlaylistDetail, undefined, 'BR-414: чужой ответ не применяется')
    })

    test('свой ответ применяется, id запоминается', async () => {
      const { ctx, state } = createCtx(sitePlaylists)
      const load = ctx.dispatch('loadSitePlaylistDetail', 42)
      assert.equal(state.sitePlaylistDetailId, 42, 'BR-414: id должен запоминаться')
      FakeXhr.last((request) => request.url === '/api/siteplaylists/byId').respond(200, {
        id: 42,
        name: 'мой',
      })
      await withTimeout(load)
      assert.equal(state.sitePlaylistDetail.name, 'мой')
      assert.equal(state.sitePlaylistDetailIsLoading, false)
    })

    test('отказ даёт явную ошибку, а не вечное «Загрузка...»', async () => {
      const { ctx, state } = createCtx(sitePlaylists)
      const load = ctx.dispatch('loadSitePlaylistDetail', 42)
      FakeXhr.last((request) => request.url === '/api/siteplaylists/byId').respond(500, '')
      await assert.rejects(withTimeout(load))
      assert.ok(state.sitePlaylistDetailError, 'BR-414: ошибка должна быть в state')
      assert.equal(state.sitePlaylistDetailIsLoading, false)
    })

    test('модалка показывает состояние загрузки и ошибки', () => {
      assert.match(
        DETAIL_MODAL,
        /getSitePlaylistDetailIsLoading/,
        'BR-414: модалка должна знать про состояние загрузки',
      )
      assert.match(
        DETAIL_MODAL,
        /getSitePlaylistDetailError/,
        'BR-414: модалка должна показывать ошибку',
      )
    })
  })

  describe('BR-415 — дайджест плейлистов привязан к пользователю', () => {
    test('владелец дайджеста фиксируется при загрузке', async () => {
      const { ctx, state } = createCtx(sitePlaylists)
      const load = ctx.dispatch('loadSitePlaylistsDigest', { filterOwnerId: 55 })
      assert.equal(
        state.sitePlaylistsDigestOwnerId,
        55,
        'BR-415: владелец данных фиксируется ДО запроса',
      )
      FakeXhr.last((request) => request.url === '/api/siteplaylists/digest').respond(200, {
        sitePlaylistsDigest: [{ id: 1 }],
      })
      await withTimeout(load)
      assert.equal(state.sitePlaylistsDigest.length, 1)
      assert.equal(state.sitePlaylistsDigestError, '')
    })

    test('ошибка загрузки попадает в state', async () => {
      const { ctx, state } = createCtx(sitePlaylists)
      const load = ctx.dispatch('loadSitePlaylistsDigest', { filterOwnerId: 55 })
      FakeXhr.last((request) => request.url === '/api/siteplaylists/digest').respond(500, '')
      await withTimeout(load)
      assert.ok(state.sitePlaylistsDigestError, 'BR-415: ошибка должна быть видна')
      assert.equal(state.sitePlaylistsDigestIsLoading, false)
    })
  })
})
