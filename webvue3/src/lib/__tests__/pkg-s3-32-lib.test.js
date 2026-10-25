// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — общие модули.
//
// Записи пакета в этом файле:
//   BR-395 — отмена запроса (AbortSignal) в транспорте `promisedXMLHttpRequest`.
//   BR-398 — CSV-экспорт: формульная инъекция (значение, начинающееся с `= + - @`).
//   BR-404 — клиентская валидация значения настройки по её типу.
//   BR-406 / BR-420 — разбор тела ответа сервера (`0L` / `false` — отказ).
//   BR-425 — разбор кода ошибки `POST /api/songeditor/edit/byId`.
//   BR-434 — word-level diff с пределом длины (без квадратичной LCS-матрицы).
//   BR-435 / BR-438 — порядок строк таблицы и карты «id песни → страница/песня».
//
// Модули, появившиеся в этом пакете, подключаются ДИНАМИЧЕСКИ: на старом коде файла
// нет, и обычный статический import уронил бы весь файл с «module not found».
// Динамический import обёрнут в catch, поэтому на старом коде тест падает с
// AssertionError («модуль должен существовать»), а не с ошибкой загрузки модуля.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/pkg-s3-32-lib.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

/**
 * Подключает модуль пакета; на старом коде возвращает null (тест падает с AssertionError).
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} модуль либо null.
 */
const importOrNull = async (specifier) => {
  try {
    return await import(specifier)
  } catch (_e) {
    return null
  }
}

const UTILS_SOURCE = readFileSync(new URL('../utils.js', import.meta.url), 'utf-8')

