// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — стор песен.
//
// Записи пакета в этом файле:
//   BR-094 — закрыто пакетом #380 (PKG-S2-17): тест ФИКСИРУЕТ инвариант, а не чинит
//            дефект заново (см. specs/380-pkg-s2-17/report.md, BR-093).
//   BR-095 — «Sync Ids»: поиск подстроки вместо сравнения по элементам списка.
//   BR-096 — смена признака sync: запрос в мутации, инверсия флага после ответа.
//   BR-097 — `POST /api/setwebvueprop` прямо из мутации, без обработки отказа.
//   BR-098 — отчёт Health Report предыдущей песни показывается под именем текущей.
//   BR-099 — action пишет state напрямую, минуя мутации (TOCTOU по `toSync`).
//
// ДО ПРАВКИ (BR-095): `getToSyncFromRest` искал подстроку в сыром теле ответа.
// Бэкенд отдаёт `List<String>` (JSON `["12","123"]`), поэтому `ids.indexOf("2")`
// находил `2` внутри `"12"` — песня 2 считалась «в очереди на sync», и клик по чужому
// флагу отправлял `remove` вместо `add`.
//
// ДО ПРАВКИ (BR-096): мутация `changeToSync` делала сетевой запрос и переключала
// `state.toSync` в `.then`. Два быстрых клика давали два одинаковых `dictAction`
// ('add'+'add') и два переключения флага — UI оставался противоположным серверу.
//
// ДО ПРАВКИ (BR-097): 14 мутаций `setLast*` писали `POST /api/setwebvueprop` прямо из
// мутации и без `.catch`: побочный эффект в мутации + unhandled rejection; в памяти
// значение новое, на сервере — старое.
//
// ДО ПРАВКИ (BR-098): `catch` только логировал, `currentSongHealthReports` не
// сбрасывался — модалка показывала отчёты ПРЕДЫДУЩЕЙ песни под именем текущей.
//
// ДО ПРАВКИ (BR-099): action `setCurrentSongId` писал `ctx.state.toSync = …` и
// `ctx.state.freeTimeSlots = …` напрямую, а проверка актуальности стояла ДО двух
// последовательных `await` — между проверкой и присвоением успевала выбраться другая
// песня (TOCTOU).
//
// Запуск: cd webvue3 && node --test src/components/Songs/__tests__/pkg-s3-31-songs-store.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const SONGS_STORE_RAW = readFileSync(new URL('../store.js', import.meta.url), 'utf-8')

// Комментарии вырезаются: иначе проверка «в коде нет такого вызова» ловила бы
// и описание дефекта в комментарии.
const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONGS_STORE = stripComments(SONGS_STORE_RAW)

/**
 * Достаёт тело мутации/экшена из нужного раздела стора.
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

/**
 * Текст раздела стора целиком.
 *
 * @param {string} source — исходник стора.
 * @param {string} section — раздел ('mutations' | 'actions').
 * @returns {string} текст раздела.
 */
const sectionText = (source, section) => {
  const start = source.indexOf(`\n  ${section}: {`)
  if (start < 0) return ''
  const end = source.indexOf('\n  },', start)
  return source.slice(start, end < 0 ? source.length : end)
}

