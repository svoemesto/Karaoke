// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — словари.
//
// Запись пакета в этом файле: BR-384 (ключи фильтра не совпадают с контрактом бэкенда).
//
// ДО ПРАВКИ: агрегирующий геттер `getDictionariesFilter` отдавал ключи
// `filterDictName`/`filterDictValue`, а бэкенд `POST /api/dictionaries/list` принимает
// `dictName`/`dictValue`. Spring неизвестный параметр игнорировал, поэтому при смене
// «строк на страницу» активный фильтр не уходил на сервер: возвращался полный список,
// хотя поля фильтра (модалка закрыта) хранили значения.
//
// Запуск: cd webvue3 && node --test src/components/Dictionaries/__tests__/pkg-s3-31-dictionaries.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const DICTIONARIES_FILTER_STORE = stripComments(
  readFileSync(new URL('../filter/store.js', import.meta.url), 'utf-8'),
)
const DICTIONARIES_TABLE = stripComments(
  readFileSync(new URL('../DictionariesTable.vue', import.meta.url), 'utf-8'),
)

describe('PKG-S3-31 / Словари', () => {
  describe('BR-384 — ключи фильтра совпадают с контрактом бэкенда', () => {
    test('геттер отдаёт dictName/dictValue, а не filterDictName/filterDictValue', () => {
      // Проверка по тексту исходника: стор фильтров импортирует `lib/utils` без
      // расширения, поэтому его нельзя импортировать в node-тесте (Node ESM требует
      // расширение) — контракт проверяем статически.
      const getter = /getDictionariesFilter\(state\)\s*\{[\s\S]*?\n    \},/.exec(
        DICTIONARIES_FILTER_STORE,
      )
      assert.ok(getter, 'BR-384: не найден геттер getDictionariesFilter')
      assert.match(getter[0], /params\.dictName = /, 'BR-384: имя параметра — dictName')
      assert.match(getter[0], /params\.dictValue = /, 'BR-384: имя параметра — dictValue')
      assert.doesNotMatch(
        getter[0],
        /params\.filterDictName|params\.filterDictValue/,
        'BR-384: незнакомый параметр Spring игнорирует — фильтр не доходил до сервера',
      )
    })

    test('в исходнике стора нет старых имён параметров', () => {
      assert.doesNotMatch(
        DICTIONARIES_FILTER_STORE,
        /filterDictName|filterDictValue/,
        'BR-384: старые имена параметров фильтра должны быть заменены',
      )
    })

    test('после создания словаря таблица перезагружается с текущим фильтром', () => {
      const create = /\n    create\(\)\s*\{[\s\S]*?\n    \},/.exec(DICTIONARIES_TABLE)
      assert.ok(create, 'BR-384: не найден метод create')
      assert.doesNotMatch(
        create[0],
        /loadDictionariesDigests', \{\}/,
        'BR-384: перезагрузка без фильтра показывала полный список словарей',
      )
      assert.match(
        create[0],
        /getDictionariesFilter/,
        'BR-384: перезагрузка обязана нести текущий фильтр',
      )
    })
  })
})
