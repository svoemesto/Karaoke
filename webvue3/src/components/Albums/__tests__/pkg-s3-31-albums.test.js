// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — альбомы.
//
// Записи пакета в этом файле:
//   BR-363 — флаг загрузки дайджеста залипал при ошибке; промис не возвращался.
//   BR-364 — устаревший ответ перезаписывал свежий (побеждал последний ПРИШЕДШИЙ).
//   BR-365 — перезагрузка после создания/удаления альбома теряла фильтр.
//   BR-366 — фильтр писался на сервер на каждый `input` (POST на каждый символ).
//
// ДО ПРАВКИ (BR-363): `catch` только логировал, `albumsDigestIsLoading` оставался
// `true` — таблица «Альбомы» навсегда показывала «Loading...», выйти можно было
// только F5. Промис не возвращался, поэтому `await dispatch(...)` не ждал ответа.
//
// ДО ПРАВКИ (BR-364): любой ответ безусловно коммитил `setAlbumsDigests` — при двух
// перекрывающихся загрузках (применение фильтра A, затем B) поздний ответ A затирал B.
//
// ДО ПРАВКИ (BR-365): `loadAlbumsDigests({})` — сервер возвращал ВСЕ альбомы, а поля
// фильтра продолжали показывать прежние значения.
//
// ДО ПРАВКИ (BR-366): каждая мутация фильтра вызывала `setWebvueProp` — POST на
// каждый символ ввода, без обработки отказа.
//
// Запуск: cd webvue3 && node --test src/components/Albums/__tests__/pkg-s3-31-albums.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, existsSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const ALBUMS_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))
const ALBUMS_TABLE = stripComments(
  readFileSync(new URL('../AlbumsTable.vue', import.meta.url), 'utf-8'),
)
const ALBUMS_FILTER_STORE = stripComments(
  readFileSync(new URL('../filter/store.js', import.meta.url), 'utf-8'),
)
const ALBUMS_FILTER_MODAL = stripComments(
  readFileSync(new URL('../filter/AlbumsFilterModal.vue', import.meta.url), 'utf-8'),
)

/**
 * Достаёт тело мутации/экшена из раздела стора.
 *
 * @param {string} source — исходник стора.
 * @param {string} section — раздел ('mutations' | 'actions').
 * @param {string} name — имя мутации/экшена.
 * @returns {string|null} тело блока либо null.
 */
const memberBody = (source, section, name) => {
  const start = source.indexOf(`\n  ${section}: {`)
  if (start < 0) return null
  const end = source.indexOf('\n  },', start)
  const text = source.slice(start, end < 0 ? source.length : end)
  const definition = new RegExp('\\n\\s{2,6}(?:async\\s+)?' + name + '\\s*\\(').exec(text)
  if (!definition) return null
  let open = text.indexOf('(', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < text.length; i++) {
    if (text[i] === '(') depth++
    else if (text[i] === ')') {
      depth--
      if (depth === 0) {
        open = i + 1
        break
      }
    }
  }
  const brace = text.indexOf('{', open)
  if (brace < 0) return null
  depth = 0
  for (let i = brace; i < text.length; i++) {
    if (text[i] === '{') depth++
    else if (text[i] === '}') {
      depth--
      if (depth === 0) return text.slice(brace + 1, i)
    }
  }
  return null
}

