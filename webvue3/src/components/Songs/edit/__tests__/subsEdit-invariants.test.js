// Инварианты редактора SubsEdit, которые нельзя проверить кликом.
//
// Контекст: дефект #194 (аудит #336, BR-619). getStringsForAllNotesInSong()
// писал this.indexTabsVariant, а метод вызывается из computed
// lstIndexesTabsVariant — то есть присваивание происходило В ФАЗЕ RENDER, и
// computed был нечистым. Кламп вынесли в watcher.
//
// Проверить это e2e-тестом нельзя: разница между старой и новой реализацией —
// МОМЕНТ применения клампа (render против watcher), а проверяемое значение
// одинаково. Поэтому инвариант проверяется СТАТИЧЕСКИ: по исходнику компонента.
//
// Тест работает без браузера и без стенда, поэтому выполняется в CI
// (npm run test:unit) — в отличие от самого e2e-набора.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const SOURCE = readFileSync(new URL('../SubsEdit.vue', import.meta.url), 'utf-8')

/**
 * Достаёт тело метода по его имени: ищет определение в начале строки
 * (с необязательными аргументами) и сопоставляет скобки.
 *
 * @param {string} source
 * @param {string} name - имя метода
 * @returns {string|null} тело метода либо null, если метода нет
 */
const methodBody = (source, name) => {
  const definition = new RegExp('\\n\\s*' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(source)
  if (!definition) return null
  const open = source.indexOf('{', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(open + 1, i)
    }
  }
  return null
}

/**
 * Достаёт тело обработчика watcher'а вида `name: { handler(v) { ... } }`.
 *
 * @param {string} source
 * @param {string} name - имя watcher'а
 * @returns {string|null}
 */
const watcherBody = (source, name) => {
  const definition = new RegExp('\\n\\s*' + name + '\\s*:\\s*\\{').exec(source)
  if (!definition) return null
  const open = source.indexOf('{', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(open + 1, i)
    }
  }
  return null
}

/** Присваивания свойства компонента: `this.что-то = ...`. */
const PROPERTY_ASSIGNMENT = /this\.[A-Za-z_$][\w$]*\s*(?:=|\+=|-=|\+\+|--)(?!=)/g

describe('SubsEdit: computed не мутирует состояние (#194)', () => {
  test('метод getStringsForAllNotesInSong не присваивает свойства компонента', () => {
    const body = methodBody(SOURCE, 'getStringsForAllNotesInSong')
    assert.ok(body, 'метод getStringsForAllNotesInSong должен существовать в SubsEdit.vue')
    const assignments = body.match(PROPERTY_ASSIGNMENT)
    assert.equal(
      assignments === null ? 0 : assignments.length,
      0,
      'метод не должен мутировать состояние компонента (найдено: ' +
        JSON.stringify(assignments) +
        ')',
    )
  })

  test('computed lstIndexesTabsVariant не мутирует состояние', () => {
    const body = methodBody(SOURCE, 'lstIndexesTabsVariant')
    assert.ok(body, 'computed lstIndexesTabsVariant должен существовать в SubsEdit.vue')
    const assignments = body.match(PROPERTY_ASSIGNMENT)
    assert.equal(
      assignments === null ? 0 : assignments.length,
      0,
      'computed обязан быть чистым (найдено: ' + JSON.stringify(assignments) + ')',
    )
  })

  test('кламп индекса варианта живёт в watcher, а не в computed', () => {
    const watcher = watcherBody(SOURCE, 'lstIndexesTabsVariant')
    assert.ok(watcher, 'watcher lstIndexesTabsVariant должен существовать в SubsEdit.vue')
    assert.ok(
      /this\.indexTabsVariant\s*=\s*variants\.length\s*-\s*1/.test(watcher),
      'кламп индекса варианта строя должен оставаться в watcher: ' + watcher,
    )
  })
})
