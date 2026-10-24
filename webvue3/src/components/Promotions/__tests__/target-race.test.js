// BR-407 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Гонка: `loadPromoRulesList` отправлял запрос с текущим `promoRulesTarget`, а ответ применял
// безусловно. Быстрое переключение «Локальная/Сервер» давало два параллельных запроса; побеждал
// последний пришедший ответ, а не последний запрос, — таблица показывала акции другой БД. Правка
// же (`savePromoRule`/`deletePromoRule`) подставляла текущий target, то есть уходила в ту БД,
// которую админ выбрал, а не в ту, откуда пришла строка.
//
// Тест воспроизводит перемежающийся порядок через шов FakeXhr: ответ прежней БД приходит
// последним. На коде до правки падает вторая половина (list подменяется, мутация уходит не туда).
//
// Запуск: cd webvue3 && npm run test:unit

import { beforeEach, describe, test } from 'node:test'
import assert from 'node:assert/strict'

import promotions from '../store.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

/** Минимальный Vuex-контекст: только state/commit/dispatch, без зависимости от vuex. */
function createCtx(module) {
  const state = structuredClone(module.state)
  const foreign = []
  const ctx = { state, getters: {} }
  ctx.commit = (type, payload) => {
    const mutation = module.mutations[type]
    assert.ok(mutation, `неизвестная мутация ${type}`)
    mutation(state, payload)
  }
  ctx.dispatch = (type, payload) => {
    const action = module.actions[type]
    if (action) return action(ctx, payload)
    foreign.push({ type, payload })
    return Promise.resolve({ success: true })
  }
  return { ctx, state, foreign }
}

const byTarget = (target) => (request) => request.params.target === target

describe('BR-407: гонка цели (target) в списке акций', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  test('ответ прежней БД, пришедший последним, не подменяет список текущей', async () => {
    const { ctx, state } = createCtx(promotions)

    const localLoad = ctx.dispatch('loadPromoRulesList')
    ctx.dispatch('setPromoRulesTarget', 'remote')
    const remoteLoad = ctx.dispatch('loadPromoRulesList')

    const localRequest = FakeXhr.last(byTarget('local'))
    const remoteRequest = FakeXhr.last(byTarget('remote'))

    // Ответ текущей БД приходит первым, ответ прежней — последним.
    remoteRequest.respond(200, { promoRules: [{ id: 2, name: 'remote-rule' }] })
    await remoteLoad
    localRequest.respond(200, { promoRules: [{ id: 1, name: 'local-rule' }] })
    await localLoad

    assert.deepEqual(
      state.promoRulesList.map((rule) => rule.name),
      ['remote-rule'],
      'в таблице должны остаться акции выбранной БД, а не последнего пришедшего ответа',
    )
    assert.equal(state.promoRulesListTarget, 'remote')
  })

  test('смена target очищает список до прихода ответа новой БД', async () => {
    const { ctx, state } = createCtx(promotions)

    const firstLoad = ctx.dispatch('loadPromoRulesList')
    FakeXhr.last(byTarget('local')).respond(200, { promoRules: [{ id: 1, name: 'local-rule' }] })
    await firstLoad
    assert.equal(state.promoRulesList.length, 1)

    ctx.dispatch('setPromoRulesTarget', 'remote')

    assert.deepEqual(
      state.promoRulesList,
      [],
      'строки прежней БД не должны изображать данные новой',
    )
    assert.equal(state.promoRulesListTarget, 'local', 'происхождение списка не переписывается')
  })

  test('сохранение адресуется БД показанного списка, а не текущему селекту', async () => {
    const { ctx, state } = createCtx(promotions)

    const load = ctx.dispatch('loadPromoRulesList')
    FakeXhr.last(byTarget('local')).respond(200, { promoRules: [{ id: 7, name: 'local-rule' }] })
    await load

    // Админ переключил селект, но список ещё от прежней БД (ответ новой в пути).
    ctx.dispatch('setPromoRulesTarget', 'remote')

    const save = ctx.dispatch('savePromoRule', { id: 7, name: 'renamed' })
    const saveRequest = FakeXhr.last((request) => request.url === '/api/promorules/update')
    assert.equal(
      saveRequest.params.target,
      'local',
      'правка строки должна уйти в ту БД, откуда строка пришла',
    )
    assert.equal(state.promoRulesTarget, 'remote', 'селект при этом остаётся на выбранной БД')

    saveRequest.respond(200, {})
    await new Promise((resolve) => setImmediate(resolve))
    FakeXhr.last(byTarget('remote')).respond(200, { promoRules: [] })
    await save
  })

  test('удаление адресуется БД показанного списка, а не текущему селекту', async () => {
    const { ctx } = createCtx(promotions)

    const load = ctx.dispatch('loadPromoRulesList')
    FakeXhr.last(byTarget('local')).respond(200, { promoRules: [{ id: 9, name: 'local-rule' }] })
    await load

    ctx.dispatch('setPromoRulesTarget', 'remote')

    const remove = ctx.dispatch('deletePromoRule', 9)
    const deleteRequest = FakeXhr.last((request) => request.url === '/api/promorules/delete')
    assert.equal(deleteRequest.params.target, 'local')
    assert.equal(deleteRequest.params.id, '9')

    deleteRequest.respond(200, {})
    await new Promise((resolve) => setImmediate(resolve))
    FakeXhr.last(byTarget('remote')).respond(200, { promoRules: [] })
    await remove
  })
})
