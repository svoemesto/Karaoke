// Регрессионный тест на контракт подтверждения `CustomConfirm` (BR-399, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `CustomConfirm.ok()` собирал результат ТОЛЬКО из `params.fields` и не
// выставлял признак подтверждения. `ProcessEdit.save()` проверяет `ret.confirmed`,
// получал `undefined` и не вызывал `doSave(changes)`: модалка закрывалась, запрос
// `POST /api/admin/processes/{id}/edit` не уходил — форма редактирования процесса
// была нефункциональна целиком, а UI выглядел как успешное сохранение.
//
// Тест фиксирует контракт: «Да» → `confirmed === true` (и поля формы на месте),
// «Отмена/таймаут» → `confirmed === false`.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/confirm-result.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { buildConfirmResult } from '../confirm-result.js'

describe('контракт результата CustomConfirm (BR-399)', () => {
  test('нажатие «Да» даёт confirmed === true', () => {
    // Ровно те params, что передаёт ProcessEdit.save(): fields нет вообще.
    const ret = buildConfirmResult({ header: 'Подтвердите сохранение' }, true)
    assert.equal(ret.confirmed, true, 'без confirmed колбэк ProcessEdit не сохраняет')
  })

  test('отмена/таймаут дают confirmed === false', () => {
    const ret = buildConfirmResult({ header: 'Подтвердите сохранение' }, false)
    assert.equal(ret.confirmed, false)
  })

  test('поля формы по-прежнему попадают в результат', () => {
    const params = {
      fields: [
        { fldName: 'author', fldValue: 'Иванов' },
        { fldName: 'watched', fldValue: true },
      ],
    }
    const ret = buildConfirmResult(params, true)
    assert.deepEqual(ret, { confirmed: true, author: 'Иванов', watched: true })
  })

  test('отсутствие params.fields не роняет сборку результата', () => {
    assert.deepEqual(buildConfirmResult({}, true), { confirmed: true })
    assert.deepEqual(buildConfirmResult(undefined, false), { confirmed: false })
  })
})
