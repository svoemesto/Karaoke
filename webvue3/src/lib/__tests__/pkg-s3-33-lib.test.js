// Регрессионные тесты пакета PKG-S3-33 (аудит #336, OpenProject #405) — общие модули webvue3.
//
// Записи пакета в этом файле:
//   BR-481 — транспорт: конечный таймаут запроса + ontimeout/onabort (запрос, на который
//            сервер не ответил, больше не висит вечно).
//   BR-482 — запись настройки (setWebvueProp): дебаунс + сериализация по ключу, чтобы
//            сервер принял последнее значение, а не промежуточный префикс.
//   BR-463 — стенное время без зоны (datetime-local → 'YYYY-MM-DD HH:mm:ss' в TZ сервера).
//   BR-488 — dev-сервер Vite проксирует /api на karaoke-app.
//
// Модуль `datetime-wall-clock.js` появился в этом пакете: на старом коде файла нет,
// поэтому он подключается ДИНАМИЧЕСКИ через catch — иначе обычный статический import
// уронил бы весь файл с «module not found». На старом коде тест падает с
// AssertionError («модуль должен существовать»), а не с ошибкой загрузки модуля.
//
// Запуск: cd webvue3 && node --test src/lib/__tests__/pkg-s3-33-lib.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { FakeXhr } from '../../test-utils/fakeXhr.js'

const UTILS_SOURCE = readFileSync(new URL('../utils.js', import.meta.url), 'utf-8')
const VITE_SOURCE = readFileSync(new URL('../../../vite.config.js', import.meta.url), 'utf-8')

/**
 * Подключает модуль пакета; на старом коде возвращает null (тест падает с AssertionError).
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} модуль либо null.
 */
const importOrNull = async (specifier) => {
  try {
    return await import(specifier)
  } catch (_e) {
    return null
  }
}

/**
 * Ограничивает ожидание промиса.
 *
 * @param {Promise<any>} promise — ожидаемый промис.
 * @param {number} ms — предел ожидания, мс.
 * @returns {Promise<any>} результат промиса либо отказ по таймауту.
 */
const withTimeout = (promise, ms = 2000) =>
  Promise.race([
    promise,
    new Promise((_resolve, reject) => setTimeout(() => reject(new Error('timeout')), ms)),
  ])

