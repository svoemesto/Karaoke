// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — картинки.
//
// Записи пакета в этом файле:
//   BR-390 — таблица картинок не загружала дайджест при открытии раздела.
//   BR-391 — при ошибке загрузки флаг загрузки залипал навсегда.
//
// ДО ПРАВКИ (BR-390): `mounted()` грузил только настройки строк, а вызов загрузки
// дайджеста был закомментирован. Единственная загрузка была в модалке фильтра, поэтому
// при первом открытии раздела (и после каждого F5) таблица показывала пустой список —
// ложное «нет данных» без спиннера и без подсказки «задайте фильтр».
//
// ДО ПРАВКИ (BR-391): `catch` только логировал, `picturesDigestIsLoading` оставался
// `true` — таблица навсегда показывала «Loading...», ошибки пользователю не было.
//
// Запуск: cd webvue3 && node --test src/components/Pictures/__tests__/pkg-s3-31-pictures.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const PICTURES_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))
const PICTURES_TABLE = stripComments(
  readFileSync(new URL('../PicturesTable.vue', import.meta.url), 'utf-8'),
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

describe('PKG-S3-31 / Картинки', () => {
  describe('BR-390 — дайджест загружается при открытии раздела', () => {
    test('mounted вызывает loadPicturesDigests', () => {
      const mounted = memberBody(PICTURES_TABLE, 'mounted')
      assert.ok(mounted, 'BR-390: не найден хук mounted')
      assert.match(
        mounted,
        /loadPicturesDigests/,
        'BR-390: раздел «Картинки» обязан загружать дайджест при открытии',
      )
    })

    test('закомментированного вызова загрузки не осталось', () => {
      assert.doesNotMatch(
        PICTURES_TABLE,
        /\/\/ this\.\$store\.dispatch\('loadPicturesDigests'/,
        'BR-390: вызов загрузки остался закомментированным',
      )
    })
  })

  describe('BR-391 — ошибка загрузки не оставляет вечный спиннер', () => {
    test('action возвращает промис, флаг снимается в finally, ошибка видна', () => {
      const body = memberBody(PICTURES_STORE, 'loadPicturesDigests')
      assert.ok(body, 'BR-391: не найден action loadPicturesDigests')
      assert.match(body, /loadListWith/, 'BR-391: action обязан отдавать промис')
      assert.match(body, /picturesDigestGeneration/, 'BR-391: нужен счётчик поколения')
    })

    test('в сторе есть состояние ошибки загрузки', () => {
      assert.match(PICTURES_STORE, /picturesDigestsError: ''/)
      assert.match(PICTURES_STORE, /getPicturesDigestsError\(state\)/)
    })
  })
})
