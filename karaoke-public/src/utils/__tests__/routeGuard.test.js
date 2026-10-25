// BR-316 / BR-317 (аудит #336, пакет PKG-S3-27, OpenProject #399): навигация и polling.
//
// BR-316 — ЧТО БЫЛО. `router.beforeEach` для `/zakroma/:id` ждал
// `store.dispatch('zakroma/loadAuthorTiles')` без try/catch. Экшен реджектит промис на любом
// не-2xx, поэтому отклонённый guard ОТМЕНЯЛ навигацию: при прямом заходе по ссылке
// router-view оставался пустым (белый экран), при клике переход «не срабатывал» без сообщения.
//
// BR-317 — ЧТО БЫЛО. `useNewsUnreadCount` проверял `isAuthenticated()` только в момент
// создания интервала, а `stopPolling()` вызывался исключительно при переходе на скрытый
// маршрут. После выхода из аккаунта на обычном маршруте polling продолжал тикать каждые
// 45 секунд и запрашивать `/api/public/news/since` уже без токена.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { routeGuardOutcome, shouldPollNews } from '../routeGuard.js'
import { readCode } from './sourceContract.js'

describe('BR-316: ошибка предзагрузки не отменяет навигацию', () => {
  test('ошибки нет — навигация продолжается', () => {
    const out = routeGuardOutcome(null)
    assert.equal(out.abort, false)
  })

  test('ошибка предзагрузки — навигация ВСЁ РАВНО продолжается', () => {
    const out = routeGuardOutcome(new Error('HTTP 500'))
    assert.equal(
      out.abort,
      false,
      'отменённый guard оставлял router-view пустым (белый экран) без сообщения',
    )
    assert.equal(out.notify, true)
  })
})

describe('BR-317: polling новостей останавливается без токена', () => {
  test('аноним — polling выключен (даже на обычном маршруте)', () => {
    assert.equal(shouldPollNews(false, false), false)
    assert.equal(shouldPollNews(false, true), false)
  })

  test('залогинен на обычном маршруте — polling включён', () => {
    assert.equal(shouldPollNews(true, false), true)
  })

  test('залогинен на скрытом маршруте (news/player/share) — polling выключен', () => {
    assert.equal(shouldPollNews(true, true), false)
  })
})

describe('BR-316 / BR-317: контракт исходников (падает на старом коде)', () => {
  test('router: dispatch обёрнут в try/catch и навигация не отменяется', () => {
    const src = readCode('src/router/index.js')
    assert.ok(src.includes('routeGuardOutcome'), 'нужна политика guard-а')
    const guard = src.slice(src.indexOf('router.beforeEach'))
    assert.ok(
      /try \{[\s\S]{0,300}await store\.dispatch\('zakroma\/loadAuthorTiles'/.test(guard),
      'dispatch должен быть в try/catch (BR-316)',
    )
    assert.ok(
      /catch \(e\) \{[\s\S]{0,300}routeGuardOutcome\(e\)/.test(guard),
      'в catch решается, отменять ли навигацию',
    )
  })

  test('useNewsUnreadCount: polling останавливается при !isAuthenticated()', () => {
    const src = readCode('src/composables/useNewsUnreadCount.js')
    assert.ok(src.includes('shouldPollNews'), 'нужна проверка условия polling')
    const apply = src.slice(src.indexOf('function applyRouteState(route)'), src.indexOf('function init(route)'))
    assert.ok(
      /if \(!shouldPollNews\(isAuthenticated\(\), hidden\)\) \{[\s\S]{0,200}stopPolling\(\)/.test(
        apply,
      ),
      'при выходе из аккаунта polling должен останавливаться (BR-317)',
    )
    assert.ok(
      src.includes('watch(token'),
      'нужен watch на токен (вход/выход) — polling должен возобновляться при входе',
    )
  })
})
