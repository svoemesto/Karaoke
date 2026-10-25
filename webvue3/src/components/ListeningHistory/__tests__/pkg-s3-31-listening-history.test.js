// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — история прослушиваний.
//
// Записи пакета в этом файле:
//   BR-382 — фильтры жили только в модалке и терялись при любой другой загрузке.
//   BR-383 — перекрывающиеся загрузки: выигрывал последний ПРИШЕДШИЙ ответ.
//
// ДО ПРАВКИ (BR-382): фильтры применялись одноразовым `dispatch` из модалки и нигде не
// сохранялись. Пагинация, «Обновить», смена БД и смена «строк на страницу» уходили
// БЕЗ фильтров — таблица молча показывала весь список. Плюс `ok()` не сбрасывал
// страницу: первая страница показывалась под номером прежней.
//
// ДО ПРАВКИ (BR-383): у загрузки не было ни отмены, ни поколения запроса — каждый ответ
// безусловно коммитил `listeningHistoryDigest`.
//
// Запуск: cd webvue3 && node --test src/components/ListeningHistory/__tests__/pkg-s3-31-listening-history.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const HISTORY_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))
const FILTER_MODAL = stripComments(
  readFileSync(new URL('../ListeningHistoryFilterModal.vue', import.meta.url), 'utf-8'),
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

describe('PKG-S3-31 / История прослушиваний', () => {
  describe('BR-382 — фильтры живут в Vuex и применяются ко всем загрузкам', () => {
    test('есть стор фильтров истории', () => {
      const filterStoreUrl = new URL('../filter/store.js', import.meta.url)
      assert.ok(
        existsSync(filterStoreUrl),
        'BR-382: должен быть стор фильтров webvue3/src/components/ListeningHistory/filter/store.js',
      )
    })

    test('геттер фильтра отдаёт имена параметров бэкенда', async () => {
      const filterStoreUrl = new URL('../filter/store.js', import.meta.url)
      assert.ok(
        existsSync(filterStoreUrl),
        'BR-382: нет стора фильтров (src/components/ListeningHistory/filter/store.js)',
      )
      const module = await import(filterStoreUrl.href)
      const getters = module.default.getters
      const params = getters.getListeningHistoryFilter({
        listeningHistoryFilterUserId: '42',
        listeningHistoryFilterSongId: '',
        listeningHistoryFilterLastPlayedFrom: '',
        listeningHistoryFilterLastPlayedTo: '',
      })
      assert.equal(params.filterUserId, 42)
      assert.equal('filterSongId' in params, false, 'пустой фильтр не передаётся')
      assert.equal('filterLastPlayedFrom' in params, false)
    })

    test('загрузка подмешивает фильтры из Vuex', () => {
      const body = memberBody(HISTORY_STORE, 'loadListeningHistoryDigest')
      assert.ok(body, 'BR-382: не найден action loadListeningHistoryDigest')
      assert.match(
        body,
        /getListeningHistoryFilter/,
        'BR-382: фильтры обязаны подмешиваться к параметрам загрузки',
      )
    })

    test('модалка пишет фильтры в Vuex и сбрасывает страницу на 1', () => {
      const body = memberBody(FILTER_MODAL, 'ok')
      assert.ok(body, 'BR-382: не найден метод ok модалки')
      assert.match(
        body,
        /setListeningHistoryFilterLastPlayedFrom/,
        'BR-382: даты фильтра приводятся к ISO и кладутся в Vuex',
      )
      assert.match(
        body,
        /setListeningHistoryTableCurrentPage', 1/,
        'BR-382: применение фильтра обязано сбрасывать страницу на 1',
      )
      assert.match(body, /loadListeningHistoryDigest/, 'BR-382: фильтр применяется загрузкой')
    })
  })

  describe('BR-383 — перекрывающиеся загрузки: побеждает последний запрос', () => {
    test('у загрузки есть поколение запроса', () => {
      assert.match(HISTORY_STORE, /listeningHistoryGeneration/)
      const body = memberBody(HISTORY_STORE, 'loadListeningHistoryDigest')
      assert.ok(body, 'BR-383: не найден action loadListeningHistoryDigest')
      assert.match(
        body,
        /listeningHistoryGeneration\.isCurrent\(ticket\)/,
        'BR-383: устаревший ответ не должен применяться',
      )
      assert.match(body, /finally/, 'BR-383: флаг загрузки снимается при любом исходе')
    })

    test('в сторе есть состояние ошибки загрузки', () => {
      assert.match(HISTORY_STORE, /listeningHistoryDigestError: ''/)
      assert.match(HISTORY_STORE, /getListeningHistoryDigestError\(state\)/)
    })
  })
})
