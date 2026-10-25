// Регрессионные тесты пакета PKG-S3-32 (аудит #336, OpenProject #404) — таблица песен.
//
// Записи пакета в этом файле:
//   BR-435 — страница песни считалась по исходному порядку, а `b-table` сортировал
//            строки сам: «страница» и «видимые строки» расходились.
//   BR-436 — массовые операции разбирали пустое тело ответа (`JSON.parse('')`), а
//            ветка «успех/ошибка» по телу была мёртвой.
//   BR-437 — ответ статусов назначений заменял всю карту без проверки актуальности.
//   BR-438 — полные проходы по каталогу с `filter`/`find` внутри цикла + дважды
//            отправлялся батч health-report.
//   BR-439 — в запрос уходили мёртвые `page`/`perPage` (эндпоинт их не принимает).
//   BR-440 — «Repair All» слал по одному запросу на песню без лимита параллелизма.
//   BR-441 — таймеры не очищались при уходе со страницы.
//   BR-442 — `threadId` собирался диалогом, но не передавался в action.
//
// Запуск: cd webvue3 && node --test src/components/Songs/__tests__/pkg-s3-32-songstable.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONGS_TABLE = stripComments(
  readFileSync(new URL('../SongsTable.vue', import.meta.url), 'utf-8'),
)
const SONGS_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))

