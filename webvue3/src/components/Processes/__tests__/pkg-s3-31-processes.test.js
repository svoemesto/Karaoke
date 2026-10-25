// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — процессы.
//
// Запись пакета в этом файле: BR-394 (двойная загрузка при смене «строк на страницу»).
//
// ДО ПРАВКИ: `this.currentPage = 1` синхронно будил watcher `currentPage`, и тот
// отправлял `loadPage()` со СТАРЫМ perPage; после `setRowsPerPage` уходил второй запрос
// с новым limit. При обратном порядке ответов таблица показывала 50 строк при
// пагинаторе на 100, а первый запрос к тому же тянул лишний `fetchBulkSelectionIds`.
//
// Запуск: cd webvue3 && node --test src/components/Processes/__tests__/pkg-s3-31-processes.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const PROCESSES_TABLE = stripComments(
  readFileSync(new URL('../ProcessesTable.vue', import.meta.url), 'utf-8'),
)

/**
 * Достаёт тело блока по имени.
 *
 * @param {string} source — исходник.
 * @param {string} name — имя блока.
 * @returns {string|null} тело блока либо null.
 */
const memberBody = (source, name) => {
  const definition = new RegExp('\\n\\s{2,6}(?:async\\s+)?' + name + '\\s*\\(').exec(source)
  if (!definition) return null
  let open = source.indexOf('(', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '(') depth++
    else if (source[i] === ')') {
      depth--
      if (depth === 0) {
        open = i + 1
        break
      }
    }
  }
  const brace = source.indexOf('{', open)
  if (brace < 0) return null
  depth = 0
  for (let i = brace; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(brace + 1, i)
    }
  }
  return null
}

describe('PKG-S3-31 / Процессы', () => {
  describe('BR-394 — смена «строк на страницу» грузит страницу один раз', () => {
    test('программный сброс страницы помечается флагом', () => {
      const body = memberBody(PROCESSES_TABLE, 'onPerPageChange')
      assert.ok(body, 'BR-394: не найден метод onPerPageChange')
      assert.match(
        body,
        /this\.skipNextPageLoad = true/,
        'BR-394: программный сброс страницы не должен порождать вторую загрузку',
      )
      assert.match(body, /this\.currentPage = 1/, 'BR-394: страница сбрасывается на 1')
    })

    test('watcher currentPage уважает флаг', () => {
      const watcher = /currentPage:\s*\{[\s\S]*?\n    \},/.exec(PROCESSES_TABLE)
      assert.ok(watcher, 'BR-394: не найден watcher currentPage')
      assert.match(
        watcher[0],
        /if \(this\.skipNextPageLoad\)[\s\S]*?return/,
        'BR-394: watcher обязан пропускать программный сброс',
      )
      assert.match(watcher[0], /this\.loadPage\(\)/, 'BR-394: обычная смена страницы грузит данные')
    })

    test('флаг объявлен в data', () => {
      assert.match(PROCESSES_TABLE, /skipNextPageLoad: false/)
    })
  })
})
