// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — пользователи сайта.
//
// Записи пакета в этом файле:
//   BR-415 — модалка «Плейлисты пользователя» рендерила общий дайджест: под именем
//            текущего пользователя оказывались данные другого.
//   BR-420 — исход сохранения/бана/удаления не проверялся: отказ уходил в unhandled
//            rejection, а диалог/панель закрывались до подтверждённого успеха.
//   BR-424 — фильтр писался на сервер на КАЖДОЕ нажатие клавиши, а гидратация шла
//            последовательными запросами и снова записывала те же значения.
//
// Запуск: cd webvue3 && node --test src/components/SiteUsers/__tests__/pkg-s3-32-siteusers.test.js

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

const siteUsers = await importStore('../store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const USER_PLAYLISTS_MODAL = stripComments(
  readFileSync(new URL('../UserPlaylistsModal.vue', import.meta.url), 'utf-8'),
)
const SITE_USER_EDIT = stripComments(
  readFileSync(new URL('../edit/SiteUserEdit.vue', import.meta.url), 'utf-8'),
)
const SITE_USERS_FILTER_STORE = stripComments(
  readFileSync(new URL('../filter/store.js', import.meta.url), 'utf-8'),
)
const SITE_USERS_FILTER_MODAL = stripComments(
  readFileSync(new URL('../filter/SiteUsersFilterModal.vue', import.meta.url), 'utf-8'),
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

describe('PKG-S3-32 / Пользователи сайта', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-415 — модалка показывает плейлисты своего пользователя', () => {
    test('модалка фильтрует дайджест по siteUserId', () => {
      assert.match(
        USER_PLAYLISTS_MODAL,
        /getSitePlaylistsDigestOwnerId\s*!==\s*this\.siteUserId/,
        'BR-415: данные чужого пользователя не должны показываться',
      )
    })

    test('модалка показывает состояние ошибки', () => {
      assert.match(
        USER_PLAYLISTS_MODAL,
        /getSitePlaylistsDigestError/,
        'BR-415: ошибка загрузки должна быть видна',
      )
      assert.match(USER_PLAYLISTS_MODAL, /upm-error/, 'BR-415: нужен блок ошибки в шаблоне')
    })
  })

  describe('BR-420 — исход операции проверяется', () => {
    test('update с id=0 отклоняется', async () => {
      const { ctx, state } = createCtx(siteUsers)
      state.siteUserCurrentId = 7
      const save = ctx.dispatch('saveSiteUser', { displayName: 'X' })
      FakeXhr.last((request) => request.url === '/api/siteusers/update').respond(200, '0')
      await assert.rejects(withTimeout(save), /не найден/i, 'BR-420: id=0 — отказ, а не успех')
    })

    test('ban с false отклоняется', async () => {
      const { ctx, state } = createCtx(siteUsers)
      state.siteUserCurrentId = 7
      const ban = ctx.dispatch('banSiteUserCurrent', 'причина')
      FakeXhr.last((request) => request.url === '/api/siteusers/ban').respond(200, 'false')
      await assert.rejects(withTimeout(ban), /не найден/i, 'BR-420: false — отказ')
    })

    test('unban с false отклоняется', async () => {
      const { ctx, state } = createCtx(siteUsers)
      state.siteUserCurrentId = 7
      const unban = ctx.dispatch('unbanSiteUserCurrent')
      FakeXhr.last((request) => request.url === '/api/siteusers/unban').respond(200, 'false')
      await assert.rejects(withTimeout(unban), /не найден/i, 'BR-420: false — отказ')
    })

    test('delete с false отклоняется', async () => {
      const { ctx, state } = createCtx(siteUsers)
      state.siteUserCurrentId = 7
      const remove = ctx.dispatch('deleteSiteUserCurrent')
      FakeXhr.last((request) => request.url === '/api/siteusers/delete').respond(200, 'false')
      await assert.rejects(withTimeout(remove), /не удалён/i, 'BR-420: false — отказ')
    })

    test('форма показывает ошибку и не закрывается до успеха', () => {
      assert.match(SITE_USER_EDIT, /actionError/, 'BR-420: ошибка должна быть видна в форме')
      assert.match(
        SITE_USER_EDIT,
        /async doDeleteUser\(\)[\s\S]*catch\s*\([\s\S]*return[\s\S]*\$emit\('close'\)/,
        'BR-420: удаление закрывает панель только после подтверждённого успеха',
      )
      assert.match(
        SITE_USER_EDIT,
        /async doBan\(item\)[\s\S]*catch\s*\(/,
        'BR-420: бан должен ловить отказ',
      )
    })
  })

  describe('BR-424 — фильтр сохраняется один раз, гидратация параллельна', () => {
    test('мутации фильтра не пишут на сервер', () => {
      const mutations = SITE_USERS_FILTER_STORE.slice(
        SITE_USERS_FILTER_STORE.indexOf('  mutations: {'),
        SITE_USERS_FILTER_STORE.indexOf('  actions: {'),
      )
      assert.doesNotMatch(
        mutations,
        /setWebvueProp/,
        'BR-424: мутации фильтра не должны писать на сервер (запись — по кнопке «Применить»)',
      )
    })

    test('есть action записи и чтения без записи', () => {
      assert.match(
        SITE_USERS_FILTER_STORE,
        /async persistSiteUsersFilter\(ctx\)/,
        'BR-424: нужен action записи фильтра',
      )
      assert.match(
        SITE_USERS_FILTER_STORE,
        /async restoreSiteUsersFilter\(ctx\)[\s\S]*Promise\.allSettled/,
        'BR-424: гидратация должна быть параллельной (Promise.allSettled), а не waterfall',
      )
    })

    test('модалка сохраняет фильтр по кнопке «Применить»', () => {
      assert.match(
        SITE_USERS_FILTER_MODAL,
        /dispatch\('persistSiteUsersFilter'\)/,
        'BR-424: запись фильтра — по кнопке «Применить»',
      )
    })
  })
})
