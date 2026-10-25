// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — настройки.
//
// Записи пакета в этом файле:
//   BR-404 — изменение значения настройки при ошибке терялось молча (диалог закрылся,
//            ошибка ушла только в консоль), значение не проверялось по типу.
//   BR-405 — дайджест настроек не грузился при открытии раздела (таблица была пуста).
//   BR-413 — при ошибке загрузки дайджеста флаг загрузки не сбрасывался, промис не
//            возвращался.
//
// Запуск: cd webvue3 && node --test src/components/Properties/__tests__/pkg-s3-32-properties.test.js

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

const properties = await importStore('../store.js')

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const PROPERTIES_STORE = stripComments(
  readFileSync(new URL('../store.js', import.meta.url), 'utf-8'),
)
const PROPERTIES_TABLE = stripComments(
  readFileSync(new URL('../PropertiesTable.vue', import.meta.url), 'utf-8'),
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

describe('PKG-S3-32 / Настройки', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-413 — загрузка дайджеста не залипает', () => {
    test('экшен возвращает промис и снимает флаг при ошибке', async () => {
      const { ctx, state } = createCtx(properties)
      const load = ctx.dispatch('loadPropertiesDigests', {})
      FakeXhr.last((request) => request.url === '/api/propertiesdigests').respond(500, '')
      await withTimeout(load)

      assert.equal(
        state.propertiesDigestIsLoading,
        false,
        'BR-413: флаг загрузки обязан сниматься при ошибке',
      )
      assert.ok(
        state.propertiesDigestsError,
        'BR-413: текст ошибки должен попасть в state (пустой список ≠ «настроек нет»)',
      )
    })

    test('успешный ответ применяется, текст ошибки пуст', async () => {
      const { ctx, state } = createCtx(properties)
      const load = ctx.dispatch('loadPropertiesDigests', {})
      FakeXhr.last((request) => request.url === '/api/propertiesdigests').respond(200, {
        propertiesDigests: [{ key: 'a', value: '1' }],
      })
      await withTimeout(load)

      assert.equal(state.propertiesDigest.length, 1)
      assert.equal(state.propertiesDigestsError, '')
      assert.equal(state.propertiesDigestIsLoading, false)
    })
  })

  describe('BR-405 — дайджест грузится при открытии раздела', () => {
    test('mounted вызывает loadPropertiesDigests', () => {
      assert.match(
        PROPERTIES_TABLE,
        /mounted\s*\(\)[\s\S]*dispatch\('loadPropertiesDigests'/,
        'BR-405: раздел «Настройки» обязан загружать дайджест при открытии',
      )
      assert.doesNotMatch(
        PROPERTIES_TABLE,
        /\/\/ this\.\$store\.dispatch\('loadPropertiesDigests'/,
        'BR-405: вызов загрузки остался закомментированным',
      )
    })

    test('в таблице есть место для сообщения об ошибке загрузки', () => {
      assert.match(
        PROPERTIES_TABLE,
        /getPropertiesDigestsError/,
        'BR-405: ошибка загрузки должна быть видна в таблице',
      )
    })
  })

  describe('BR-404 — изменение значения настройки', () => {
    test('значение проверяется по типу до отправки', () => {
      assert.match(
        PROPERTIES_TABLE,
        /validatePropertyValue/,
        'BR-404: значение должно проверяться по типу настройки',
      )
      assert.match(
        PROPERTIES_TABLE,
        /propertyError/,
        'BR-404: ошибка изменения значения должна быть видна в таблице',
      )
      assert.match(
        PROPERTIES_TABLE,
        /import \{ validatePropertyValue \} from '\.\.\/\.\.\/lib\/property-value\.js'/,
        'BR-404: проверка значения должна быть в общем модуле',
      )
    })

    test('отказ запроса показывается пользователю', () => {
      assert.match(
        PROPERTIES_TABLE,
        /doChangeValue\s*\([^)]*\)\s*\{[\s\S]*catch\s*\(/,
        'BR-404: отказ изменения значения должен обрабатываться',
      )
      assert.doesNotMatch(
        PROPERTIES_TABLE,
        /console\.error\('Ошибка при выполнении setPropertyValuePromise:'/,
        'BR-404: ошибка не должна уходить только в консоль',
      )
    })

    test('диалог переоткрывается с введённым значением', () => {
      assert.match(
        PROPERTIES_TABLE,
        /this\.\$nextTick\(\(\)\s*=>\s*\{\s*this\.isCustomConfirmVisible = true/,
        'BR-404: при невалидном значении диалог переоткрывается',
      )
    })
  })
})
