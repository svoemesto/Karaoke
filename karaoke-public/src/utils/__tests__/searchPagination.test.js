// BR-344 / BR-345 (аудит #336, пакет PKG-S3-27, OpenProject #399): пагинация поиска.
//
// BR-344 — ЧТО БЫЛО. Восстановление среза по URL работало только при изменении query
// (`watch '$route.query.page'` с `immediate: false`), а `onSearch()` для `?page>1` грузил
// ТОЛЬКО первую страницу (и опирался на несуществующий watch 'searchPagination.page').
// Итог при F5 на `?text=…&page=3`: загружена страница 1, в URL по-прежнему `page=3`.
// Дополнительно `restoreFromUrl()` для `targetPage <= 1` выходил сразу, ничего не сбрасывая.
//
// BR-345 — ЧТО БЫЛО. Один и тот же флаг `searchPaginationError` использовался и для ошибки
// первичного поиска, и для ошибки подгрузки. Ошибка первичного поиска показывала «Не удалось
// загрузить ещё», а «Повторить» вызывал onLoadMore() → guard `hasMore=false` → ничего не
// запрашивал, после чего страница показывала ложное «Ничего не найдено».
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  SEARCH_ERROR_SCOPE,
  needsSliceRestore,
  restorePlan,
  searchErrorScope,
  searchErrorText,
  urlPage,
} from '../searchPagination.js'
import { readCode } from './sourceContract.js'

describe('BR-344: восстановление среза из URL', () => {
  test('страница из URL', () => {
    assert.equal(urlPage({ page: '3' }), 3)
    assert.equal(urlPage({ page: 2 }), 2)
  })

  test('нет page / мусор — страница 1', () => {
    assert.equal(urlPage({}), 1)
    assert.equal(urlPage({ page: 'abc' }), 1)
    assert.equal(urlPage({ page: '0' }), 1)
    assert.equal(urlPage(null), 1)
  })

  test('восстановление нужно, когда целевая страница > 1 и отличается от текущей', () => {
    assert.equal(needsSliceRestore(3, 1), true)
    assert.equal(needsSliceRestore(3, 3), false)
    assert.equal(needsSliceRestore(1, 1), false)
  })

  test('план восстановления: первая страница + добираемые порции', () => {
    assert.deepEqual(restorePlan(3), { firstPage: 1, extraPages: 2 })
    assert.deepEqual(restorePlan(1), { firstPage: 1, extraPages: 0 })
    assert.deepEqual(restorePlan(0), { firstPage: 1, extraPages: 0 })
  })
})

describe('BR-345: ошибки поиска различаются по scope', () => {
  test('scope различается', () => {
    assert.equal(searchErrorScope(SEARCH_ERROR_SCOPE.PRIMARY), SEARCH_ERROR_SCOPE.PRIMARY)
    assert.equal(searchErrorScope(SEARCH_ERROR_SCOPE.LOAD_MORE), SEARCH_ERROR_SCOPE.LOAD_MORE)
    assert.equal(searchErrorScope('что-то ещё'), SEARCH_ERROR_SCOPE.PRIMARY)
  })

  test('тексты ошибок различаются', () => {
    assert.notEqual(
      searchErrorText(SEARCH_ERROR_SCOPE.PRIMARY),
      searchErrorText(SEARCH_ERROR_SCOPE.LOAD_MORE),
      'ошибка поиска и ошибка подгрузки должны читаться по-разному',
    )
  })
})

describe('BR-344 / BR-345: контракт исходников (падает на старом коде)', () => {
  test('SearchView: onSearch при page>1 восстанавливает срез целиком', () => {
    const src = readCode('src/views/SearchView.vue')
    const fn = src.slice(src.indexOf('    onSearch() {'), src.indexOf('    // BR-345: «Повторить»'))
    assert.ok(
      /if \(hasFilters && targetPage > 1\) \{\s*this\.restoreFromUrl\(\)/.test(fn),
      'при page>1 нужно восстанавливать срез, а не грузить только первую страницу (BR-344)',
    )
    assert.ok(
      !/Делается через watch 'searchPagination\.page'/.test(src),
      'комментарий ссылался на несуществующий watch — это и было причиной бага',
    )
  })

  test('SearchView: restoreFromUrl сбрасывает срез при targetPage <= 1', () => {
    const src = readCode('src/views/SearchView.vue')
    const fn = src.slice(
      src.indexOf('    async restoreFromUrl() {'),
      src.indexOf('  },\n}\n</script>'),
    )
    assert.ok(
      !/if \(targetPage <= 1\) return/.test(fn),
      'ранний return оставлял state на странице 2 при URL с page=1 (BR-344)',
    )
    assert.ok(fn.includes('restorePlan('), 'нужен план восстановления среза')
  })

  test('SearchView: отдельные флаги ошибок и разные кнопки «Повторить»', () => {
    const src = readCode('src/views/SearchView.vue')
    assert.ok(src.includes('retrySearch'), 'нужна отдельная кнопка повтора поиска')
    assert.ok(
      /this\.\$store\.commit\('songs\/setSearchError', false\)\s*\n\s*this\.onSearch\(\)/.test(src),
      'повтор поиска должен повторять ИМЕННО поиск (BR-345)',
    )
    const store = readCode('src/store/modules/songs.js')
    assert.ok(store.includes('setSearchError'), 'нужен отдельный флаг ошибки первичного поиска')
    assert.ok(
      /commit\('setSearchError', true\)/.test(store),
      'ошибка первичного поиска должна ставить свой флаг',
    )
    assert.ok(
      /commit\('setSearchResults', \[\]\)/.test(store),
      'при ошибке нового поиска прежние результаты не должны выдаваться за новые',
    )
  })
})
