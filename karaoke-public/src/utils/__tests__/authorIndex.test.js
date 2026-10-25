// BR-347 (аудит #336, пакет PKG-S3-28, OpenProject #400): индекс авторов Map.
//
// ЧТО БЫЛО. `SearchView.authorIdFor(name)` — линейный `find` по каталогу авторов на
// КАЖДУЮ строку выдачи (и дважды на строку): O(строк × тайлов) на каждый рендер.
//
// ЧТО СТАЛО. Каталог разворачивается в `Map<authorName, authorId>` (пересборка только
// при смене `authorTiles`), поиск — O(1).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { buildAuthorIndex, authorIdFromIndex } from '../authorIndex.js'
import { readCode } from './sourceContract.js'

describe('BR-347: индекс авторов', () => {
  test('имя автора → id за O(1)', () => {
    const index = buildAuthorIndex([
      { author: 'Аквариум', id: 12 },
      { author: 'Кино', id: 34 },
    ])
    assert.equal(authorIdFromIndex(index, 'Кино'), '34')
    assert.equal(authorIdFromIndex(index, 'Аквариум'), '12')
  })

  test('неизвестный автор → null (ссылка не рендерится)', () => {
    const index = buildAuthorIndex([{ author: 'Кино', id: 34 }])
    assert.equal(authorIdFromIndex(index, 'Неизвестный'), null)
    assert.equal(authorIdFromIndex(index, ''), null)
    assert.equal(authorIdFromIndex(index, null), null)
  })

  test('первый тайл с таким именем выигрывает (как у find)', () => {
    const index = buildAuthorIndex([
      { author: 'Кино', id: 1 },
      { author: 'Кино', id: 2 },
    ])
    assert.equal(authorIdFromIndex(index, 'Кино'), '1')
  })

  test('битые тайлы не ломают индекс', () => {
    const index = buildAuthorIndex([null, undefined, {}, { author: '', id: 5 }, { author: 'X' }])
    assert.equal(index.size, 0)
    assert.equal(authorIdFromIndex(index, 'X'), null)
  })

  test('пустой каталог → пустой индекс', () => {
    assert.equal(buildAuthorIndex([]).size, 0)
    assert.equal(buildAuthorIndex(null).size, 0)
    assert.equal(authorIdFromIndex(null, 'Кино'), null)
  })

  test('id приводится к строке (path-параметр роутера)', () => {
    const index = buildAuthorIndex([{ author: 'Кино', id: 34 }])
    assert.equal(typeof authorIdFromIndex(index, 'Кино'), 'string')
  })
})

describe('BR-347: контракт исходников (падает на старом коде)', () => {
  test('SearchView использует Map-индекс, а не линейный find', () => {
    const src = readCode('src/views/SearchView.vue')
    assert.ok(src.includes('buildAuthorIndex'), 'нужен индекс Map вместо find')
    assert.ok(!/authorTiles\.value\.find/.test(src), 'линейный find по каталогу авторов ушёл')
  })

  test('id автора резолвится один раз на строку (в computed searchRows)', () => {
    const src = readCode('src/views/SearchView.vue')
    assert.ok(src.includes('searchRows'), 'нужен список строк с resolved authorId')
    // В шаблоне id автора больше не вызывается методом — берётся готовое поле.
    const templateCalls = (src.match(/authorIdFor\(song\.author\)/g) || []).length
    assert.equal(templateCalls, 0, 'в шаблоне не должно быть вызовов authorIdFor()')
    assert.ok(src.includes('song.authorId'), 'шаблон берёт готовый song.authorId')
  })
})
