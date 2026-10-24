// BR-340 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// готовность SmartCaptcha и клиентский гейт отправки формы.
//
// ЧТО БЫЛО. Наличие <script src="…captcha.js"> в DOM считалось признаком готовности:
// ветка `if (existing) { onReady(); return }` звала onReady() немедленно, без проверки
// `window.smartCaptcha`. Если скрипт ещё грузился (SPA-переход с /register и назад,
// медленная сеть) или упал раньше (`script.onerror = () => resolve()`, элемент при этом
// оставался в head навсегда), виджет не рендерился, captchaToken оставался пустым, а
// гейт отправки требовал «Подтвердите, что вы не робот» — невыполнимое действие,
// блокировавшее регистрацию до полной перезагрузки страницы.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { CAPTCHA_STATE, captchaScriptState, captchaClientGateEnabled } from '../captchaReady.js'

describe('BR-340: готовность капчи определяется глобалом, а не наличием <script>', () => {
  test('тег в DOM есть, window.smartCaptcha ещё нет — wait (а не ready)', () => {
    assert.equal(
      captchaScriptState({ scriptPresent: true, globalReady: false }),
      CAPTCHA_STATE.WAIT,
      'именно эта ветка раньше вызывала onReady() немедленно',
    )
  })

  test('тег упал (onerror) — unavailable', () => {
    assert.equal(
      captchaScriptState({ scriptPresent: true, globalReady: false, failed: true }),
      CAPTCHA_STATE.UNAVAILABLE,
    )
  })

  test('тег завис и не ответил за таймаут — unavailable', () => {
    assert.equal(
      captchaScriptState({ scriptPresent: true, globalReady: false, timedOut: true }),
      CAPTCHA_STATE.UNAVAILABLE,
    )
  })

  test('тега нет — load (создать скрипт)', () => {
    assert.equal(
      captchaScriptState({ scriptPresent: false, globalReady: false }),
      CAPTCHA_STATE.LOAD,
    )
  })

  test('глобал определён — ready', () => {
    assert.equal(
      captchaScriptState({ scriptPresent: true, globalReady: true }),
      CAPTCHA_STATE.READY,
    )
  })
})

describe('BR-340: клиентский гейт не требует невыполнимого', () => {
  test('виджет отрисован, токена нет — гейт включён', () => {
    assert.equal(
      captchaClientGateEnabled({
        clientKey: 'key',
        token: '',
        state: CAPTCHA_STATE.READY,
      }),
      true,
    )
  })

  test('токен получен — гейт выключен', () => {
    assert.equal(
      captchaClientGateEnabled({ clientKey: 'key', token: 'tok', state: CAPTCHA_STATE.READY }),
      false,
    )
  })

  test('капча недоступна — гейт ВЫКЛЮЧЕН (решает серверная проверка)', () => {
    for (const state of [CAPTCHA_STATE.WAIT, CAPTCHA_STATE.LOAD, CAPTCHA_STATE.UNAVAILABLE]) {
      assert.equal(
        captchaClientGateEnabled({ clientKey: 'key', token: '', state }),
        false,
        `в состоянии ${state} требовать токен нельзя — виджета на странице нет`,
      )
    }
  })

  test('клиентского ключа нет — гейт выключен (капча не настроена)', () => {
    assert.equal(
      captchaClientGateEnabled({ clientKey: '', token: '', state: CAPTCHA_STATE.UNAVAILABLE }),
      false,
    )
  })
})