describe('PKG-S3-31 / Альбомы', () => {
  describe('BR-363 — загрузка дайджеста: промис, finally, видимая ошибка', () => {
    test('помощник loadListWith снимает флаг загрузки при ошибке и показывает её', async () => {
      const moduleUrl = new URL('../../../lib/list-loader.js', import.meta.url)
      assert.ok(
        existsSync(moduleUrl),
        'BR-363: должен быть общий помощник загрузки списка lib/list-loader.js',
      )
      const { loadListWith } = await import(moduleUrl.href)
      const committed = []
      const generation = { next: () => 1, isCurrent: () => true, current: () => 1 }
      const result = await loadListWith({
        request: () => Promise.reject(new Error('HTTP 500')),
        commit: (type, payload) => committed.push([type, payload]),
        generation,
        requestOptions: { method: 'POST', url: '/api/albums/albumsdigests', params: {} },
        loadingCommit: 'setAlbumsDigestIsLoading',
        errorCommit: 'setAlbumsDigestsError',
        applyCommit: 'setAlbumsDigests',
        errorMessage: 'Не удалось загрузить список альбомов',
      })
      assert.equal(result.applied, false)
      const lastLoading = committed.filter(([type]) => type === 'setAlbumsDigestIsLoading').pop()
      assert.deepEqual(
        lastLoading,
        ['setAlbumsDigestIsLoading', false],
        'BR-363: флаг загрузки обязан сниматься при ошибке',
      )
      const errorCommits = committed.filter(([type]) => type === 'setAlbumsDigestsError')
      assert.ok(errorCommits.length >= 1, 'BR-363: ошибка обязана быть видна в UI')
      assert.match(
        errorCommits[errorCommits.length - 1][1],
        /HTTP 500/,
        'BR-363: в state попадает текст ошибки',
      )
    })

    test('action loadAlbumsDigests возвращает промис и использует поколение запроса', () => {
      const body = memberBody(ALBUMS_STORE, 'actions', 'loadAlbumsDigests')
      assert.ok(body, 'action loadAlbumsDigests не найден')
      assert.match(body, /loadListWith/, 'BR-363: action обязан отдавать промис')
      assert.match(body, /albumsDigestGeneration/, 'BR-364: нужен счётчик поколения запроса')
    })

    test('в сторе есть состояние ошибки загрузки', () => {
      assert.match(ALBUMS_STORE, /albumsDigestsError: ''/)
      assert.match(ALBUMS_STORE, /getAlbumsDigestsError\(state\)/)
    })
  })

  describe('BR-364 — устаревший ответ не затирает свежий', () => {
    test('поколение запроса отбрасывает поздний ответ прежнего фильтра', async () => {
      const moduleUrl = new URL('../../../lib/list-loader.js', import.meta.url)
      assert.ok(existsSync(moduleUrl), 'BR-364: нужен общий помощник загрузки списка')
      const { loadListWith } = await import(moduleUrl.href)
      const { createRequestGeneration } = await import(
        new URL('../../../lib/target-request-guard.js', import.meta.url).href
      )
      const committed = []
      const generation = createRequestGeneration()
      const transport = []
      const request = (options) =>
        new Promise((resolve, reject) => transport.push({ options, resolve, reject }))

      const a = loadListWith({
        request,
        commit: (type, payload) => committed.push([type, payload]),
        generation,
        requestOptions: { method: 'POST', url: '/api/albums/albumsdigests', params: { f: 'A' } },
        loadingCommit: 'setAlbumsDigestIsLoading',
        errorCommit: 'setAlbumsDigestsError',
        applyCommit: 'setAlbumsDigests',
        errorMessage: 'ошибка',
      })
      const b = loadListWith({
        request,
        commit: (type, payload) => committed.push([type, payload]),
        generation,
        requestOptions: { method: 'POST', url: '/api/albums/albumsdigests', params: { f: 'B' } },
        loadingCommit: 'setAlbumsDigestIsLoading',
        errorCommit: 'setAlbumsDigestsError',
        applyCommit: 'setAlbumsDigests',
        errorMessage: 'ошибка',
      })

      // B отвечает первым, A — позже.
      transport[1].resolve(JSON.stringify({ albumsDigests: [{ id: 2 }] }))
      await b
      transport[0].resolve(JSON.stringify({ albumsDigests: [{ id: 1 }] }))
      await a

      const applied = committed.filter(([type]) => type === 'setAlbumsDigests')
      assert.equal(applied.length, 1, 'BR-364: применён должен быть ровно один ответ')
      assert.equal(
        applied[0][1].albumsDigests[0].id,
        2,
        'BR-364: побеждает последний запрос, а не последний пришедший ответ',
      )
    })
  })

  describe('BR-365 — перезагрузка сохраняет фильтр', () => {
    test('создание и удаление альбома перезагружают с текущим фильтром', () => {
      const reloads = ALBUMS_TABLE.match(/loadAlbumsDigests/g) || []
      assert.ok(reloads.length >= 3, 'BR-365: в таблице есть перезагрузки дайджеста')
      assert.doesNotMatch(
        ALBUMS_TABLE,
        /loadAlbumsDigests', \{\}/,
        'BR-365: перезагрузка без фильтра показывала ВСЕ альбомы',
      )
      assert.match(
        ALBUMS_TABLE,
        /loadAlbumsDigests', \{\s*\.\.\.this\.\$store\.getters\.getAlbumsFilter/,
        'BR-365: перезагрузка обязана нести текущий фильтр',
      )
    })
  })

  describe('BR-366 — фильтр сохраняется по действию, а не на каждый ввод', () => {
    test('в мутациях фильтра нет сетевой записи', () => {
      const mutationsText = ALBUMS_FILTER_STORE.slice(
        ALBUMS_FILTER_STORE.indexOf('\n  mutations: {'),
        ALBUMS_FILTER_STORE.indexOf('\n  actions: {'),
      )
      assert.doesNotMatch(
        mutationsText,
        /setWebvueProp/,
        'BR-366: сетевая запись в мутации — POST на каждый символ ввода',
      )
    })

    test('есть actions persistAlbumsFilter и restoreAlbumsFilter', () => {
      assert.match(
        ALBUMS_FILTER_STORE,
        /persistAlbumsFilter\(ctx\)/,
        'BR-366: запись фильтра должна быть отдельным действием',
      )
      assert.match(
        ALBUMS_FILTER_STORE,
        /restoreAlbumsFilter\(ctx\)/,
        'BR-366: восстановление фильтра должно быть отдельным действием (без записи)',
      )
    })

    test('модалка сохраняет фильтр по кнопке «Применить» и восстанавливает без записи', () => {
      assert.match(
        ALBUMS_FILTER_MODAL,
        /dispatch\('persistAlbumsFilter'\)/,
        'BR-366: фильтр сохраняется по кнопке «Применить»',
      )
      assert.match(
        ALBUMS_FILTER_MODAL,
        /dispatch\('restoreAlbumsFilter'\)/,
        'BR-366: восстановление фильтра не должно писать его обратно на сервер',
      )
      assert.doesNotMatch(
        ALBUMS_FILTER_MODAL,
        /getWebvueProp\('albumsFilter/,
        'BR-366: модалка не читает значения напрямую — это делает store',
      )
    })
  })
})
