// Регрессионный тест на арифметику окна публикаций (BR-411, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `stringDDMMYYaddDays('', 90)` возвращала строку `NaN.NaN.N`:
// `stringDDMMYYtoDate('')` давал `new Date(NaN, NaN, NaN)`, и форматирование
// выводило поля Invalid Date. Эта строка уходила в `POST /api/publicationsdigest`
// как `filterDateTo`; бэкенд парсил её `SimpleDateFormat("dd.MM.yy")`, бросал
// ParseException и отвечал HTTP 500 (глобального @ControllerAdvice нет). Один клик
// по штатной кнопке EXCLUSIVE (эндпоинт `/api/publications/date` штатно отдаёт пустую
// строку) ломал грид публикаций; значение сохранялось в webvueProp и переживало F5.
//
// Тест фиксирует: «даты нет» — это пустая строка, а не NaN; календарно невалидная
// дата тоже не превращается в строку.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/publish-date-window.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { stringDDMMYYaddDays, isValidDDMMYY, stringDDMMYYtoDate } from '../utils.js'

describe('окно публикаций: пустая дата не превращается в NaN (BR-411)', () => {
  test('прибавление дней к пустой дате даёт пустую строку, а не NaN.NaN.N', () => {
    const result = stringDDMMYYaddDays('', 90)
    assert.equal(result, '', 'на бэкенд не должна уходить строка NaN.NaN.N')
    assert.ok(!result.includes('NaN'), 'в дате не должно быть NaN')
  })

  test('корректная арифметика дат сохраняется', () => {
    assert.equal(stringDDMMYYaddDays('01.02.26', 10), '11.02.26')
    assert.equal(stringDDMMYYaddDays('31.12.26', 1), '01.01.27')
    assert.equal(stringDDMMYYaddDays('01.03.26', -1), '28.02.26')
  })

  test('несуществующая календарная дата не даёт строку', () => {
    assert.equal(stringDDMMYYaddDays('32.13.26', 1), '')
    assert.equal(stringDDMMYYaddDays('31.02.26', 1), '')
  })

  test('валидатор отличает дату от мусора', () => {
    assert.equal(isValidDDMMYY('01.02.26'), true)
    assert.equal(isValidDDMMYY(''), false)
    assert.equal(isValidDDMMYY('NaN.NaN.N'), false)
    assert.equal(isValidDDMMYY('2026-02-01'), false)
    assert.equal(isValidDDMMYY(null), false)
    assert.equal(isValidDDMMYY(undefined), false)
  })

  test('разбор невалидной даты даёт null, а не Invalid Date', () => {
    assert.equal(stringDDMMYYtoDate(''), null)
    assert.equal(stringDDMMYYtoDate('NaN.NaN.N'), null)
    assert.ok(stringDDMMYYtoDate('01.02.26') instanceof Date)
  })
})
