// BR-350 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// состояния страницы возврата из ЮKassa.
//
// ЧТО БЫЛО. poll() не различал «ошибка проверки» и «подписки нет». Любой ответ, кроме
// 200 с массивом (401 от SiteAuthInterceptor, 500 от БД), и любой reject не меняли
// sub/orderSubs; после 6 попыток loading=false, и шаблон показывал «Подписка не
// найдена» / «Заказ не найден» — без текста ошибки и без кнопки повтора. При этом
// оплата уже могла пройти: Subscription.status → PAID выполняет вебхук ЮKassa, а не
// этот GET. Пользователь видел ложное «не найдена» и мог оплатить повторно.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  POLL_OUTCOME,
  POLL_VIEW,
  classifyPollResponse,
  nextPollDelay,
  subscriptionPollView,
} from '../subscriptionPoll.js'

const view = (over) =>
  subscriptionPollView({
    outcome: POLL_OUTCOME.OK,
    successfulPolls: 6,
    attempts: 6,
    maxAttempts: 6,
    hasResult: false,
    terminal: false,
    ...over,
  })

describe('BR-350: «не найдена» только после успешного ответа сервера', () => {
  test('6 попыток, все успешные, подписки нет → not-found', () => {
    assert.equal(view({}), POLL_VIEW.NOT_FOUND)
  })

  test('6 попыток, все с ошибкой 500 → check-error (НЕ «не найдена»)', () => {
    assert.equal(
      view({ outcome: POLL_OUTCOME.CHECK_ERROR, successfulPolls: 0, hasResult: false }),
      POLL_VIEW.CHECK_ERROR,
      'подмена ошибки проверки на «не найдена» заставляла оплачивать повторно',
    )
  })

  test('ошибка первого же ответа → check-error (а не «не найдена» и не вечный спиннер)', () => {
    assert.equal(
      view({
        outcome: POLL_OUTCOME.CHECK_ERROR,
        successfulPolls: 0,
        attempts: 1,
        hasResult: false,
      }),
      POLL_VIEW.CHECK_ERROR,
    )
  })

  test('ответ 200, но успех не зачтён → checking, «не найдена» объявлять нельзя', () => {
    assert.equal(
      view({ outcome: POLL_OUTCOME.OK, successfulPolls: 0, attempts: 2, hasResult: false }),
      POLL_VIEW.CHECKING,
    )
  })

  test('401 → unauthorized, повтор бессмысленен', () => {
    assert.equal(
      view({ outcome: POLL_OUTCOME.UNAUTHORIZED, successfulPolls: 0 }),
      POLL_VIEW.UNAUTHORIZED,
    )
  })

  test('терминальный статус → terminal (шаблон рисует PAID/FAILED)', () => {
    assert.equal(
      view({ successfulPolls: 1, attempts: 1, hasResult: true, terminal: true }),
      POLL_VIEW.TERMINAL,
    )
  })

  test('успешный 200, заказ найден, статус не терминальный → processing', () => {
    assert.equal(
      view({ successfulPolls: 1, attempts: 1, hasResult: true, terminal: false }),
      POLL_VIEW.PROCESSING,
    )
  })

  test('ошибка после успешной попытки — всё равно check-error, а не not-found', () => {
    assert.equal(
      view({ outcome: POLL_OUTCOME.CHECK_ERROR, successfulPolls: 1, hasResult: false }),
      POLL_VIEW.CHECK_ERROR,
    )
  })
})

describe('BR-350: классификация ответа списка подписок', () => {
  test('200 + массив — ok', () => {
    assert.equal(classifyPollResponse(null, 200, []), POLL_OUTCOME.OK)
  })

  test('200 + не массив (HTML/объект) — check-error', () => {
    assert.equal(classifyPollResponse(null, 200, {}), POLL_OUTCOME.CHECK_ERROR)
    assert.equal(classifyPollResponse(null, 200, null), POLL_OUTCOME.CHECK_ERROR)
  })

  test('401 — unauthorized', () => {
    assert.equal(classifyPollResponse(null, 401, null), POLL_OUTCOME.UNAUTHORIZED)
  })

  test('500/403 и сетевой reject — check-error', () => {
    assert.equal(classifyPollResponse(null, 500, null), POLL_OUTCOME.CHECK_ERROR)
    assert.equal(
      classifyPollResponse(new Error('network_error'), 0, null),
      POLL_OUTCOME.CHECK_ERROR,
    )
  })
})

describe('BR-350: расписание попыток', () => {
  test('пока попытки есть — задержка 2 с', () => {
    assert.equal(nextPollDelay(1, 6, 2000), 2000)
    assert.equal(nextPollDelay(5, 6, 2000), 2000)
  })

  test('попытки кончились — null (опрос останавливается)', () => {
    assert.equal(nextPollDelay(6, 6, 2000), null)
    assert.equal(nextPollDelay(7, 6, 2000), null)
  })
})