describe('PKG-S3-32 / Общие модули webvue3', () => {
  describe('BR-395 — транспорт умеет отменять запрос', () => {
    test('promisedXMLHttpRequest поддерживает obj.signal', () => {
      assert.match(
        UTILS_SOURCE,
        /obj\.signal/,
        'BR-395: транспорт должен поддерживать отмену запроса через AbortSignal',
      )
      assert.match(
        UTILS_SOURCE,
        /xhr\.abort\(\)/,
        'BR-395: при отмене запрос должен прерываться (xhr.abort)',
      )
    })

    test('отменённый запрос отклоняется с ошибкой AbortError', async () => {
      const utils = await importOrNull('../utils.js')
      assert.ok(utils, 'BR-395: webvue3/src/lib/utils.js должен существовать')
      assert.equal(typeof utils.createAbortError, 'function', 'BR-395: нужен createAbortError')
      const error = utils.createAbortError()
      assert.equal(error.name, 'AbortError', 'BR-395: отмена должна отличаться от ошибки')
    })
  })

  describe('BR-398 — CSV: формульная инъекция нейтрализуется', () => {
    test('модуль csv-export существует и экранирует формулы', async () => {
      const mod = await importOrNull('../csv-export.js')
      assert.ok(mod, 'BR-398: webvue3/src/lib/csv-export.js должен существовать')
      assert.equal(typeof mod.escapeCsvCell, 'function', 'BR-398: нужен escapeCsvCell')
    })

    test('значение с ведущим = + - @ не исполняется как формула', async () => {
      const mod = await importOrNull('../csv-export.js')
      assert.ok(mod, 'BR-398: webvue3/src/lib/csv-export.js должен существовать')
      for (const trigger of ['=', '+', '-', '@', '\t', '\r']) {
        const cell = mod.escapeCsvCell(`${trigger}cmd|' /C calc'!A1`)
        assert.ok(
          cell.startsWith("'"),
          `BR-398: значение с ведущим «${JSON.stringify(trigger)}» должно получать префикс-апостроф, получено «${cell}»`,
        )
      }
    })

    test('обычный текст и числа не меняются', async () => {
      const mod = await importOrNull('../csv-export.js')
      assert.ok(mod, 'BR-398: webvue3/src/lib/csv-export.js должен существовать')
      assert.equal(mod.escapeCsvCell('обычный текст'), 'обычный текст')
      assert.equal(mod.escapeCsvCell(42), '42')
      assert.equal(mod.escapeCsvCell(-1), '-1', 'BR-398: числа не префиксуются')
      assert.equal(mod.escapeCsvCell(null), '')
      assert.equal(mod.escapeCsvCell(undefined), '')
    })

    test('кавычки и запятые по-прежнему экранируются', async () => {
      const mod = await importOrNull('../csv-export.js')
      assert.ok(mod, 'BR-398: webvue3/src/lib/csv-export.js должен существовать')
      assert.equal(mod.escapeCsvCell('a,b'), '"a,b"')
      assert.equal(mod.escapeCsvCell('a"b'), '"a""b"')
    })
  })

  describe('BR-404 — значение настройки проверяется по типу', () => {
    test('модуль property-value существует', async () => {
      const mod = await importOrNull('../property-value.js')
      assert.ok(mod, 'BR-404: webvue3/src/lib/property-value.js должен существовать')
      assert.equal(typeof mod.validatePropertyValue, 'function')
    })

    test('Int/Long/Double отвергают нечисловой ввод', async () => {
      const mod = await importOrNull('../property-value.js')
      assert.ok(mod, 'BR-404: webvue3/src/lib/property-value.js должен существовать')
      assert.equal(mod.validatePropertyValue('Int', '20x4').valid, false)
      assert.equal(mod.validatePropertyValue('Long', 'abc').valid, false)
      assert.equal(mod.validatePropertyValue('Double', '12,5x').valid, false)
      assert.equal(mod.validatePropertyValue('Int', '2024').valid, true)
      assert.equal(mod.validatePropertyValue('Long', '-5').valid, true)
      assert.equal(mod.validatePropertyValue('Double', '12.5').valid, true)
      assert.equal(
        mod.validatePropertyValue('Int', '').valid,
        false,
        'BR-404: пустое число невалидно',
      )
    })

    test('Boolean принимает только разрешённые значения', async () => {
      const mod = await importOrNull('../property-value.js')
      assert.ok(mod, 'BR-404: webvue3/src/lib/property-value.js должен существовать')
      for (const value of ['true', 'false', '1', '0', 'yes', 'no', 'on', 'off']) {
        assert.equal(
          mod.validatePropertyValue('Boolean', value).valid,
          true,
          `BR-404: «${value}» — допустимое значение boolean`,
        )
      }
      assert.equal(mod.validatePropertyValue('Boolean', 'tru').valid, false)
      assert.equal(mod.validatePropertyValue('Boolean', 'да').valid, false)
    })

    test('строковый тип принимается как есть', async () => {
      const mod = await importOrNull('../property-value.js')
      assert.ok(mod, 'BR-404: webvue3/src/lib/property-value.js должен существовать')
      assert.equal(mod.validatePropertyValue('String', 'любой текст').valid, true)
      assert.equal(mod.validatePropertyValue(undefined, 'любой текст').valid, true)
    })
  })

  describe('BR-406 / BR-420 — тело ответа сервера разбирается', () => {
    test('модуль server-result существует', async () => {
      const mod = await importOrNull('../server-result.js')
      assert.ok(mod, 'BR-406: webvue3/src/lib/server-result.js должен существовать')
      assert.equal(typeof mod.parseServerId, 'function')
      assert.equal(typeof mod.parseServerFlag, 'function')
    })

    test('id=0 и false означают отказ', async () => {
      const mod = await importOrNull('../server-result.js')
      assert.ok(mod, 'BR-406: webvue3/src/lib/server-result.js должен существовать')
      assert.equal(mod.parseServerId('0'), 0, 'BR-406: id=0 — отказ')
      assert.equal(mod.parseServerId('7'), 7)
      assert.equal(mod.parseServerId(''), 0)
      assert.equal(mod.parseServerFlag('false'), false, 'BR-406: false — отказ')
      assert.equal(mod.parseServerFlag('true'), true)
      assert.equal(mod.parseServerFlag(''), false)
    })
  })

  describe('BR-425 — код ошибки загрузки редактора разбирается', () => {
    test('song_not_found_in_local_db даёт отдельный текст', async () => {
      const mod = await importOrNull('../song-editor-errors.js')
      assert.ok(mod, 'BR-425: webvue3/src/lib/song-editor-errors.js должен существовать')
      const text = mod.describeSongEditorLoadError({
        found: false,
        error: 'song_not_found_in_local_db',
      })
      assert.match(text, /sync/i, 'BR-425: для song_not_found_in_local_db нужен текст про sync')
      assert.doesNotMatch(
        text,
        /^Не удалось загрузить данные для редактирования\.$/,
        'BR-425: общий текст не должен выдаваться вместо отличимого кода',
      )
    })

    test('неизвестный код даёт общий текст (fallback)', async () => {
      const mod = await importOrNull('../song-editor-errors.js')
      assert.ok(mod, 'BR-425: webvue3/src/lib/song-editor-errors.js должен существовать')
      assert.equal(
        mod.describeSongEditorLoadError({ found: false, error: 'что-то ещё' }),
        'Не удалось загрузить данные для редактирования.',
      )
      assert.equal(
        mod.describeSongEditorLoadError(null),
        'Не удалось загрузить данные для редактирования.',
      )
    })
  })

  describe('BR-434 — diff текста не строит квадратичную матрицу', () => {
    test('модуль text-diff существует', async () => {
      const mod = await importOrNull('../text-diff.js')
      assert.ok(mod, 'BR-434: webvue3/src/lib/text-diff.js должен существовать')
      assert.equal(typeof mod.diffTokens, 'function')
    })

    test('общий префикс/суффикс вырезаются, differing-часть помечается', async () => {
      const mod = await importOrNull('../text-diff.js')
      assert.ok(mod, 'BR-434: webvue3/src/lib/text-diff.js должен существовать')
      const { tokens, truncated } = mod.diffTokens(['a', 'b', 'c'], ['a', 'X', 'c'])
      assert.equal(truncated, false)
      const added = tokens.filter((t) => t.type === 'added').map((t) => t.text)
      assert.deepEqual(added, ['X'], 'BR-434: добавленный токен должен подсвечиваться')
    })

    test('при превышении предела подсветка не вычисляется, результат показан целиком', async () => {
      const mod = await importOrNull('../text-diff.js')
      assert.ok(mod, 'BR-434: webvue3/src/lib/text-diff.js должен существовать')
      const a = Array.from({ length: 50 }, (_, i) => `a${i}`)
      const b = Array.from({ length: 50 }, (_, i) => `b${i}`)
      const { tokens, truncated } = mod.diffTokens(a, b, 10)
      assert.equal(truncated, true, 'BR-434: превышение предела должно быть признано')
      assert.equal(
        tokens.filter((t) => t.type === 'added').length,
        0,
        'BR-434: при превышении предела подсветки быть не должно',
      )
      assert.equal(tokens.length, b.length, 'BR-434: результат показан целиком')
    })

    test('buildDiffHtml возвращает html и признак усечения', async () => {
      const mod = await importOrNull('../text-diff.js')
      assert.ok(mod, 'BR-434: webvue3/src/lib/text-diff.js должен существовать')
      const { html, truncated } = mod.buildDiffHtml('кот', 'коты')
      assert.equal(truncated, false)
      assert.match(html, /ae-mark ae-added/, 'BR-434: добавленное помечается классом')
    })
  })

  describe('BR-435 / BR-438 — порядок строк и карты таблицы', () => {
    test('модуль table-sort существует', async () => {
      const mod = await importOrNull('../table-sort.js')
      assert.ok(mod, 'BR-435: webvue3/src/lib/table-sort.js должен существовать')
      assert.equal(typeof mod.sortTableItems, 'function')
    })

    test('сортировка по возрастанию и по убыванию', async () => {
      const mod = await importOrNull('../table-sort.js')
      assert.ok(mod, 'BR-435: webvue3/src/lib/table-sort.js должен существовать')
      const items = [
        { id: 1, year: 2020 },
        { id: 2, year: 2010 },
        { id: 3, year: 2030 },
      ]
      const asc = mod.sortTableItems(items, [{ key: 'year', order: 'asc' }], [{ key: 'year' }])
      assert.deepEqual(
        asc.map((s) => s.id),
        [2, 1, 3],
        'BR-435: сортировка по возрастанию',
      )
      const desc = mod.sortTableItems(items, [{ key: 'year', order: 'desc' }], [{ key: 'year' }])
      assert.deepEqual(
        desc.map((s) => s.id),
        [3, 1, 2],
        'BR-435: сортировка по убыванию',
      )
    })

    test('без sortBy порядок не меняется', async () => {
      const mod = await importOrNull('../table-sort.js')
      assert.ok(mod, 'BR-435: webvue3/src/lib/table-sort.js должен существовать')
      const items = [{ id: 1 }, { id: 2 }]
      assert.deepEqual(
        mod.sortTableItems(items, [], []).map((s) => s.id),
        [1, 2],
        'BR-435: без сортировки порядок исходный',
      )
    })

    test('карта «id → страница» строится по порядку таблицы', async () => {
      const mod = await importOrNull('../table-sort.js')
      assert.ok(mod, 'BR-435: webvue3/src/lib/table-sort.js должен существовать')
      const items = [{ id: 10 }, { id: 20 }, { id: 30 }]
      const map = mod.buildSongIdAndPageId(items, 2)
      assert.equal(map.get(10), 1)
      assert.equal(map.get(20), 1)
      assert.equal(map.get(30), 2)
    })

    test('карта «id → песня» строится одним проходом', async () => {
      const mod = await importOrNull('../table-sort.js')
      assert.ok(mod, 'BR-438: webvue3/src/lib/table-sort.js должен существовать')
      const map = mod.buildSongById([
        { id: 1, name: 'a' },
        { id: 2, name: 'b' },
      ])
      assert.equal(map.get(2).name, 'b')
      assert.equal(map.size, 2)
    })
  })
})
