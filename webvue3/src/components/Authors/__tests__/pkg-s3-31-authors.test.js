// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — авторы.
//
// Записи пакета в этом файле:
//   BR-367 — модалка алиасов отправляла полный снимок автора (lost update).
//   BR-368 — ответ «0» (автор не найден) считался успехом, модалка закрывалась.
//   BR-373 — флаг загрузки дайджеста авторов залипал при ошибке.
//   BR-374 — в модалке «Новый альбом?» показывался признак «Следить?».
//   BR-375 — ссылка VK Music открывалась по `ymId`, а не по `vkId`.
//   BR-376 — результат сохранения не проверялся, ошибка уходила только в консоль.
//   BR-377 — в запрос уходил полный снимок 15 полей (lost update).
//
// ДО ПРАВКИ (BR-367/BR-377): `POST /api/authors/updateauthor` — не patch: он
// перезаписывает все переданные поля. Модалки слали полный снимок из дайджеста, поэтому
// правки другого админа/синхронизации, сделанные, пока модалка была открыта, молча
// откатывались.
//
// ДО ПРАВКИ (BR-368): бэкенд отдаёт `0`, если автор не найден (HTTP 200). Ответ — строка,
// поэтому `result !== 0` было истинно всегда, и модалка закрывалась как при успехе.
//
// ДО ПРАВКИ (BR-374): `fldValue: item.watched` в поле «Новый альбом?».
//
// ДО ПРАВКИ (BR-375): `openVKMusicAuthor` проверял `item.ymId`.
//
// ДО ПРАВКИ (BR-376): `if (result !== 0)` — строка «0» всегда проходила проверку.
//
// Запуск: cd webvue3 && node --test src/components/Authors/__tests__/pkg-s3-31-authors.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const AUTHORS_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))
const AUTHORS_TABLE = stripComments(
  readFileSync(new URL('../AuthorsTable.vue', import.meta.url), 'utf-8'),
)
const ALIASES_MODAL = stripComments(
  readFileSync(new URL('../AuthorAliasesModal.vue', import.meta.url), 'utf-8'),
)

/**
 * Достаёт тело метода по имени.
 *
 * @param {string} source — исходник.
 * @param {string} name — имя метода.
 * @returns {string|null} тело метода либо null.
 */
