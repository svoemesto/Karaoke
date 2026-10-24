// Контракт экранирования пользовательского текста (спека #558, OpenProject #360,
// дефект BR-123 из аудита #336).
//
// Проверяем ровно то, что ломается тихо и не видно на глаз:
//
//  1. РЕГРЕСС-КОНТРОЛЬ (прецедент спеки #215): для значения БЕЗ спецсимволов
//     результат экранирования побайтово совпадает с исходным значением.
//     Если это перестало быть так — значит экранирование начало менять данные,
//     которые меняться не должны (счётчики, идентификаторы, закрытые перечисления).
//
//  2. НЕТ ДВОЙНОГО ЭКРАНИРОВАНИЯ: значение с `&` должно дать ровно одну
//     `&amp;`, а не `&amp;amp;`. На экране пользователь должен видеть `&`.
//
//  3. Экранирование ровно один раз: компонент подтверждения НЕ экранирует
//     значение повторно на выходе (это проверяется отдельно — guard
//     tools/check-confirm-escape.py и сам CustomConfirm.vue).
//
//  4. null/undefined не роняют: подстановка в текст подтверждения не должна
//     приводить к «undefined» на экране.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { escapeHtml } from '../escapeHtml.js'

describe('escapeHtml — регресс-контроль (до/после)', () => {
  test('значение без спецсимволов совпадает с базовой линией побайтово', () => {
    const BASELINE = [
      'Говорит Деметра',
      'Song 11718 (Demo)',
      'Автор — Иванов И.И.',
      'Песня № 42 (2024)',
      'АБВ ГДЁ ЖЗИ',
      'file-name_with.dots',
      '100%',
      '',
    ]
    for (const value of BASELINE) {
      assert.equal(escapeHtml(value), value, `значение изменилось: ${value}`)
    }
  })

  test('одиночный & экранируется ровно один раз (нет &amp;amp;)', () => {
    assert.equal(escapeHtml('a & b'), 'a &amp; b')
    assert.equal(escapeHtml('a & b').includes('&amp;amp;'), false)
    assert.equal(escapeHtml('&').split('&amp;').length - 1, 1)
  })

  test('символы разметки не превращаются в элементы', () => {
    assert.equal(escapeHtml('<img src=x onerror=alert(1)>'), '&lt;img src=x onerror=alert(1)&gt;')
    assert.equal(escapeHtml('<b>все</b>'), '&lt;b&gt;все&lt;/b&gt;')
    assert.equal(escapeHtml('"всё"'), '&quot;всё&quot;')
    assert.equal(escapeHtml("it's"), 'it&#39;s')
  })

  test('значение, уже содержащее &, отображается как & (один проход экранирования)', () => {
    // Вход: «Том & Джерри». После экранирования в HTML-буфере — «Том &amp; Джерри».
    // Браузер покажет «Том & Джерри» — ровно один раз, без «&amp;» на экране.
    const once = escapeHtml('Том & Джерри')
    assert.equal(once, 'Том &amp; Джерри')
    // Повторное экранирование той же строки — это уже двойное экранирование
    // (его в проекте быть не должно), и guard это ловит отдельно.
    assert.equal(escapeHtml(once), 'Том &amp;amp; Джерри')
  })

  test('null/undefined не роняют и не дают «undefined» на экране', () => {
    assert.equal(escapeHtml(null), '')
    assert.equal(escapeHtml(undefined), '')
    assert.equal(escapeHtml(''), '')
  })

  test('не-строки приводятся к строке без потерь', () => {
    assert.equal(escapeHtml(42), '42')
    assert.equal(escapeHtml(0), '0')
    assert.equal(escapeHtml(false), 'false')
  })
})
