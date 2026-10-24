// Регрессии PKG-S2-18 (аудит #336) по статистике админ-дашборда.
//
//   BR-459 — ответ статистики не должен применяться, если он уже неактуален
//            (смена БД/периода во время запроса);
//   BR-460 — «Топ песен по подпискам» должен реально загружаться этой вкладкой;
//   BR-487 — смена target/days должна инвалидировать кеш всех затронутых вкладок,
//            а не только активной.
//
// Для BR-459/460 экшены исполняются по-настоящему: подменяем XMLHttpRequest и
// отвечаем «серверу» в нужном порядке. Для BR-487 тело методов StatsView
// извлекается из SFC и исполняется на фейковом `this` — так проверяется набор
// вкладок, которые реально инвалидируются.

import { test, describe, afterEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import statsStore from '../store.js'
import { statsRequestGuard } from '../request-guard.js'
import { FakeXhr } from '../../../test-utils/fakeXhr.js'

const VIEW_SOURCE = readFileSync(new URL('../../../views/StatsView.vue', import.meta.url), 'utf-8')

/**
 * Минимальный Vuex-контекст: state + commit, применяющий настоящие мутации модуля.
 *
 * @param {Object} storeModule
 * @returns {{ctx: Object, state: Object}}
 */
function createStoreContext(storeModule) {
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

afterEach(() => {
  statsRequestGuard.reset()
})

describe('BR-459: поздний ответ статистики не подменяет данные', () => {
  test('ответ прежней БД игнорируется после смены target', async () => {
    FakeXhr.install()
    statsRequestGuard.reset()
    const { ctx, state } = createStoreContext(statsStore)

    state.statsTarget = 'local'
    const first = statsStore.actions.loadStatsGeo(ctx)
    state.statsTarget = 'remote'
    const second = statsStore.actions.loadStatsGeo(ctx)

    assert.equal(FakeXhr.sent.length, 4, 'обе загрузки шлют по два endpoint`а')
    assert.match(FakeXhr.sent[0].url, /target=local/)
    assert.match(FakeXhr.sent[2].url, /target=remote/)

    // Сначала отвечает НОВЫЙ запрос, затем — старый (медленный GeoIP).
    FakeXhr.sent[2].respond(200, { items: [{ country: 'REMOTE' }] })
    FakeXhr.sent[3].respond(200, { items: [{ referrer: 'REMOTE' }] })
    await Promise.resolve()
    FakeXhr.sent[0].respond(200, { items: [{ country: 'LOCAL' }] })
    FakeXhr.sent[1].respond(200, { items: [{ referrer: 'LOCAL' }] })
    await Promise.all([first, second])

    assert.deepEqual(state.countries, [{ country: 'REMOTE' }], 'данные должны остаться от новой БД')
    assert.deepEqual(state.referrers, [{ referrer: 'REMOTE' }])
  })
})

describe('BR-460: топ песен по подпискам действительно загружается', () => {
  test('экшен возвращает Promise<Boolean> и коммитит элементы', async () => {
    FakeXhr.install()
    const { ctx, state } = createStoreContext(statsStore)
    const promise = statsStore.actions.loadMonetizationTopSongs(ctx, { limit: 5 })
    assert.match(FakeXhr.sent[0].url, /monetization\/top-songs/)
    FakeXhr.sent[0].respond(200, { items: [{ songId: 7, count: 3 }] })
    assert.equal(await promise, true, 'успешная загрузка должна возвращать true (контракт TTL)')
    assert.deepEqual(state.monetizationTopSongs, [{ songId: 7, count: 3 }])
  })

  test('вкладка «Монетизация» перечисляет оба endpoint`а', () => {
    const source = /const tabEndpoints = (\{[\s\S]*?\n\})/.exec(VIEW_SOURCE)
    assert.ok(source, 'tabEndpoints должен существовать')
    const tabEndpoints = new Function('return ' + source[1])()
    assert.ok(
      tabEndpoints[1].includes('monetization-top-songs'),
      'без второго endpoint`а state.monetizationTopSongs всегда пуст',
    )
    const actions = /const simpleEndpointActions = (\{[\s\S]*?\n\})/.exec(VIEW_SOURCE)
    assert.ok(actions, 'simpleEndpointActions должен существовать')
    const simpleEndpointActions = new Function('return ' + actions[1])()
    assert.equal(simpleEndpointActions['monetization-top-songs'], 'loadMonetizationTopSongs')
  })
})

describe('BR-487: смена target/days инвалидирует кеш затронутых вкладок', () => {
  /**
   * Достать тело метода из SFC по имени.
   *
   * @param {string} source
   * @param {string} name
   * @returns {string}
   */
  const methodBody = (source, name) => {
    const definition = new RegExp('\\n\\s*' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(source)
    assert.ok(definition, `метод ${name} должен существовать`)
    const open = source.indexOf('{', definition.index + definition[0].length - 1)
    let depth = 0
    for (let i = open; i < source.length; i++) {
      if (source[i] === '{') depth++
      else if (source[i] === '}') {
        depth--
        if (depth === 0) return source.slice(open + 1, i)
      }
    }
    throw new Error('unbalanced method ' + name)
  }

  const tabEndpointsSource = /const tabEndpoints = (\{[\s\S]*?\n\})/.exec(VIEW_SOURCE)[1]
  const tabEndpoints = new Function('return ' + tabEndpointsSource)()
  const daySetSource = /const dayDependentEndpoints = new Set\((\[[^\]]*\])\)/.exec(VIEW_SOURCE)[1]
  const dayDependentEndpoints = new Set(new Function('return ' + daySetSource)())

  test('invalidateAllTabs сбрасывает данные всех вкладок', () => {
    const body = methodBody(VIEW_SOURCE, 'invalidateAllTabs')
    const invalidateAll = new Function('tabEndpoints', 'return function(){' + body + '}')(
      tabEndpoints,
    )
    const cleared = []
    invalidateAll.call({ clearActiveTabData: (tab) => cleared.push(tab) })
    assert.deepEqual(
      cleared.sort((a, b) => a - b),
      [0, 1, 2, 3, 4, 5, 6, 7],
    )
  })

  test('invalidateDayDependentTabs сбрасывает только вкладки 0/2/3', () => {
    const body = methodBody(VIEW_SOURCE, 'invalidateDayDependentTabs')
    const invalidateDays = new Function(
      'tabEndpoints',
      'dayDependentEndpoints',
      'return function(){' + body + '}',
    )(tabEndpoints, dayDependentEndpoints)
    const cleared = []
    invalidateDays.call({ clearActiveTabData: (tab) => cleared.push(tab) })
    assert.deepEqual(
      cleared.sort((a, b) => a - b),
      [0, 2, 3],
    )
  })

  test('onTargetChange инвалидирует все вкладки и грузит активную', () => {
    const body = methodBody(VIEW_SOURCE, 'onTargetChange')
    const onTargetChange = new Function('return function(){' + body + '}')()
    const calls = { invalidateAll: 0, loaded: [] }
    onTargetChange.call({
      activeTab: 3,
      statsBySongPage: 5,
      webEventsPage: 5,
      topUsersPage: 5,
      topListenedPage: 5,
      invalidateAllTabs() {
        calls.invalidateAll++
      },
      loadDataForActiveTab(tab) {
        calls.loaded.push(tab)
      },
    })
    assert.equal(calls.invalidateAll, 1, 'должны чиститься все вкладки, а не только активная')
    assert.deepEqual(calls.loaded, [3])
  })
})