describe('PKG-S3-33 / Транспорт и общие модули webvue3', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-481 — запрос не висит вечно', () => {
    test('задаётся конечный таймаут и обрабатываются ontimeout/onabort', () => {
      assert.match(
        UTILS_SOURCE,
        /xhr\.timeout\s*=/,
        'BR-481: транспорт обязан задавать конечный xhr.timeout',
      )
      assert.match(
        UTILS_SOURCE,
        /xhr\.ontimeout\s*=/,
        'BR-481: транспорт обязан обрабатывать ontimeout (иначе промис не завершается)',
      )
      assert.match(
        UTILS_SOURCE,
        /xhr\.onabort\s*=/,
        'BR-481: транспорт обязан обрабатывать onabort',
      )
    })

    test('есть createTimeoutError с именем TimeoutError', async () => {
      const utils = await importOrNull('../utils.js')
      assert.ok(utils, 'BR-481: webvue3/src/lib/utils.js должен существовать')
      assert.equal(
        typeof utils.createTimeoutError,
        'function',
        'BR-481: нужен createTimeoutError для понятного текста ошибки',
      )
      const error = utils.createTimeoutError(30000)
      assert.equal(error.name, 'TimeoutError', 'BR-481: таймаут должен отличаться от прочих ошибок')
      assert.ok(error.message.includes('30000'), 'BR-481: текст ошибки должен содержать предел')
    })

    test('запрос без ответа отклоняется по таймауту, а не остаётся pending', async () => {
      const utils = await importOrNull('../utils.js')
      assert.ok(utils, 'BR-481: webvue3/src/lib/utils.js должен существовать')
      const promise = utils.promisedXMLHttpRequest({ method: 'GET', url: '/api/ping' })
      await new Promise((resolve) => setTimeout(resolve, 10))
      const xhr = FakeXhr.last((x) => x.url === '/api/ping')
      assert.ok(xhr, 'BR-481: запрос должен быть отправлен')
      assert.ok(xhr.timeout > 0, 'BR-481: у запроса должен быть конечный таймаут')
      if (typeof xhr.ontimeout === 'function') {
        xhr.ontimeout()
      } else {
        assert.fail(
          'BR-481: у запроса нет обработчика ontimeout — таймаут не завершит промис (запрос висит вечно)',
        )
      }
      await assert.rejects(
        () => withTimeout(promise),
        (error) => error.name === 'TimeoutError',
        'BR-481: по таймауту промис обязан отклониться (иначе вызывающий ждёт вечно)',
      )
    })
  })

  describe('BR-482 — запись настройки не может «залипнуть» на префиксе', () => {
    test('в коде есть дебаунс и очередь записи по ключу', () => {
      assert.match(
        UTILS_SOURCE,
        /PROP_WRITE_DEBOUNCE_MS/,
        'BR-482: запись настройки обязана иметь дебаунс (иначе на каждый символ уходит POST)',
      )
      assert.match(
        UTILS_SOURCE,
        /propWriteQueue/,
        'BR-482: записи по одному ключу должны выстраиваться в очередь',
      )
    })

    test('серия быстрых изменений значения уходит ОДНИМ запросом с последним значением', async () => {
      const utils = await importOrNull('../utils.js')
      assert.ok(utils, 'BR-482: webvue3/src/lib/utils.js должен существовать')
      const values = ['П', 'Пе', 'Пес', 'Песн', 'Песня']
      const promises = values.map((value) => utils.setWebvueProp('', 'picturesFilterName', value))
      // Ждём дебаунс (300 мс) — запрос должен уйти уже после всех изменений.
      await new Promise((resolve) => setTimeout(resolve, 400))
      const sent = FakeXhr.all((x) => x.url === '/api/setwebvueprop')
      assert.equal(
        sent.length,
        1,
        'BR-482: пять быстрых изменений должны дать один POST, а не пять',
      )
      assert.equal(
        sent[0].params.value,
        'Песня',
        'BR-482: сервер должен получить последнее значение, а не промежуточный префикс',
      )
      // Отвечаем на запрос — иначе промисы останутся pending.
      sent[0].respond(200, 'ok')
      const results = await Promise.all(promises)
      assert.ok(
        results.every((ok) => ok === true),
        'BR-482: все вызывающие должны получить подтверждение записи',
      )
    })

    test('значение, пришедшее во время запроса, дописывается следующим (порядок сохранён)', async () => {
      const utils = await importOrNull('../utils.js')
      assert.ok(utils, 'BR-482: webvue3/src/lib/utils.js должен существовать')
      const first = utils.setWebvueProp('', 'picturesFilterName', 'Пес')
      await new Promise((resolve) => setTimeout(resolve, 400))
      const sentFirst = FakeXhr.all((x) => x.url === '/api/setwebvueprop')
      assert.equal(sentFirst.length, 1, 'BR-482: первый POST должен уйти')
      // Второе изменение приходит, пока первый запрос ещё в полёте.
      const second = utils.setWebvueProp('', 'picturesFilterName', 'Песня')
      await new Promise((resolve) => setTimeout(resolve, 400))
      const sentWhileInFlight = FakeXhr.all((x) => x.url === '/api/setwebvueprop')
      assert.equal(
        sentWhileInFlight.length,
        1,
        'BR-482: по одному ключу не должно быть двух запросов в полёте (порядок записи)',
      )
      // Первый запрос завершился — новое значение уходит следующим.
      sentFirst[0].respond(200, 'ok')
      await new Promise((resolve) => setTimeout(resolve, 20))
      const sentAll = FakeXhr.all((x) => x.url === '/api/setwebvueprop')
      assert.equal(
        sentAll.length,
        2,
        'BR-482: изменение во время полёта должно уйти вторым запросом, а не потеряться',
      )
      assert.equal(
        sentAll[1].params.value,
        'Песня',
        'BR-482: вторым запросом должно уйти новое значение',
      )
      sentAll[1].respond(200, 'ok')
      assert.equal(
        await withTimeout(first),
        true,
        'BR-482: первый вызов должен разрешиться успехом',
      )
      assert.equal(
        await withTimeout(second),
        true,
        'BR-482: второй вызов должен разрешиться успехом',
      )
    })
  })

  describe('BR-463 — стенное время без зоны', () => {
    test('модуль datetime-wall-clock существует и переводит мгновение в стенное время', async () => {
      const mod = await importOrNull('../datetime-wall-clock.js')
      assert.ok(mod, 'BR-463: webvue3/src/lib/datetime-wall-clock.js должен существовать')
      assert.equal(
        typeof mod.toServerWallClock,
        'function',
        'BR-463: нужен toServerWallClock (datetime-local → стенное время без зоны)',
      )
    })

    test('стенное время сервера не содержит зоны (UTC и МСК)', async () => {
      const mod = await importOrNull('../datetime-wall-clock.js')
      assert.ok(mod, 'BR-463: webvue3/src/lib/datetime-wall-clock.js должен существовать')
      const moment = new Date('2026-10-23T07:00:00.000Z')
      assert.equal(
        mod.formatServerWallClock(moment, 'UTC'),
        '2026-10-23 07:00:00',
        'BR-463: стенное время должно быть без суффикса Z',
      )
      assert.equal(
        mod.formatServerWallClock(moment, 'Europe/Moscow'),
        '2026-10-23 10:00:00',
        'BR-463: стенное время сервера (МСК) должно совпадать с колонкой timestamp без зоны',
      )
      assert.doesNotMatch(
        mod.formatServerWallClock(moment, 'Europe/Moscow'),
        /Z$/,
        'BR-463: строка фильтра не должна содержать признак зоны',
      )
    })

    test('пустое/некорректное значение даёт пустую строку, а не Invalid Date', async () => {
      const mod = await importOrNull('../datetime-wall-clock.js')
      assert.ok(mod, 'BR-463: webvue3/src/lib/datetime-wall-clock.js должен существовать')
      assert.equal(mod.toServerWallClock(''), '', 'BR-463: пустое значение — пустая строка')
      assert.equal(
        mod.toServerWallClock('не дата'),
        '',
        'BR-463: некорректное значение — пустая строка, а не "Invalid Date"',
      )
    })
  })

  describe('BR-488 — dev-сервер проксирует API на бэкенд', () => {
    test('в vite.config.js есть server.proxy с маршрутом /api', () => {
      assert.match(
        VITE_SOURCE,
        /server\s*:/,
        'BR-488: в webvue3/vite.config.js должен быть блок server',
      )
      assert.match(
        VITE_SOURCE,
        /proxy\s*:/,
        'BR-488: в webvue3/vite.config.js должен быть блок proxy',
      )
      assert.match(
        VITE_SOURCE,
        /['"]\/api['"]\s*:/,
        'BR-488: прокси должен вешать маршрут /api на karaoke-app',
      )
    })
  })
})
