// BR-319 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// состояние доступа к чату и различение 403 premium_required / 401 / ошибки.
//
// ЧТО БЫЛО. ChatView решал про доступ в `mounted()` по кэшу `km_auth_user`, хотя
// `isPremium` — computed от асинхронно обновляемого `user.effectivePremium`. При
// устаревшем кэше гейт срабатывал по старому флагу: тред не грузился, `loading`
// оставался true навсегда — «Загрузка...» до перезагрузки страницы. Обратный случай
// (кэш «премиум», сервер — нет) давал 403 premium_required, который initialLoad()
// молча превращал в пустой тред «Сообщений пока нет. Напишите первым!».
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { CHAT_ACCESS, chatAccessState, chatLoadFailure } from '../chatAccess.js'

describe('BR-319: доступ к чату решается после ответа сервера, а не по кэшу', () => {
  test('не залогинен — login (кэш не важен)', () => {
    assert.equal(
      chatAccessState({ isLoggedIn: false, isPremium: true, premiumChecked: true }),
      CHAT_ACCESS.LOGIN,
    )
  })

  test('пока /auth/me не ответил — checking, даже если кэш говорит «премиум»', () => {
    assert.equal(
      chatAccessState({ isLoggedIn: true, isPremium: true, premiumChecked: false }),
      CHAT_ACCESS.CHECKING,
    )
  })

  test('ответ получен и премиум есть — ready', () => {
    assert.equal(
      chatAccessState({ isLoggedIn: true, isPremium: true, premiumChecked: true }),
      CHAT_ACCESS.READY,
    )
  })

  test('кэш говорил «премиум», сервер — нет: locked (а не вечная «Загрузка...»)', () => {
    assert.equal(
      chatAccessState({ isLoggedIn: true, isPremium: false, premiumChecked: true }),
      CHAT_ACCESS.LOCKED,
    )
  })
})

describe('BR-319: сбой загрузки треда не маскируется под «сообщений нет»', () => {
  test('200 — успех', () => {
    assert.equal(chatLoadFailure(200, { messages: [] }), null)
  })

  test('403 premium_required — «подписка неактивна», а не пустой тред', () => {
    assert.equal(chatLoadFailure(403, { error: 'premium_required' }), 'premium_required')
  })

  test('401 — истёкшая сессия', () => {
    assert.equal(chatLoadFailure(401, null), 'unauthorized')
  })

  test('500 и 403 с другим кодом — ошибка с повтором', () => {
    assert.equal(chatLoadFailure(500, null), 'error')
    assert.equal(chatLoadFailure(403, { error: 'banned' }), 'error')
  })
})
