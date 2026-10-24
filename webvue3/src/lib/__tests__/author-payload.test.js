// Регрессионный тест на payload inline-сохранения sort_order автора (BR-370, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `AuthorsTable.onSortOrderChange` отправлял `{ fldName: 'sortOrder',
// fldValue: value, id }`. Бэкенд `POST /api/authors/updateauthor` (apisUpdateAuthor) —
// не generic-saver: `author`, `ymId`, `vkId`, `lastAlbumYm`, `lastAlbumVk`,
// `lastAlbumProcessed`, `watched`, `skip` объявлены `@RequestParam(required = true)`,
// а ключей `fldName`/`fldValue` он не знает; `sortOrder` в payload отсутствовал.
// Каждое изменение колонки Sort давало HTTP 400 и откат значения — сохранить
// sort_order из ячейки было нельзя никогда (FR-008/US3 спеки 307).
//
// Тест фиксирует контракт: payload содержит ВСЕ обязательные параметры бэкенда и
// несёт новое значение sortOrder.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/author-payload.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { buildAuthorUpdatePayload } from '../author-payload.js'

// Обязательные @RequestParam(required = true) у apisUpdateAuthor.
const REQUIRED = [
  'id',
  'author',
  'ymId',
  'vkId',
  'lastAlbumYm',
  'lastAlbumVk',
  'lastAlbumProcessed',
  'watched',
  'skip',
]

const ROW = {
  id: 7,
  author: 'Иванов',
  ymId: 'ym-1',
  vkId: 'vk-1',
  lastAlbumYm: '2020',
  lastAlbumVk: '2021',
  lastAlbumProcessed: '2020',
  watched: 1,
  skip: 0,
  sortOrder: 10,
}

describe('payload apisUpdateAuthor при inline-правке sortOrder (BR-370)', () => {
  test('содержит все обязательные параметры бэкенда', () => {
    const payload = buildAuthorUpdatePayload(ROW, { sortOrder: 100 })
    for (const key of REQUIRED) {
      assert.ok(key in payload, `в payload нет обязательного параметра ${key} — бэкенд ответит 400`)
    }
  })

  test('несёт новое значение sortOrder (прежний payload его вообще не передавал)', () => {
    const payload = buildAuthorUpdatePayload(ROW, { sortOrder: 100 })
    assert.equal(payload.sortOrder, 100)
  })

  test('не отправляет ключи generic-saver, которых бэкенд не знает', () => {
    const payload = buildAuthorUpdatePayload(ROW, { sortOrder: 1 })
    assert.ok(!('fldName' in payload), 'fldName не обрабатывается apisUpdateAuthor')
    assert.ok(!('fldValue' in payload), 'fldValue не обрабатывается apisUpdateAuthor')
  })

  test('boolean-поля приводятся к boolean, а не к 0/1 из БД', () => {
    const payload = buildAuthorUpdatePayload(ROW, { sortOrder: 0 })
    assert.equal(payload.watched, true)
    assert.equal(payload.skip, false)
  })
})
