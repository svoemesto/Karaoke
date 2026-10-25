// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — картинки.
//
// Записи пакета в этом файле:
//   BR-395 — автосейв: снимок брался из ЖИВОГО `pictureCurrent`, а не из отправленного
//            payload; таймер/запрос не отменялись при размонтировании.
//   BR-396 — HTTP-запросы жили в мутациях; удаление/сохранение не ждали ответа, ошибки
//            не показывались, дайджест не обновлялся.
//
// ДО ПРАВКИ (BR-395): `savePicture` коммитил `savePicture` (мутацию), которая копировала
// в снимок ТЕКУЩИЙ `pictureCurrent`. Правки, сделанные пока запрос в полёте, попадали в
// снимок как «сохранённые»: diff обнулялся, повторный автосейв не срабатывал — изменения
// молча терялись при закрытии модалки.
//
// ДО ПРАВКИ (BR-396): `commit('deletePictureCurrent')` делал HTTP-запрос ВНУТРИ мутации,
// промис никто не ждал и не ловил; модалка закрывалась сразу, дайджест не перезагружался,
// `pictureCurrent` не очищался. Загрузка картинки в `mounted` шла `.then` без `.catch`.
//
// Запуск: cd webvue3 && node --test src/components/Pictures/__tests__/pkg-s3-32-pictures.test.js

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

const pictures = await importStore('../store.js')

const PICTURES_STORE = readFileSync(new URL('../store.js', import.meta.url), 'utf-8')
const PICTURE_EDIT = readFileSync(new URL('../edit/PictureEdit.vue', import.meta.url), 'utf-8')

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