describe('PKG-S3-32 / Таблица песен', () => {
  describe('BR-435 — страница считается по порядку таблицы', () => {
    test('в таблицу отдаётся отсортированный массив', () => {
      assert.match(
        SONGS_TABLE,
        /:items="sortedSongsDigests"/,
        'BR-435: таблица должна получать тот же массив, по которому считается страница',
      )
      assert.match(
        SONGS_TABLE,
        /sortedSongsDigests\(\)\s*\{\s*return sortTableItems\(/,
        'BR-435: порядок строк считается общим помощником',
      )
    })

    test('карта «id → страница» строится по отсортированному массиву', () => {
      assert.match(
        SONGS_TABLE,
        /songIdAndPageId\(\)\s*\{[\s\S]*buildSongIdAndPageId\(this\.sortedSongsDigests/,
        'BR-435: карта страниц строится по порядку таблицы',
      )
      assert.doesNotMatch(
        SONGS_TABLE,
        /Math\.floor\(i \/ this\.perPage\) \+ 1/,
        'BR-435: старая формула страницы по индексу в songsIds осталась',
      )
    })
  })

  describe('BR-436 — массовые операции не разбирают пустое тело', () => {
    test('нет JSON.parse тела массовой операции', () => {
      assert.doesNotMatch(
        SONGS_TABLE,
        /let response = JSON\.parse\(data\)/,
        'BR-436: тело пустое — разбирать его нельзя',
      )
    })

    test('результат показывается по факту 2xx, отказ — отдельным сообщением', () => {
      assert.match(
        SONGS_TABLE,
        /reportBulkOperation\(header, successBody, error\)/,
        'BR-436: единый показ результата массовой операции',
      )
      assert.match(
        SONGS_TABLE,
        /alertType: failed \? 'error' : 'info'/,
        'BR-436: отказ должен давать «ошибка», а не «успех»',
      )
    })

    test('у обновления хранилища есть catch', () => {
      assert.match(
        SONGS_TABLE,
        /collectStorePromise[\s\S]*\.catch\(/,
        'BR-436: отказ обновления хранилища должен обрабатываться',
      )
    })
  })

  describe('BR-437 — ответ статусов назначений проверяется на актуальность', () => {
    test('запрошенный набор id запоминается и передаётся в экшен', () => {
      assert.match(
        SONGS_TABLE,
        /reloadAssignmentStatus\(\)[\s\S]*assignmentStatusRequestIds = songIds/,
        'BR-437: набор запрошенных id должен запоминаться',
      )
      assert.match(
        SONGS_TABLE,
        /isCurrent: \(\) => this\.assignmentStatusRequestIds === songIds/,
        'BR-437: экшену передаётся проверка актуальности ответа',
      )
    })

    test('экшен применяет ответ только если он актуален', () => {
      assert.match(
        SONGS_TABLE,
        /dispatch\('loadAssignmentStatusBySongIds', \{[\s\S]*isCurrent/,
        'BR-437: проверка актуальности обязана уходить в экшен',
      )
    })
  })

  describe('BR-438 — обход каталога без квадратичной сложности', () => {
    test('есть карта «id → песня»', () => {
      assert.match(
        SONGS_TABLE,
        /songById\(\)\s*\{\s*return buildSongById\(this\.songsDigests\)/,
        'BR-438: карта «id → песня» строится один раз',
      )
    })

    test('в циклах нет filter/find по всему каталогу', () => {
      assert.doesNotMatch(
        SONGS_TABLE,
        /this\.songsDigests\.filter\(\(song\) => song\.id === songId\)/,
        'BR-438: filter по каталогу внутри цикла — источник квадратичной сложности',
      )
      assert.doesNotMatch(
        SONGS_TABLE,
        /this\.songsDigests\.find\(\(s\) => s\.id === songId\)/,
        'BR-438: find по каталогу внутри цикла — источник квадратичной сложности',
      )
    })

    test('батч health-report отправляется один раз на смену страницы', () => {
      const enqueueCalls = SONGS_TABLE.match(/_enqueueHrBatch\(this\./g) || []
      assert.equal(
        enqueueCalls.length,
        1,
        'BR-438: батч health-report должен отправляться один раз на смену страницы',
      )
    })
  })

  describe('BR-439 — мёртвые параметры пагинации убраны', () => {
    test('в запрос списка не уходят page/perPage', () => {
      assert.doesNotMatch(
        SONGS_TABLE,
        /dispatch\('loadSongsDigests', \{\s*page: this\.currentPage/,
        'BR-439: эндпоинт songsdigests не принимает page/perPage — их нельзя посылать',
      )
      assert.match(
        SONGS_TABLE,
        /dispatch\('loadSongsDigests', \{\s*\.\.\.this\.\$store\.getters\.getSongsFilter/,
        'BR-439: запрос списка должен содержать только фильтр',
      )
    })
  })

  describe('BR-440 — «Repair All» с лимитом параллелизма', () => {
    test('задана константа лимита', () => {
      assert.match(
        SONGS_TABLE,
        /const REPAIR_ALL_CONCURRENCY = \d+/,
        'BR-440: нужен явный лимит одновременных запросов',
      )
    })

    test('ремонт идёт очередью с лимитом и ждёт ответ', () => {
      assert.match(
        SONGS_TABLE,
        /async repairAllForCurrentPage\(\)[\s\S]*await this\.\$store\.dispatch\('repairAllPromise'/,
        'BR-440: запросы должны выполняться с ожиданием ответа',
      )
      assert.match(
        SONGS_TABLE,
        /Promise\.all\(Array\.from\(\{ length: concurrency \}, worker\)\)/,
        'BR-440: параллелизм должен быть ограничен очередью',
      )
      assert.doesNotMatch(
        SONGS_TABLE,
        /repairAllForCurrentPage\(\)\s*\{\s*for \(const songId of this\.songsIds\)/,
        'BR-440: старый цикл без лимита остался',
      )
    })

    test('прогресс и ошибки видны в UI', () => {
      assert.match(SONGS_TABLE, /repairAllInProgress/, 'BR-440: нужен прогресс')
      assert.match(SONGS_TABLE, /repairAllErrors/, 'BR-440: нужна сводка ошибок')
    })
  })

  describe('BR-441 — таймеры очищаются при уходе со страницы', () => {
    test('есть beforeUnmount с clearTimeout', () => {
      assert.match(
        SONGS_TABLE,
        /beforeUnmount\(\)\s*\{[\s\S]*clearTimeout\(this\.notifyBackendActivePageDebounce\)/,
        'BR-441: debounce-таймер активной страницы должен очищаться',
      )
      assert.match(
        SONGS_TABLE,
        /beforeUnmount\(\)\s*\{[\s\S]*clearTimeout\(this\.mountedNotifyTimer\)/,
        'BR-441: таймер монтирования должен очищаться',
      )
    })

    test('id таймеров сохраняются в data', () => {
      assert.match(
        SONGS_TABLE,
        /mountedNotifyTimer: undefined/,
        'BR-441: id таймера монтирования должен храниться',
      )
      assert.match(
        SONGS_TABLE,
        /this\.mountedNotifyTimer = setTimeout\(/,
        'BR-441: id таймера монтирования должен сохраняться',
      )
    })
  })

  describe('BR-442 — threadId передаётся в action', () => {
    test('SHEETSAGE и SYMLINKs передают threadId', () => {
      assert.match(
        SONGS_TABLE,
        /createSheetsageForAllPromise', \{\s*prior: result\.prior,\s*threadId: result\.threadId/,
        'BR-442: threadId обязан уходить в action SHEETSAGE',
      )
      assert.match(
        SONGS_TABLE,
        /createSymlinksForAllPromise', \{\s*prior: result\.prior,\s*threadId: result\.threadId/,
        'BR-442: threadId обязан уходить в action SYMLINKs',
      )
    })

    test('у SHEETSAGE отдельный ключ приоритета', () => {
      assert.match(
        SONGS_TABLE,
        /dispatch\('setLastPriorSheetsage'/,
        'BR-442: приоритет SHEETSAGE должен жить в отдельном ключе',
      )
      assert.match(
        SONGS_TABLE,
        /getLastPriorSheetsage/,
        'BR-442: диалог SHEETSAGE должен читать свой ключ приоритета',
      )
      assert.match(
        SONGS_STORE,
        /lastPriorSheetsage/,
        'BR-442: в сторе должен быть ключ lastPriorSheetsage',
      )
    })
  })
})
