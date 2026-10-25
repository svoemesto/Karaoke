// BR-300 / BR-315 / BR-321 (аудит #336, пакет PKG-S3-27, OpenProject #399): гонки запросов.
//
// ЧТО БЫЛО.
//   - BR-300 (`useSongSubscription.loadPrice`): модалка переиспользует один `priceInfo`, а
//     запрос не имел идентификатора — ответ по ПРЕДЫДУЩЕЙ песне применялся к текущей
//     (в шапке модалки — цена чужой песни, а оплата идёт по цене сервера).
//   - BR-315 (`useSiteSubscription.loadPrice`): ответ на первый тариф мог прийти последним —
//     в UI оставалась цена первого тарифа при выбранном втором.
//   - BR-321 (`songs/loadMoreSearchResults`): `search` и `loadMore` имели НЕЗАВИСИМЫЕ
//     счётчики — новая выдача не инвалидировала in-flight подгрузку, и page 2 старого
//     запроса дописывалась к результатам нового.
//
// ЧТО СТАЛО. Единый счётчик поколений: ответ применяется, только если он относится к
// последнему запросу.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { createRequestGuard } from '../requestGuard.js'
import { readSource } from './sourceContract.js'

describe('BR-300 / BR-315 / BR-321: latest-request guard', () => {
  test('первый запрос — текущий', () => {
    const guard = createRequestGuard()
    const id = guard.begin()
    assert.equal(guard.isCurrent(id), true)
  })

  test('ответ предыдущего запроса отбрасывается', () => {
    const guard = createRequestGuard()
    const first = guard.begin()
    const second = guard.begin()
    assert.equal(guard.isCurrent(first), false, 'устаревший ответ не применяется')
    assert.equal(guard.isCurrent(second), true)
  })

  test('invalidate() делает текущий ответ устаревшим', () => {
    const guard = createRequestGuard()
    const id = guard.begin()
    guard.invalidate()
    assert.equal(guard.isCurrent(id), false)
  })

  test('current() не меняет поколение (подгрузка принадлежит текущей выдаче)', () => {
    const guard = createRequestGuard()
    const id = guard.begin()
    assert.equal(guard.current(), id)
    assert.equal(guard.current(), id)
    assert.equal(guard.isCurrent(id), true)
  })

  test('новая выдача инвалидирует in-flight подгрузку', () => {
    const guard = createRequestGuard()
    const generation = guard.current() // подгрузка старой выдачи
    guard.begin() // пришёл новый поиск → новое поколение
    assert.equal(
      guard.isCurrent(generation),
      false,
      'ответ старой подгрузки не должен применяться к новой выдаче',
    )
  })

  test('три запроса подряд — актуальным остаётся только последний', () => {
    const guard = createRequestGuard()
    const ids = [guard.begin(), guard.begin(), guard.begin()]
    assert.equal(guard.isCurrent(ids[0]), false)
    assert.equal(guard.isCurrent(ids[1]), false)
    assert.equal(guard.isCurrent(ids[2]), true)
  })
})

describe('BR-300 / BR-315 / BR-321: контракт исходников (падает на старом коде)', () => {
  test('useSongSubscription отбрасывает устаревший ответ по цене', () => {
    const src = readSource('src/composables/useSongSubscription.js')
    assert.ok(src.includes('createRequestGuard'), 'нужен счётчик поколений')
    assert.ok(
      /if \(!priceGuard\.isCurrent\(requestId\)\) return/.test(src),
      'устаревший ответ по цене не должен трогать state',
    )
  })

  test('useSiteSubscription отбрасывает устаревший ответ по тарифу', () => {
    const src = readSource('src/composables/useSiteSubscription.js')
    assert.ok(src.includes('createRequestGuard'), 'нужен счётчик поколений')
    assert.ok(
      /if \(!priceGuard\.isCurrent\(requestId\)\) return/.test(src),
      'устаревший ответ по тарифу не должен трогать state',
    )
  })

  test('songs store: один счётчик поколения выдачи на search и loadMore', () => {
    const src = readSource('src/store/modules/songs.js')
    assert.ok(
      src.includes('createRequestGuard'),
      'нужен общий счётчик поколения выдачи',
    )
    assert.ok(
      !/latestSearchId/.test(src) && !/latestLoadMoreId/.test(src),
      'старые независимые счётчики должны быть убраны (BR-321)',
    )
    assert.ok(
      /const generation = searchGuard\.current\(\)/.test(src),
      'loadMore должен запоминать поколение на старте',
    )
    assert.ok(
      /if \(!searchGuard\.isCurrent\(generation\)\) return/.test(src),
      'устаревшая подгрузка не должна применяться к новой выдаче',
    )
    assert.ok(
      /commit\('setSearchPagination', \{ page: 1, hasMore: false, isLoadingMore: false \}\)/.test(
        src,
      ),
      'новый поиск синхронно сбрасывает пагинацию (кнопка «Загрузить ещё» не должна оставаться активной)',
    )
  })
})