const methodBody = (source, name) => {
  const definition = new RegExp('\\n\\s*(?:async\\s+)?' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(
    source,
  )
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

describe('PKG-S3-31 / Авторы', () => {
  describe('BR-367 — модалка алиасов не затирает чужие изменения', () => {
    test('перед сохранением автор перечитывается с сервера', () => {
      const body = methodBody(ALIASES_MODAL, 'save')
      assert.ok(body, 'BR-367: не найден метод save')
      assert.match(
        body,
        /loadOneRecordPromise/,
        'BR-367: перед сохранением автор обязан перечитываться с сервера',
      )
      assert.match(body, /const fresh = await/, 'BR-367: используются СВЕЖИЕ данные автора')
      assert.match(
        body,
        /if \(!fresh\)/,
        'BR-367: не удалось прочитать автора — не пишем (fail-closed)',
      )
    })

    test('payload строится из свежих данных, а не из снимка дайджеста', () => {
      const body = methodBody(ALIASES_MODAL, 'save')
      assert.ok(body, 'BR-367: не найден метод save')
      const payloadBlock = /const payload = \{[\s\S]*?\n        \}/.exec(body)
      assert.ok(payloadBlock, 'BR-367: не найден блок payload')
      assert.match(payloadBlock[0], /id: fresh\.id/)
      assert.match(payloadBlock[0], /author: fresh\.author/)
      assert.doesNotMatch(
        payloadBlock[0],
        /this\.author\./,
        'BR-367: payload из снимка дайджеста затирал чужие изменения',
      )
    })

    test('в сторе есть действие чтения одного автора', () => {
      assert.match(
        AUTHORS_STORE,
        /loadOneRecordPromise\(ctx, id\)/,
        'BR-367: нужен action чтения одного автора с сервера',
      )
      assert.match(
        AUTHORS_STORE,
        /params: \{ filterId: id \}/,
        'BR-367: имя параметра — filterId (контракт бэкенда)',
      )
    })
  })

  describe('BR-368 — ответ «0» не считается успехом', () => {
    test('модалка проверяет ответ и не закрывается при нуле', () => {
      const body = methodBody(ALIASES_MODAL, 'save')
      assert.ok(body, 'BR-368: не найден метод save')
      assert.match(
        body,
        /Number\(response\) === 0/,
        'BR-368: ответ «0» (автор не найден) обязан распознаваться',
      )
      assert.match(body, /this\.error =/, 'BR-368: пользователь должен видеть ошибку')
      assert.match(body, /finally \{/, 'BR-368: saving сбрасывается в finally')
    })

    test('в модалке есть поле ошибки и оно показывается', () => {
      assert.match(ALIASES_MODAL, /error: ''/)
      assert.match(ALIASES_MODAL, /v-if="error"/)
    })
  })

  describe('BR-373 — дайджест авторов: промис, finally, видимая ошибка', () => {
    test('action loadAuthorsDigests возвращает промис и снимает флаг при ошибке', () => {
      assert.match(
        AUTHORS_STORE,
        /loadListWith/,
        'BR-373: action обязан использовать общий помощник загрузки списка',
      )
      assert.match(AUTHORS_STORE, /authorsDigestGeneration/, 'BR-373: нужен счётчик поколения')
      assert.match(AUTHORS_STORE, /authorsDigestsError: ''/)
      assert.match(AUTHORS_STORE, /getAuthorsDigestsError\(state\)/)
    })
  })

  describe('BR-374 — «Новый альбом?» показывает haveNewAlbum', () => {
    test('значение поля берётся из haveNewAlbum, а не из watched', () => {
      const block = /fldName: 'haveNewAlbum'[\s\S]{0,400}?\n          \},/.exec(AUTHORS_TABLE)
      assert.ok(block, 'BR-374: не найдено поле «Новый альбом?»')
      assert.match(
        block[0],
        /fldValue: item\.haveNewAlbum/,
        'BR-374: показывался признак «Следить?», а не «Новый альбом?»',
      )
      assert.doesNotMatch(block[0], /fldValue: item\.watched/)
    })
  })

  describe('BR-375 — ссылка VK Music открывается по vkId', () => {
    test('проверяется item.vkId', () => {
      const body = methodBody(AUTHORS_TABLE, 'openVKMusicAuthor')
      assert.ok(body, 'BR-375: не найден метод openVKMusicAuthor')
      assert.match(body, /if \(item\.vkId\)/, 'BR-375: проверяться должен vkId')
      assert.doesNotMatch(body, /if \(item\.ymId\)/)
    })
  })

  describe('BR-376 — результат сохранения проверяется, ошибка видна', () => {
    test('сравнение ответа — строковое', () => {
      const body = methodBody(AUTHORS_TABLE, 'doChangeValue')
      assert.ok(body, 'BR-376: не найден метод doChangeValue')
      assert.match(
        body,
        /String\(result\) !== '0'/,
        'BR-376: ответ — строка, поэтому сравнение обязано быть строковым',
      )
      assert.doesNotMatch(body, /if \(result !== 0\)/, 'BR-376: «0»-строка проходила проверку')
    })

    test('ошибка показывается пользователю', () => {
      const body = methodBody(AUTHORS_TABLE, 'doChangeValue')
      assert.ok(body, 'BR-376: не найден метод doChangeValue')
      assert.match(body, /showAuthorError/, 'BR-376: отказ обязан быть виден пользователю')
      assert.match(AUTHORS_TABLE, /v-if="authorError"/, 'BR-376: ошибка показывается в таблице')
    })
  })

  describe('BR-377 — в запрос уходят только изменённые поля', () => {
    test('payload собирается как diff со снимком', () => {
      const body = methodBody(AUTHORS_TABLE, 'doChangeValue')
      assert.ok(body, 'BR-377: не найден метод doChangeValue')
      assert.match(body, /const snapshot = this\.authorEditSnapshot/, 'BR-377: нужен снимок')
      assert.match(
        body,
        /if \(author\[key\] !== snapshot\[key\]\) payload\[key\] = author\[key\]/,
        'BR-377: в payload идут только изменённые поля',
      )
      assert.match(
        body,
        /const payload = \{ id: author\.id \}/,
        'BR-377: id — обязательный параметр',
      )
    })

    test('снимок сохраняется при открытии модалки', () => {
      const body = methodBody(AUTHORS_TABLE, 'changeValue')
      assert.ok(body, 'BR-377: не найден метод changeValue')
      assert.match(body, /this\.authorEditSnapshot = \{ \.\.\.item \}/)
    })
  })
})
