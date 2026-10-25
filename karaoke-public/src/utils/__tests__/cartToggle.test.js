// BR-297 / BR-314 (аудит #336, пакет PKG-S3-27, OpenProject #399): корзина.
//
// BR-297 — ЧТО БЫЛО. `useCart.toggle()` возвращает `{status, body}`, а `CartIcon.onClick()`
// результат НЕ анализировал: `await toggle(props.songId)` в try/finally без catch и без
// ветки на status. Ответ 409 `already_subscribed`, 400 `song_not_for_subscription`, 401
// (истёкший JWT) и 5xx оставляли иконку в прежнем состоянии — кнопка «не работала».
//
// BR-314 — ЧТО БЫЛО. `load()` для анонима ставил общий модульный `loaded = true`, из-за чего
// после входа `load()` выходил по `if (loaded && !force) return` и корзина оставалась пустой
// (иконка в шапке не появлялась). При выходе `items`/`songIds` прежнего пользователя оставались
// доступны следующему в том же браузере.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  CART_TOGGLE_RESULT,
  classifyCartToggle,
  needsCartLoad,
} from '../cartToggle.js'
import { readSource } from './sourceContract.js'

describe('BR-297: результат toggle разбирается', () => {
  test('200 — ок, без сообщения', () => {
    const outcome = classifyCartToggle(200, { ok: true })
    assert.equal(outcome.result, CART_TOGGLE_RESULT.OK)
    assert.equal(outcome.message, '')
    assert.equal(outcome.hideIcon, false)
  })

  test('409 already_subscribed — «уже оформлена», иконку надо скрыть', () => {
    const outcome = classifyCartToggle(409, { error: 'already_subscribed' })
    assert.equal(outcome.result, CART_TOGGLE_RESULT.ALREADY_SUBSCRIBED)
    assert.equal(outcome.hideIcon, true, 'подписка уже есть — иконка корзины лишняя')
    assert.ok(outcome.message.length > 0, 'пользователь должен получить сообщение')
  })

  test('400 song_not_for_subscription — «песня не продаётся поштучно»', () => {
    const outcome = classifyCartToggle(400, { error: 'song_not_for_subscription' })
    assert.equal(outcome.result, CART_TOGGLE_RESULT.NOT_FOR_SUBSCRIPTION)
    assert.equal(outcome.hideIcon, false)
    assert.ok(outcome.message.length > 0)
  })

  test('401/403 — сессия истекла', () => {
    assert.equal(classifyCartToggle(401, null).result, CART_TOGGLE_RESULT.UNAUTHORIZED)
    assert.equal(classifyCartToggle(403, null).result, CART_TOGGLE_RESULT.UNAUTHORIZED)
  })

  test('5xx — общая ошибка с повтором', () => {
    const outcome = classifyCartToggle(500, null)
    assert.equal(outcome.result, CART_TOGGLE_RESULT.ERROR)
    assert.ok(outcome.message.length > 0)
  })

  test('5xx с тем же кодом — сообщаем, но иконку НЕ прячем (тело 5xx недоверенное)', () => {
    const outcome = classifyCartToggle(500, { error: 'already_subscribed' })
    assert.equal(outcome.result, CART_TOGGLE_RESULT.ALREADY_SUBSCRIBED)
    assert.equal(
      outcome.hideIcon,
      false,
      'при 5xx иконку прятать нельзя — сервер мог отдать тело не по контракту',
    )
  })
})

describe('BR-314: корзина загружается только для текущего пользователя', () => {
  test('аноним — грузить нечего (и флаг «загружено» НЕ ставится)', () => {
    assert.equal(
      needsCartLoad({ hasToken: false, loaded: true, loadedForToken: null, token: '' }),
      false,
    )
  })

  test('залогинен, но ещё не грузили — грузим', () => {
    assert.equal(
      needsCartLoad({ hasToken: true, loaded: false, loadedForToken: null, token: 't1' }),
      true,
    )
  })

  test('загрузили для ЭТОГО же токена — не грузим повторно', () => {
    assert.equal(
      needsCartLoad({ hasToken: true, loaded: true, loadedForToken: 't1', token: 't1' }),
      false,
    )
  })

  test('сменился пользователь (другой токен) — грузим заново', () => {
    assert.equal(
      needsCartLoad({ hasToken: true, loaded: true, loadedForToken: 't1', token: 't2' }),
      true,
      'корзина прежнего пользователя не должна показываться следующему',
    )
  })
})

describe('BR-297 / BR-314: контракт исходников (падает на старом коде)', () => {
  test('CartIcon разбирает результат toggle', () => {
    const src = readSource('src/components/CartIcon.vue')
    assert.ok(src.includes('classifyCartToggle'), 'CartIcon должен разбирать ответ toggle')
    assert.ok(
      /await toggle\(props\.songId\)\s*\n\s*const \{ status, body \} =/.test(src) ||
        /const \{ status, body \} = await toggle\(props\.songId\)/.test(src),
      'нужен разбор status/body ответа toggle',
    )
    assert.ok(
      src.includes('CART_TOGGLE_RESULT'),
      'результат классификации должен сравниваться с CART_TOGGLE_RESULT.OK',
    )
    assert.ok(
      /catch\s*\(\w+\)\s*\{[\s\S]{0,200}notify\(/.test(src),
      'сетевой reject должен показываться пользователю, а не глотаться',
    )
  })

  test('useCart не помечает корзину загруженной для анонима', () => {
    const src = readSource('src/composables/useCart.js')
    assert.ok(src.includes('needsCartLoad'), 'useCart должен использовать needsCartLoad')
    const anonymousBlock = src.slice(src.indexOf('if (!currentToken)'), src.indexOf('if (!force'))
    assert.ok(
      /loaded = false/.test(anonymousBlock),
      'для анонима loaded должен сбрасываться (иначе после входа корзина не загрузится)',
    )
    assert.ok(
      !/loaded = true/.test(anonymousBlock),
      'для анонима НЕЛЬЗЯ ставить loaded = true — это и был баг',
    )
    assert.ok(src.includes('function reset()'), 'нужен reset() для сброса при выходе')
  })

  test('useAuthBootstrap сбрасывает корзину при logout', () => {
    const src = readSource('src/composables/useAuthBootstrap.js')
    assert.ok(
      src.includes('resetCart'),
      'при logout корзина должна сбрасываться (BR-314)',
    )
  })
})
