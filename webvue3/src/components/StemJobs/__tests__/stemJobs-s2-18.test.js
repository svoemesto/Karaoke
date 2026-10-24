// Регрессии PKG-S2-18 (аудит #336) по заданиям стемов: BR-462.
//
// При смене target список не очищался, а при неудачной загрузке catch только
// логировал — таблица продолжала показывать задания ДРУГОЙ БД под новой меткой.
// «Остановить»/«Удалить» отправляли target текущего селектора и id устаревшей
// строки: в прод-БД это удаление файлов чужого задания из хранилища.
//
// Экшены исполняются по-настоящему через шов FakeXhr; проверка баннера — по SFC.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import stemJobs from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const TABLE_SOURCE = readFileSync(new URL('../StemJobsTable.vue', import.meta.url), 'utf-8')

/**
 * Минимальный Vuex-контекст: state + commit, применяющий настоящие мутации модуля.
 *
 * @param {Object} storeModule
 * @returns {{ctx: Object, state: Object}}
 */
function createCtx(storeModule) {
  const state = structuredClone(storeModule.state)
  const ctx = { state }
  ctx.commit = (type, payload) => {
    const mutation = storeModule.mutations[type]
    if (!mutation) throw new Error(`unknown mutation: ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => storeModule.actions[type](ctx, payload)
  return { ctx, state }
}

describe('BR-462: список заданий не переживает смену БД', () => {
  test('смена target сразу очищает список и признак загруженной БД', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(stemJobs)
    state.stemJobs = [{ id: 1 }]
    state.stemJobsLoadedTarget = 'remote'
    await stemJobs.actions.setStemJobsTarget(ctx, 'local')
    assert.deepEqual(state.stemJobs, [], 'строки другой БД нельзя оставлять под новой меткой')
    assert.equal(state.stemJobsLoadedTarget, null)
    assert.equal(state.stemJobsError, null)
  })

  test('неудачная загрузка очищает список и фиксирует ошибку (fail-closed)', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(stemJobs)
    state.stemJobs = [{ id: 1, target: 'local' }]
    state.stemJobsLoadedTarget = 'local'
    const promise = stemJobs.actions.loadStemJobs(ctx)
    FakeXhr.sent[0].respond(500, 'db down')
    assert.equal(await promise, false)
    assert.deepEqual(state.stemJobs, [], 'устаревшие строки не должны оставаться кликабельными')
    assert.equal(state.stemJobsLoadedTarget, null)
    assert.ok(state.stemJobsError, 'ошибка должна быть видна пользователю')
  })

  test('поздний ответ прежней БД игнорируется', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(stemJobs)
    state.stemJobsTarget = 'remote'
    const promise = stemJobs.actions.loadStemJobs(ctx)
    // пользователь переключил БД, пока запрос в пути
    await stemJobs.actions.setStemJobsTarget(ctx, 'local')
    FakeXhr.sent[0].respond(200, { stemJobs: [{ id: 42, target: 'remote' }] })
    assert.equal(await promise, false)
    assert.deepEqual(state.stemJobs, [], 'ответ прежней БД не должен попасть в таблицу')
    assert.equal(state.stemJobsLoadedTarget, null)
  })

  test('deleteStemJob при ошибке возвращает false и не удаляет строку молча', async () => {
    FakeXhr.install()
    const { ctx, state } = createCtx(stemJobs)
    state.stemJobs = [{ id: 7 }]
    const promise = stemJobs.actions.deleteStemJob(ctx, 7)
    FakeXhr.sent[0].respond(500, 'boom')
    assert.equal(await promise, false)
    assert.deepEqual(state.stemJobs, [{ id: 7 }], 'строка не должна исчезать без подтверждения')
    assert.ok(state.stemJobsError)
  })

  test('таблица показывает ошибку загрузки', () => {
    assert.match(TABLE_SOURCE, /v-if="error"/, 'ошибка должна рендериться баннером')
    assert.match(
      TABLE_SOURCE,
      /return this\.\$store\.getters\.getStemJobsError/,
      'компонент должен читать ошибку из store',
    )
  })
})
