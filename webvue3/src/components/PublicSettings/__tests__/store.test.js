// Юнит-тесты стора PublicSettings (KARAOKE-403) и контракт его таблицы.
//
// ЗАДАЧА. У стора публичных настроек (`webvue3/src/components/PublicSettings/store.js`)
// и компонента `PublicSettingsTable.vue` не было ни одного теста: каталога
// `__tests__` рядом не существовало. Стор грузит дайджест публичных настроек, ведёт
// признак загрузки, переключает цель (local/remote) и сохраняет значение.
//
// ЧТО ПОКРЫВАЕТСЯ.
//   1. Загрузка дайджеста кладёт значения в state и снимает флаг загрузки.
//   2. Отказ загрузки НЕ оставляет флаг включённым и кладёт текст ошибки в state
//      (BR-413 — та же правка, что в сторе Properties: иначе пустой список
//      выглядит как «настроек нет»).
//   3. Сохранение значения отправляет ключ и значение, а таблица обновляет
//      «оригинал» — чтобы UI не показывал несохранённое как сохранённое.
//
// ТРАНСПОРТ. Сеть не задействована: `XMLHttpRequest` подменяется общим шовом
// `webvue3/src/test-utils/fakeXhr.js` (тот же, что в тестах пакета PKG-S3-33).
//
// Запуск: cd webvue3 && node --test src/components/PublicSettings/__tests__/store.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const read = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf-8')

const PUBLIC_SETTINGS_TABLE = read('../PublicSettingsTable.vue')

/**
 * Подключает стор ДИНАМИЧЕСКИ.
 *
 * Если стор не импортируется (например, вернулось расширение-less импорт
 * `../../lib/utils`, который Node ESM не резолвит), тест падает с понятным
 * AssertionError, а не с ошибкой загрузки модуля.
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
 * Минимальный контекст Vuex для проверки действий стора.
 *
 * @param {Object} store — стор (state/mutations/actions).
 * @returns {Object} контекст с commit/dispatch.
 */
const createCtx = (store) => {
  const state = JSON.parse(JSON.stringify(store.state))
  const ctx = {
    state,
    commit: (name, payload) => {
      const mutation = store.mutations[name]
      assert.ok(mutation, `нет мутации ${name}`)
      mutation(state, payload)
    },
    dispatch: null,
  }
  ctx.dispatch = (name, payload) => {
    const action = store.actions[name]
    assert.ok(action, `нет действия ${name}`)
    return action(ctx, payload)
  }
  return ctx
}

const DIGEST = [
  { key: 'captcha_key', value: 'секретный ключ', description: 'Ключ капчи' },
  { key: 'flag_free', value: 'true', description: 'Флаг' },
]

describe('PublicSettings: загрузка дайджеста', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  test('значения дайджеста кладутся в state, флаг загрузки снимается', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать и грузиться в Node ESM')
    const ctx = createCtx(store)

    const promise = ctx.dispatch('loadPublicSettingsDigest')
    const xhr = FakeXhr.last((x) => x.url === '/api/publicsettings/digest')
    assert.ok(xhr, 'запрос дайджеста должен уйти')
    assert.equal(xhr.method, 'POST')

    xhr.respond(200, { publicSettingsDigest: DIGEST })
    await promise

    assert.deepEqual(ctx.state.publicSettingsDigest, DIGEST)
    assert.equal(ctx.state.publicSettingsDigestIsLoading, false)
    assert.equal(ctx.state.publicSettingsDigestError, '')
  })

  test('пока идёт запрос, флаг загрузки включён', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    const promise = ctx.dispatch('loadPublicSettingsDigest')
    assert.equal(ctx.state.publicSettingsDigestIsLoading, true)

    FakeXhr.last((x) => x.url === '/api/publicsettings/digest').respond(200, {
      publicSettingsDigest: DIGEST,
    })
    await promise
    assert.equal(ctx.state.publicSettingsDigestIsLoading, false)
  })

  test('цель запроса берётся из state и переключается действием', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    ctx.dispatch('setPublicSettingsTarget', 'remote')
    assert.equal(ctx.state.publicSettingsTarget, 'remote')

    const promise = ctx.dispatch('loadPublicSettingsDigest')
    const xhr = FakeXhr.last((x) => x.url === '/api/publicsettings/digest')
    assert.equal(xhr.params.target, 'remote', 'цель должна уходить в запрос')

    xhr.respond(200, { publicSettingsDigest: DIGEST })
    await promise
  })

  test('отказ загрузки снимает флаг и кладёт текст ошибки в state', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    const promise = ctx.dispatch('loadPublicSettingsDigest')
    FakeXhr.last((x) => x.url === '/api/publicsettings/digest').respond(500, 'boom')
    await promise
    await new Promise((resolve) => setTimeout(resolve, 20))

    assert.equal(
      ctx.state.publicSettingsDigestIsLoading,
      false,
      'отказ не должен оставлять флаг загрузки включённым',
    )
    assert.ok(
      ctx.state.publicSettingsDigestError,
      'после отказа в state должен быть текст ошибки — иначе пустой список выглядит как «настроек нет»',
    )
  })

  test('текст ошибки снимается следующей успешной загрузкой', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    const failed = ctx.dispatch('loadPublicSettingsDigest')
    FakeXhr.last((x) => x.url === '/api/publicsettings/digest').respond(500, 'boom')
    await failed
    await new Promise((resolve) => setTimeout(resolve, 20))
    assert.ok(
      ctx.state.publicSettingsDigestError,
      'до успешной загрузки ошибка должна быть в state',
    )

    const promise = ctx.dispatch('loadPublicSettingsDigest')
    assert.equal(ctx.state.publicSettingsDigestError, '', 'ошибка снимается в начале загрузки')
    FakeXhr.last((x) => x.url === '/api/publicsettings/digest').respond(200, {
      publicSettingsDigest: DIGEST,
    })
    await promise
    assert.equal(ctx.state.publicSettingsDigestError, '')
  })
})