describe('PKG-S3-32 / Картинки', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-395 — снимок автосейва берётся из отправленного payload', () => {
    test('правка во время запроса не помечается как сохранённая', async () => {
      const { ctx, state } = createCtx(pictures)
      state.pictureCurrent = { id: 1, name: 'исходное', full: 'F1' }
      state.pictureSnapshot = { id: 1, name: 'исходное', full: 'F1' }
      state.pictureCurrentId = 1

      const save = ctx.dispatch('savePicture', { diffs: { name: 'новое' } })
      // Пока запрос в полёте, админ печатает дальше.
      state.pictureCurrent.name = 'ещё новее'
      FakeXhr.last((request) => request.url === '/api/pictures/updatepicture').respond(200, '')
      await withTimeout(save)

      assert.equal(
        state.pictureSnapshot.name,
        'новое',
        'BR-395: в снимок попадает ОТПРАВЛЕННОЕ значение, а не живое',
      )
      assert.equal(
        state.pictureCurrent.name,
        'ещё новее',
        'BR-395: живое значение картинки не должно меняться снимком',
      )
    })

    test('в снимок не попадают поля, которые не ушли на сервер', async () => {
      const { ctx, state } = createCtx(pictures)
      state.pictureCurrent = { id: 1, name: 'исходное', full: 'F1' }
      state.pictureSnapshot = { id: 1, name: 'исходное', full: 'F1' }
      state.pictureCurrentId = 1

      const save = ctx.dispatch('savePicture', { diffs: { name: 'новое' } })
      FakeXhr.last((request) => request.url === '/api/pictures/updatepicture').respond(200, '')
      await withTimeout(save)

      assert.equal(state.pictureSnapshot.full, 'F1', 'BR-395: `full` не отправлялся — не сохранён')
    })

    test('поздний ответ устаревшего запроса не трогает снимок', async () => {
      const { ctx, state } = createCtx(pictures)
      state.pictureCurrent = { id: 1, name: 'A' }
      state.pictureSnapshot = { id: 1, name: 'A' }
      state.pictureCurrentId = 1

      const first = ctx.dispatch('savePicture', { diffs: { name: 'A' } })
      const firstRequest = FakeXhr.last((request) => request.url === '/api/pictures/updatepicture')
      const second = ctx.dispatch('savePicture', { diffs: { name: 'B' } })
      const secondRequest = FakeXhr.last((request) => request.url === '/api/pictures/updatepicture')

      // Ответ ПЕРВОГО запроса приходит последним — «побеждает последний запрос».
      secondRequest.respond(200, '')
      await withTimeout(second)
      firstRequest.respond(200, '')
      await withTimeout(first)

      assert.equal(state.pictureSnapshot.name, 'B', 'BR-395: снимок принадлежит последнему запросу')
    })

    test('PictureEdit гасит таймер и отменяет запрос в beforeUnmount', () => {
      assert.match(
        PICTURE_EDIT,
        /beforeUnmount\s*\(\)\s*\{[\s\S]*clearTimeout/,
        'BR-395: таймер автосейва должен гаситься при размонтировании',
      )
      assert.match(
        PICTURE_EDIT,
        /beforeUnmount\s*\(\)\s*\{[\s\S]*abort\(/,
        'BR-395: запрос в полёте должен отменяться при размонтировании',
      )
    })
  })

  describe('BR-396 — операции ждут ответа и показывают ошибку', () => {
    test('в мутациях не осталось сетевых запросов', () => {
      const mutations = PICTURES_STORE.slice(
        PICTURES_STORE.indexOf('  mutations: {'),
        PICTURES_STORE.indexOf('  actions: {'),
      )
      assert.doesNotMatch(
        mutations,
        /promisedXMLHttpRequest/,
        'BR-396: в мутациях не должно быть сетевых запросов',
      )
      assert.doesNotMatch(
        mutations,
        /async\s+deletePictureCurrent|async\s+savePictureCurrentToDisk/,
        'BR-396: сетевые операции должны быть action’ами, а не мутациями',
      )
    })

    test('удаление убирает строку из дайджеста и чистит текущую картинку', async () => {
      const { ctx, state } = createCtx(pictures)
      state.pictureCurrentId = 7
      state.pictureCurrent = { id: 7, name: 'картинка' }
      state.picturesDigest = [
        { id: 7, name: 'картинка' },
        { id: 8, name: 'другая' },
      ]

      const remove = ctx.dispatch('deletePictureCurrent')
      FakeXhr.last((request) => request.url === '/api/picture/delete').respond(200, '')
      await withTimeout(remove)

      assert.equal(state.picturesDigest.length, 1, 'BR-396: удалённая строка убирается из таблицы')
      assert.equal(state.picturesDigest[0].id, 8)
      assert.equal(state.pictureCurrent, undefined, 'BR-396: текущая картинка очищается')
    })

    test('отказ удаления не чистит картинку и не роняет промис', async () => {
      const { ctx, state } = createCtx(pictures)
      state.pictureCurrentId = 7
      state.pictureCurrent = { id: 7, name: 'картинка' }
      state.picturesDigest = [{ id: 7, name: 'картинка' }]

      const remove = ctx.dispatch('deletePictureCurrent')
      FakeXhr.last((request) => request.url === '/api/picture/delete').respond(500, '')
      await assert.rejects(withTimeout(remove))

      assert.equal(state.pictureCurrent.id, 7, 'BR-396: при отказе картинка остаётся')
      assert.equal(state.picturesDigest.length, 1, 'BR-396: при отказе дайджест не меняется')
    })

    test('PictureEdit: удаление ждёт ответа и закрывается только после успеха', () => {
      assert.match(
        PICTURE_EDIT,
        /await this\.\$store\.dispatch\('deletePictureCurrent'\)/,
        'BR-396: удаление должно ждать промис экшена',
      )
      assert.doesNotMatch(
        PICTURE_EDIT,
        /commit\('deletePictureCurrent'\)/,
        'BR-396: удаление не должно идти через commit мутации',
      )
      assert.match(
        PICTURE_EDIT,
        /catch\s*\(\s*error\s*\)\s*\{[\s\S]*showErrorToast/,
        'BR-396: отказ удаления должен показываться пользователю',
      )
    })

    test('PictureEdit: загрузка картинки обрабатывает отказ', () => {
      assert.match(
        PICTURE_EDIT,
        /loadError/,
        'BR-396: у модалки должно быть состояние ошибки загрузки',
      )
      assert.match(
        PICTURE_EDIT,
        /mounted\s*\(\)[\s\S]*catch\s*\(/,
        'BR-396: загрузка картинки обязана ловить отказ (без unhandled rejection)',
      )
    })

    test('PictureEdit: сохранение на диске ждёт ответа', () => {
      assert.doesNotMatch(
        PICTURE_EDIT,
        /commit\('savePictureCurrentToDisk'\)/,
        'BR-396: сохранение на диске не должно идти через commit мутации',
      )
      assert.match(
        PICTURE_EDIT,
        /await this\.\$store\.dispatch\('savePictureCurrentToDisk'\)/,
        'BR-396: сохранение на диске должно ждать промис экшена',
      )
    })
  })
})
