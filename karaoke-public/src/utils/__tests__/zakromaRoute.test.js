// BR-353 / BR-355 / BR-357 (аудит #336, пакет PKG-S3-28, OpenProject #400):
// состояние страницы «Закромы» из маршрута.
//
// ЧТО БЫЛО. Прямой заход на `/zakroma/special-bucket` показывал сетку авторов; back-link
// в спец-корзине строил `/zakroma//albums` (несуществующий маршрут); «Повторить» после
// ошибки стрима уводил на страницу альбомов вместо повторного запуска; «Автор не найден»
// не показывался вовсе.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  ZAKROMA_SPECIAL_PATH,
  isAuthorRoute,
  isSpecialBucketPath,
  authorChosenForRoute,
  zakromaBackTarget,
  zakromaStreamParams,
  isAuthorMissing,
} from '../zakromaRoute.js'
import { readCode } from './sourceContract.js'

describe('BR-353: состояние из маршрута', () => {
  test('маршрут автора распознаётся по числовому id', () => {
    assert.equal(isAuthorRoute({ authorId: '12' }), true)
    assert.equal(isAuthorRoute({ authorId: 'abc' }), false)
    assert.equal(isAuthorRoute({}), false)
    assert.equal(isAuthorRoute(null), false)
  })

  test('спец-корзина — отдельный маршрут', () => {
    assert.equal(isSpecialBucketPath(ZAKROMA_SPECIAL_PATH), true)
    assert.equal(isSpecialBucketPath('/zakroma'), false)
  })

  test('authorChosen = автор ИЛИ спец-корзина', () => {
    assert.equal(authorChosenForRoute('/zakroma/12', { authorId: '12' }), true)
    assert.equal(authorChosenForRoute(ZAKROMA_SPECIAL_PATH, {}), true)
    assert.equal(authorChosenForRoute('/zakroma', {}), false)
  })
})

describe('BR-353: back-link в шапке', () => {
  test('спец-корзина → список авторов (НЕ /zakroma//albums)', () => {
    const target = zakromaBackTarget({
      authorChosen: true,
      specialBucketShown: true,
      selectedAuthorId: '',
    })
    assert.equal(target.to, '/zakroma')
  })

  test('автор → альбомы автора', () => {
    const target = zakromaBackTarget({
      authorChosen: true,
      specialBucketShown: false,
      selectedAuthorId: '12',
    })
    assert.equal(target.to, '/zakroma/12/albums')
  })

  test('пустой authorId → список авторов, а не несуществующий маршрут', () => {
    const target = zakromaBackTarget({
      authorChosen: true,
      specialBucketShown: false,
      selectedAuthorId: '',
    })
    assert.equal(target.to, '/zakroma')
    assert.ok(!target.to.includes('//'))
  })

  test('сетка авторов → без back-link', () => {
    assert.equal(zakromaBackTarget({ authorChosen: false, specialBucketShown: false }), null)
  })
})

describe('BR-355: повтор стрима', () => {
  test('параметры повтора совпадают с параметрами штатного старта', () => {
    const tile = { author: 'Кино', songCount: 2485 }
    const params = zakromaStreamParams(tile, null)
    assert.equal(params.author, 'Кино')
    assert.equal(params.expectedCount, 2485)
    assert.equal(params.force, true)
    assert.equal(params.albumId, null)
  })

  test('при фильтре по альбому авторский счётчик не подставляется', () => {
    const tile = { author: 'Кино', songCount: 2485 }
    const params = zakromaStreamParams(tile, 7)
    assert.equal(params.expectedCount, undefined)
    assert.equal(params.albumId, 7)
  })

  test('без тайла параметров нет', () => {
    assert.equal(zakromaStreamParams(null, null), null)
  })
})

describe('BR-357: автор не найден', () => {
  test('тайла нет, но каталог загружен — автор не найден', () => {
    assert.equal(isAuthorMissing(null, true), true)
  })

  test('каталог ещё не загружен — это не «автор не найден»', () => {
    assert.equal(isAuthorMissing(null, false), false)
  })

  test('тайл есть — автор найден', () => {
    assert.equal(isAuthorMissing({ author: 'Кино' }, true), false)
  })
})

describe('BR-353/BR-355/BR-357: контракт исходников (падает на старом коде)', () => {
  test('ZakromaView выводит состояние из маршрута и повторяет стрим по кнопке', () => {
    const src = readCode('src/views/ZakromaView.vue')
    assert.ok(src.includes('authorChosenForRoute'), 'нужен вывод authorChosen из маршрута')
    assert.ok(src.includes('zakromaBackTarget'), 'нужен back-link из состояния')
    assert.ok(src.includes('zakromaStreamParams'), 'нужны общие параметры запуска стрима')
    assert.ok(src.includes('authorNotFound'), 'нужно пустое состояние «автор не найден»')
  })
})
