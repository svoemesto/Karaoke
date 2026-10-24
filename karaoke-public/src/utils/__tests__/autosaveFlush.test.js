// BR-327 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// правило «отложенное автосохранение перед уходом со страницы сбрасывается, а не
// отменяется».
//
// ЧТО БЫЛО. Автосохранение в EditorWorkView отложено на 3 с; `beforeUnmount` просто
// гасил таймер, а `beforeunload`/`beforeRouteLeave` в файле отсутствовали вовсе.
// Уход со страницы в окне 0–3 с после последней правки терял её без следа.
//
// ЧТО ПРОВЕРЯЕТСЯ. Решение принимается по двум фактам: таймер взведён И правка
// разрешена. Тогда уход обязан выполнить flush; иначе — нечего сохранять.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { AUTOSAVE_PLAN, autosaveFlushPlan } from '../autosave.js'

describe('BR-327: уход со страницы не отменяет отложенное сохранение', () => {
  test('таймер взведён + правка разрешена → flush (а не отмена таймера)', () => {
    assert.equal(autosaveFlushPlan({ timerArmed: true, canEdit: true }), AUTOSAVE_PLAN.FLUSH)
  })

  test('таймера нет → сохранять нечего', () => {
    assert.equal(autosaveFlushPlan({ timerArmed: false, canEdit: true }), AUTOSAVE_PLAN.NONE)
  })

  test('задание только на проверке (canEdit=false) → сохранять нечего', () => {
    assert.equal(autosaveFlushPlan({ timerArmed: true, canEdit: false }), AUTOSAVE_PLAN.NONE)
  })

  test('правка запрещена и таймера нет → none (не шлём лишний запрос)', () => {
    assert.equal(autosaveFlushPlan({ timerArmed: false, canEdit: false }), AUTOSAVE_PLAN.NONE)
  })
})