describe('PublicSettings: сохранение значения', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  test('сохранение отправляет ключ, значение и цель', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    const promise = ctx.dispatch('savePublicSettingValue', {
      key: 'captcha_key',
      value: 'новый ключ',
    })
    const xhr = FakeXhr.last((x) => x.url === '/api/publicsettings/update')
    assert.ok(xhr, 'запрос сохранения должен уйти')
    assert.equal(xhr.method, 'POST')
    assert.equal(xhr.params.key, 'captcha_key')
    assert.equal(xhr.params.value, 'новый ключ')
    assert.equal(xhr.params.target, 'local')

    xhr.respond(200, 'ok')
    await promise
  })

  test('цель сохранения — та, что выбрана в сторе', async () => {
    const store = await importStore('../store.js')
    assert.ok(store, 'стор PublicSettings должен существовать')
    const ctx = createCtx(store)

    ctx.dispatch('setPublicSettingsTarget', 'remote')
    const promise = ctx.dispatch('savePublicSettingValue', { key: 'flag_free', value: 'false' })
    const xhr = FakeXhr.last((x) => x.url === '/api/publicsettings/update')
    assert.equal(xhr.params.target, 'remote')

    xhr.respond(200, 'ok')
    await promise
  })
})

describe('PublicSettings: контракт таблицы (падает на старом коде)', () => {
  test('после сохранения «оригинал» обновляется — несохранённое не выглядит сохранённым', () => {
    assert.match(
      PUBLIC_SETTINGS_TABLE,
      /await this\.\$store\.dispatch\('savePublicSettingValue'[^\n]*\n\s*this\.original\[item\.key\] = item\.value/,
      'сохранение должно ждать ответа и только потом обновлять оригинал',
    )
  })

  test('кнопка «Сохранить» отключена, пока значение не отличается от оригинала', () => {
    assert.match(
      PUBLIC_SETTINGS_TABLE,
      /:disabled="item\.value === original\[item\.key\]"/,
      'кнопка сохранения должна быть отключена для неизменённого значения',
    )
  })

  test('отказ загрузки показывается пользователю, а не выглядит как «Нет настроек»', () => {
    assert.match(
      PUBLIC_SETTINGS_TABLE,
      /getPublicSettingsDigestError/,
      'таблица должна брать текст ошибки из стора',
    )
    assert.match(PUBLIC_SETTINGS_TABLE, /class="pst-error"/, 'в таблице должен быть блок ошибки')
    assert.match(
      PUBLIC_SETTINGS_TABLE,
      /v-else-if="publicSettingsDigestError"[\s\S]{0,200}v-else-if="editable\.length === 0"/,
      'ошибка должна проверяться раньше «Нет настроек» — иначе отказ выглядит как пустой список',
    )
  })
})
