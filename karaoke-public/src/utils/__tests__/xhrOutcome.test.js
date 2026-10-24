// BR-330 / BR-339 / BR-350 (аудит #336, пакет PKG-S2-14, OpenProject #377):
// регрессионный тест на классификацию исхода auth-запроса.
//
// ЧТО БЫЛО. `authGet`/`authPost` резолвят промис на ЛЮБОМ `onload` (тело доступно и
// на 4xx/5xx) и реджектят только на сетевой ошибке/таймауте. Компоненты проверяли
// только `status`, поэтому:
//   - EditorWorkView: saveState/submitting/recalling залипали навсегда при reject,
//     исключение уходило в unhandled rejection (BR-330);
//   - PlaylistsView/PlaylistEditView: `loading` оставался true (BR-339);
//   - SubscriptionReturnView: «сеть не ответила» и «401» превращались в «подписка
//     не найдена», хотя оплата могла пройти (BR-350).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { AUTH_OUTCOME, classifyAuthOutcome, describeAuthOutcome } from '../xhrOutcome.js'

describe('BR-330/339/350: исход auth-запроса различается', () => {
  test('200 без ошибки — ok', () => {
    assert.equal(classifyAuthOutcome(null, 200), AUTH_OUTCOME.OK)
  })

  test('сетевой reject — network (а не «ничего не найдено»)', () => {
    assert.equal(classifyAuthOutcome(new Error('network_error'), 0), AUTH_OUTCOME.NETWORK)
  })

  test('таймаут — отдельный исход timeout', () => {
    assert.equal(classifyAuthOutcome(new Error('timeout'), 0), AUTH_OUTCOME.TIMEOUT)
  })

  test('401 без reject — unauthorized (сессия истекла, повтор не поможет)', () => {
    assert.equal(classifyAuthOutcome(null, 401), AUTH_OUTCOME.UNAUTHORIZED)
  })

  test('500 — http (ошибка сервера, не «не найдено»)', () => {
    assert.equal(classifyAuthOutcome(null, 500), AUTH_OUTCOME.HTTP)
  })

  test('403 — http, вызывающий код решает сам (premium_required)', () => {
    assert.equal(classifyAuthOutcome(null, 403), AUTH_OUTCOME.HTTP)
  })

  test('каждому ошибочному исходу соответствует понятный текст, ok — пустой', () => {
    assert.equal(describeAuthOutcome(AUTH_OUTCOME.OK), '')
    for (const outcome of [
      AUTH_OUTCOME.NETWORK,
      AUTH_OUTCOME.TIMEOUT,
      AUTH_OUTCOME.UNAUTHORIZED,
      AUTH_OUTCOME.HTTP,
    ]) {
      const text = describeAuthOutcome(outcome)
      assert.ok(text.length > 0, `для ${outcome} должен быть текст ошибки`)
      assert.notEqual(text, 'Ошибка', 'текст должен объяснять причину, а не быть заглушкой')
    }
  })
})