describe('PKG-S3-31 / Songs store', () => {
  describe('BR-094 — дайджест песен: промис возвращается, флаг снимается при ошибке', () => {
    test('инвариант закрытого пакета #380 держится (регрессионная защита)', async () => {
      // BR-094 закрыт пакетом #380 (PKG-S2-17, OpenProject #380): правка BR-093 ввела
      // `loadSongsDigestsWith` — промис возвращается, флаг снимается в `finally`,
      // ошибка кладётся в state. Здесь инвариант фиксируется, чтобы следующий пакет
      // не откатил его обратно.
      const storeModule = await import('../store.js')
      assert.equal(
        typeof storeModule.loadSongsDigestsWith,
        'function',
        'BR-094: нужен помощник loadSongsDigestsWith (поколение запроса + finally)',
      )
      const committed = []
      const generation = { next: () => 1, isCurrent: () => true, current: () => 1 }
      const result = await storeModule.loadSongsDigestsWith({
        request: () => Promise.reject(new Error('HTTP 500')),
        commit: (type, payload) => committed.push([type, payload]),
        generation,
        params: {},
      })
      assert.equal(result.applied, false)
      const lastLoading = committed.filter(([type]) => type === 'setSongsDigestIsLoading').pop()
      assert.deepEqual(
        lastLoading,
        ['setSongsDigestIsLoading', false],
        'BR-094: при ошибке флаг загрузки обязан сниматься',
      )
      const errorCommit = committed.find(([type]) => type === 'setSongsDigestsError')
      assert.ok(errorCommit, 'BR-094: ошибка обязана быть видна в UI, а не только в консоли')
    })

    test('action loadSongsDigests использует помощник с поколением запроса', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'loadSongsDigests')
      assert.ok(body, 'action loadSongsDigests не найден')
      assert.match(body, /loadSongsDigestsWith/, 'BR-094: action обязан отдавать промис')
    })
  })

  describe('BR-095 — «Sync IIds»: сравнение по элементам списка, а не по подстроке', () => {
    test('функция songIdInSyncIds различает id 2 и «12»', async () => {
      const storeModule = await import('../store.js')
      assert.equal(
        typeof storeModule.songIdInSyncIds,
        'function',
        'BR-095: должна быть экспортирована чистая функция songIdInSyncIds',
      )
      const { songIdInSyncIds } = storeModule
      assert.equal(songIdInSyncIds('["12","123"]', 2), false, '«2» не должен находиться в «12»')
      assert.equal(songIdInSyncIds('["12","123"]', 12), true)
      assert.equal(songIdInSyncIds('["12","123"]', 123), true)
      assert.equal(songIdInSyncIds('[]', 12), false)
      assert.equal(songIdInSyncIds('[12,123]', '12'), true, 'числа и строки сравнимы')
      assert.equal(songIdInSyncIds('не json', 12), false, 'битый ответ — не «да»')
      assert.equal(songIdInSyncIds('["12"]', null), false)
    })

    test('в исходнике нет поиска подстроки по телу ответа', () => {
      assert.doesNotMatch(
        SONGS_STORE,
        /ids\.indexOf\(/,
        'BR-095: поиск подстроки в теле ответа даёт ложное «песня в очереди на sync»',
      )
    })

    test('getToSyncFromRest использует songIdInSyncIds', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'getToSyncFromRest')
      assert.ok(body, 'action getToSyncFromRest не найден')
      assert.match(body, /songIdInSyncIds/)
    })
  })

  describe('BR-096 — смена признака sync: запрос в action, состояние — по ответу сервера', () => {
    test('мутация changeToSync удалена — сети в мутациях нет', () => {
      const mutationsText = sectionText(SONGS_STORE, 'mutations')
      assert.doesNotMatch(
        mutationsText,
        /\n\s{4}changeToSync\s*\(/,
        'BR-096: мутация changeToSync обязана быть удалена (сеть — только в action)',
      )
      assert.doesNotMatch(
        mutationsText,
        /promisedXMLHttpRequest/,
        'BR-096: в мутациях не должно быть сетевых запросов',
      )
    })

    test('action changeToSync фиксирует действие, блокирует повтор и ставит флаг по ответу', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'changeToSync')
      assert.ok(body, 'action changeToSync не найден')
      assert.match(
        body,
        /toSyncRequestInFlight/,
        'BR-096: параллельный запрос должен блокироваться',
      )
      assert.match(body, /dictAction/, 'BR-096: действие уходит на сервер явным add/remove')
      assert.match(
        body,
        /commit\('setToSync'/,
        'BR-096: состояние ставится по подтверждённому сервером результату',
      )
      assert.match(body, /catch/, 'BR-096: отказ запроса должен обрабатываться')
    })

    test('есть мутация setToSync, присваивающая только состояние', () => {
      const body = memberBody(SONGS_STORE, 'mutations', 'setToSync')
      assert.ok(body, 'мутация setToSync не найдена')
      assert.match(body, /state\.toSync = value/)
      assert.doesNotMatch(body, /promisedXMLHttpRequest/)
    })
  })

  describe('BR-097 — запись настроек: без сети в мутациях, отказ обрабатывается', () => {
    test('в мутациях нет POST /api/setwebvueprop', () => {
      const mutationsText = sectionText(SONGS_STORE, 'mutations')
      assert.doesNotMatch(
        mutationsText,
        /url: '\/api\/setwebvueprop'/,
        'BR-097: сетевой запрос в мутации — нарушение контракта Vuex',
      )
    })

    test('в исходнике есть обработчик отказа persistWebvueProp', () => {
      const helper = /async function persistWebvueProp\([\s\S]*?\n}/.exec(SONGS_STORE)
      assert.ok(helper, 'BR-097: не найден helper persistWebvueProp')
      assert.match(
        helper[0],
        /catch/,
        'BR-097: отказ записи не должен уходить в unhandled rejection',
      )
    })

    test('восстановление настроек при старте не пишет их на сервер', () => {
      const appVue = readFileSync(new URL('../../../App.vue', import.meta.url), 'utf-8')
      assert.match(
        appVue,
        /dispatch\('restoreLastSettings'/,
        'BR-097: App.vue должен восстанавливать настройки отдельным действием',
      )
      assert.doesNotMatch(
        appVue,
        /dispatch\('setLastSettingType'/,
        'BR-097: восстановление не должно идти через setLast* (это лишние POST-записи)',
      )
      assert.match(
        SONGS_STORE,
        /restoreLastSettings\(state, values\)/,
        'BR-097: нужна мутация restoreLastSettings без записи на сервер',
      )
    })
  })

  describe('BR-098 — Health Report: отчёт другой песни не показывается под именем текущей', () => {
    test('перед запросом и при ошибке список очищается, ошибка попадает в state', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'setCurrentSongHealthReports')
      assert.ok(body, 'action setCurrentSongHealthReports не найден')
      assert.match(
        body,
        /commit\('setCurrentSongHealthReports', \[\]\)/,
        'BR-098: список отчётов обязан очищаться',
      )
      assert.match(
        body,
        /setCurrentSongHealthReportsError/,
        'BR-098: ошибка обязана попадать в state, чтобы UI отличал её от «нарушений нет»',
      )
      assert.match(body, /currentSongId === currId/)
    })

    test('в state есть признак ошибки и геттер', () => {
      assert.match(SONGS_STORE, /currentSongHealthReportsError: ''/)
      assert.match(SONGS_STORE, /getCurrentSongHealthReportsError\(state\)/)
    })
  })

  describe('BR-099 — состояние меняется только через мутации, актуальность проверяется', () => {
    test('action setCurrentSongId не пишет state напрямую', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'setCurrentSongId')
      assert.ok(body, 'action setCurrentSongId не найден')
      assert.doesNotMatch(
        body,
        /ctx\.state\.toSync\s*=/,
        'BR-099: прямая запись в state не видна подписчикам store.subscribe',
      )
      assert.doesNotMatch(body, /ctx\.state\.freeTimeSlots\s*=/)
      assert.doesNotMatch(body, /ctx\.state\.songPages\[.*\]\.splice\(/)
      assert.match(body, /commit\('replaceSongInPage'/)

      const syncBody = memberBody(SONGS_STORE, 'actions', 'applySongSyncState')
      assert.ok(syncBody, 'action applySongSyncState не найден')
      assert.match(syncBody, /commit\('setToSync'/)
      assert.match(syncBody, /commit\('setFreeTimeSlots'/)
    })

    test('после каждого await актуальность песни перепроверяется', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'applySongSyncState')
      assert.ok(body, 'action applySongSyncState не найден')
      const checks = body.match(/currId !== ctx\.state\.currentSongId/g) || []
      assert.ok(
        checks.length >= 2,
        'BR-099: проверка актуальности нужна после КАЖДОГО await (getdict и getfreetimeslots)',
      )
    })

    test('loadHealthReportBatch не пишет в объект песни напрямую', () => {
      const body = memberBody(SONGS_STORE, 'actions', 'loadHealthReportBatch')
      assert.ok(body, 'action loadHealthReportBatch не найден')
      assert.doesNotMatch(
        body,
        /song\.healthReportText\s*=/,
        'BR-099: запись в объект state вне мутации не видна подписчикам',
      )
      assert.match(body, /commit\('setSongHealthReportPending'/)
    })
  })
})
