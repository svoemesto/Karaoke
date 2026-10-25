// BR-351 / BR-352 / BR-360 (аудит #336, пакет PKG-S3-28, OpenProject #400):
// страницы подписок и страница песни.
//
// BR-351 (LEAK). Таймер опроса статуса оплаты не сохранялся и не снимался при уходе
// со страницы: цепочка жила ещё 12 секунд и могла увести пользователя
// `$router.replace('/song?id=…')` с той страницы, на которой он уже находился.
//
// BR-352 (ERR). Отмена автопродления игнорировала ответ сервера: при 401/404/500 (или
// обрыве сети) список молча перезагружался, и пользователь не узнавал, что карта не
// отвязана.
//
// BR-360 (COMPAT). Признак SKIP-песни читался из `currentSong.tags`, но такого поля в
// `SongPublicDto` нет (есть вычисленный `contentRemoved`) — геттер был всегда `false`.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { readCode } from './sourceContract.js'

describe('BR-351: таймер опроса статуса оплаты', () => {
  test('id таймера сохраняется и снимается при размонтировании', () => {
    const src = readCode('src/views/SubscriptionReturnView.vue')
    assert.ok(src.includes('pollTimer'), 'нужен id таймера в состоянии компонента')
    assert.ok(src.includes('clearTimeout'), 'таймер должен сниматься')
    assert.ok(src.includes('beforeUnmount'), 'нужен beforeUnmount')
    assert.ok(
      src.includes('this.pollTimer = setTimeout'),
      'таймер опроса должен сохраняться в this.pollTimer',
    )
  })

  test('авто-переход выполняется только на смонтированной странице возврата', () => {
    const src = readCode('src/views/SubscriptionReturnView.vue')
    assert.ok(src.includes('isUnmounted'), 'нужен флаг размонтирования')
    assert.ok(src.includes('/subscription/return'), 'нужна проверка текущего маршрута')
  })
})

describe('BR-352: отмена автопродления подтверждается сервером', () => {
  test('статус ответа проверяется, список перезагружается только после успеха', () => {
    const src = readCode('src/views/SubscriptionsView.vue')
    assert.ok(src.includes('cancelError'), 'нужен видимый текст ошибки')
    assert.ok(src.includes('cancelMessage'), 'нужно подтверждение успеха')
    assert.ok(src.includes('status !== 200'), 'нужна проверка статуса ответа')
    assert.ok(src.includes('catch'), 'нужна обработка сетевого сбоя')
  })

  test('прежняя безусловная перезагрузка списка ушла', () => {
    const src = readCode('src/views/SubscriptionsView.vue')
    assert.ok(
      !/await authPost\([^)]*\)\s*\n\s*await this\.load\(\)/.test(src),
      'ответ cancel больше не игнорируется',
    )
  })
})

describe('BR-360: признак SKIP-песни', () => {
  test('берётся из contentRemoved, а не из несуществующего поля tags', () => {
    const src = readCode('src/views/SongView.vue')
    assert.ok(src.includes('contentRemoved'), 'нужен флаг contentRemoved из DTO')
    assert.ok(!/currentSong\.tags/.test(src), 'поля tags в SongPublicDto нет — читать нечего')
  })
})
