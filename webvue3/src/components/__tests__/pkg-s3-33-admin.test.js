// Регрессионные тесты пакета PKG-S3-33 (аудит #336, OpenProject #405) — админка webvue3.
//
// Записи пакета в этом файле:
//   BR-444 — восстановление фильтра из «Истории» читает ключи по контракту бэкенда
//            (`is_sync`, `filter_audio_parent_id`, `flag_free`).
//   BR-445 — загрузка истории фильтров не зависит от успеха загрузки авторов; отказ виден.
//   BR-446 — отказ Sponsr-синхронизации не оставляет ПРЕДЫДУЩИЙ успешный результат.
//   BR-447 — «Активный премиум»: точное число (union) с бэкенда, а не max().
//   BR-448 — «Искать заново»/«Удалить результаты» показывают ошибку, а не ложный успех.
//   BR-464 — изменения флагов sync по одной сущности выполняются последовательно.
//   BR-465 — drill-down статистики очищает прежние события и показывает ошибку.
//   BR-466 — ошибки загрузки sync-сущностей и статуса автозапуска видны в UI.
//   BR-475 — главная страница: отказ операции показывается, счётчик ответа проверяется.
//   BR-476 — флаг authYmInProgress сбрасывается при любом исходе.
//   BR-477 — ошибка сохранения шаблона публикации не прячет редактор.
//
// Запуск: cd webvue3 && node --test src/components/__tests__/pkg-s3-33-admin.test.js

import { test, describe, beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { FakeXhr } from '../../test-utils/fakeXhr.js'

const read = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf-8')

const SONGS_FILTER_MODAL = read('../Songs/filter/SongsFilterModal.vue')
const SONGS_FILTER_STORE = read('../Songs/filter/store.js')
const SPONSR_STORE = read('../SponsrSync/store.js')
const SPONSR_PANEL = read('../SponsrSync/SponsrSyncPanel.vue')
const MONETIZATION_PANEL = read('../Stats/MonetizationPanel.vue')
const SEARCH_TEXT = read('../Songs/edit/SearchText.vue')
const SYNC_TABLE = read('../Sync/SyncTable.vue')
const SYNC_STORE = read('../Sync/store.js')
const STATS_STORE = read('../Stats/store.js')
const STATS_VIEW = read('../../views/StatsView.vue')
const USER_EVENTS_MODAL = read('../Stats/UserEventsModal.vue')
const SONG_EVENTS_MODAL = read('../Stats/SongEventsModal.vue')
const HOME_VIEW = read('../../views/HomeView.vue')
const PUBLISH_TEMPLATES_VIEW = read('../../views/PublishTemplatesView.vue')

/**
 * Подключает стор ДИНАМИЧЕСКИ.
 *
 * На старом коде (до правки пакета) сторы импортировали `../../lib/utils` БЕЗ
 * расширения — такой импорт не резолвится в Node ESM, и обычный статический import
 * уронил бы весь файл с «module not found». Динамический импорт обёрнут в catch,
 * поэтому на старом коде тест падает с AssertionError, а не с ошибкой загрузки.
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} default-экспорт модуля либо null.
 */
const importStore = async (specifier) => {
  try {
    const mod = await import(specifier)
    return mod.default || mod
  } catch (_e) {
    return null
  }
}

/**
 * Минимальный контекст Vuex для проверки действий стора.
 *
 * @param {Object} store — стор (state/mutations/actions).
 * @returns {Object} контекст с commit/dispatch.
 */
const createCtx = (store) => {
  const state = JSON.parse(JSON.stringify(store.state))
  const ctx = {
    state,
    commit: (name, payload) => {
      const mutation = store.mutations[name]
      assert.ok(mutation, `нет мутации ${name}`)
      mutation(state, payload)
    },
    dispatch: null,
  }
  ctx.dispatch = (name, payload) => {
    const action = store.actions[name]
    assert.ok(action, `нет действия ${name}`)
    return action(ctx, payload)
  }
  return ctx
}

describe('PKG-S3-33 / Админка webvue3', () => {
  beforeEach(() => {
    FakeXhr.install()
  })

  describe('BR-444 — восстановление фильтра из истории по контракту бэкенда', () => {
    test('ключ синхронизации читается как is_sync, а не filter_is_sync', () => {
      assert.match(
        SONGS_FILTER_MODAL,
        /args\['is_sync'\]/,
        'BR-444: SongsHistory пишет ключ is_sync — его и надо читать',
      )
      assert.doesNotMatch(
        SONGS_FILTER_MODAL,
        /args\['filter_is_sync'\]/,
        'BR-444: ключа filter_is_sync в истории нет — значение всегда было undefined',
      )
    })

    test('A-root и Free восстанавливаются из истории', () => {
      assert.match(
        SONGS_FILTER_MODAL,
        /args\['filter_audio_parent_id'\]/,
        'BR-444: filter_audio_parent_id пишется в историю и должен читаться',
      )
      assert.match(
        SONGS_FILTER_MODAL,
        /args\['flag_free'\]/,
        'BR-444: flag_free пишется в историю и должен читаться',
      )
    })

    test('все поля фильтра, которых нет в истории, сбрасываются явно', () => {
      assert.match(
        SONGS_FILTER_MODAL,
        /this\.songsFilterAssignmentStatus = ''/,
        'BR-444: поле, отсутствующее в historyArgs, сбрасывается явно',
      )
    })
  })

  describe('BR-445 — загрузка истории фильтров не завис��т от списка авторов', () => {
    test('история грузится отдельным вызовом, а не внутри then списка авторов', () => {
      const mountedStart = SONGS_FILTER_MODAL.indexOf('async mounted() {')
      assert.ok(mountedStart > 0, 'BR-445: у модалки фильтра должен быть mounted()')
      const mountedBlock = SONGS_FILTER_MODAL.slice(
        mountedStart,
        SONGS_FILTER_MODAL.indexOf('  methods: {', mountedStart),
      )
      assert.match(
        mountedBlock,
        /dispatch\('loadSongsHistory'\)/,
        'BR-445: история должна грузиться отдельным вызовом',
      )
      assert.doesNotMatch(
        mountedBlock,
        /songAuthorsPromise\.then/,
        'BR-445: загрузка истории не должна быть вложена в then() списка авторов',
      )
      assert.match(mountedBlock, /catch/, 'BR-445: отказ загрузки авторов должен обрабатываться')
    })

    test('в сторе есть состояние ошибки истории, а флаг снимается в finally', () => {
      assert.match(
        SONGS_FILTER_STORE,
        /songsHistoryError/,
        'BR-445: у истории фильтров должно быть состояние ошибки',
      )
      assert.match(
        SONGS_FILTER_STORE,
        /setSongsHistoryError/,
        'BR-445: мутация установки ошибки истории нужна',
      )
      assert.match(
        SONGS_FILTER_STORE,
        /loadSongsHistory\(ctx\)[\s\S]*?finally\(\(\) => \{[\s\S]*?setSongsHistoryIsLoading/,
        'BR-445: флаг загрузки истории обязан сниматься в finally',
      )
      assert.match(
        SONGS_FILTER_STORE,
        /loadSongsHistory\(ctx\)[\s\S]*?return promisedXMLHttpRequest/,
        'BR-445: действие загрузки истории обязано возвращать промис',
      )
    })
  })

  describe('BR-446 — отказ Sponsr-синхронизации не выглядит как успех', () => {
    test('в сторе есть отдельное состояние ошибки', () => {
      assert.match(
        SPONSR_STORE,
        /sponsrSyncError/,
        'BR-446: отказ транспорта должен отличаться от бизнес-ответа ok:false',
      )
      assert.match(
        SPONSR_STORE,
        /setSponsrSyncLastResult', null\)/,
        'BR-446: прежний успешный результат должен очищаться при отказе',
      )
    })

    test('панель показывает ошибку пользователю', () => {
      assert.match(
        SPONSR_PANEL,
        /getSponsrSyncError/,
        'BR-446: панель обязана читать состояние ошибки',
      )
      assert.match(SPONSR_PANEL, /v-if="error"/, 'BR-446: панель обязана показывать текст ошибки')
    })

    test('отказ запроса очищает результат и кладёт текст ошибки', async () => {
      const store = await importStore('../SponsrSync/store.js')
      assert.ok(store, 'BR-446: стор SponsrSync должен существовать')
      const ctx = createCtx(store)
      // Промис НЕ ждём до ответа: действие завершается только после ответа сервера.
      const promise = ctx.dispatch('runSponsrSync')
      const xhr = FakeXhr.last((x) => x.url === '/api/sponsrsync/run')
      assert.ok(xhr, 'BR-446: запрос синхронизации должен уйти')
      xhr.respond(500, 'boom')
      await promise
      await new Promise((resolve) => setTimeout(resolve, 20))
      assert.equal(
        ctx.state.sponsrSyncLastResult,
        null,
        'BR-446: после отказа прежний результат не должен оставаться на экране',
      )
      assert.ok(ctx.state.sponsrSyncError, 'BR-446: после отказа должен быть текст ошибки')
      assert.equal(
        ctx.state.sponsrSyncIsLoading,
        false,
        'BR-446: флаг загрузки обязан сниматься при отказе',
      )
    })
  })

  describe('BR-447 — «Активный премиум» — точное число, а не max()', () => {
    test('панель берёт точное число с бэкенда', () => {
      assert.match(
        MONETIZATION_PANEL,
        /activePremiumTotal/,
        'BR-447: панель должна брать точное число активных премиумов',
      )
      assert.doesNotMatch(
        MONETIZATION_PANEL,
        /верхняя\s*\n?\s*оценка/,
        'BR-447: комментарий про «верхнюю оценку» был НЕВЕРНЫМ (max — нижняя граница)',
      )
      assert.match(
        MONETIZATION_PANEL,
        /не менее/,
        'BR-447: без точного числа подпись должна говорить «не менее»',
      )
    })
  })

  describe('BR-448 — поиск текста: отказ виден, ложного успеха нет', () => {
    test('оба действия обёрнуты в try/catch', () => {
      assert.match(
        SEARCH_TEXT,
        /async doResearch\(engine\)[\s\S]*?try \{[\s\S]*?catch/,
        'BR-448: «Искать заново» обязано обрабатывать отказ',
      )
      assert.match(
        SEARCH_TEXT,
        /async doDeleteResults\(\)[\s\S]*?try \{[\s\S]*?catch/,
        'BR-448: «Удалить результаты поиска» обязано обрабатывать отказ',
      )
      assert.match(SEARCH_TEXT, /showError\(/, 'BR-448: отказ должен показываться пользователю')
    })
  })

  describe('BR-464 — флаги sync меняются последовательно по сущности', () => {
    test('в компоненте есть очередь изменений по сущности', () => {
      assert.match(
        SYNC_TABLE,
        /flagQueues/,
        'BR-464: изменения по сущности должны выстраиваться в очередь',
      )
      assert.match(
        SYNC_TABLE,
        /\.catch\(\(\) => \{\}\)/,
        'BR-464: отказ одного запроса не должен блокировать очередь следующих',
      )
    })
  })

  describe('BR-465 — drill-down статистики не показывает чужие события', () => {
    test('состояние очищается ДО запроса, ошибка кладётся в отдельное поле', () => {
      assert.match(
        STATS_STORE,
        /setStatsUserEventsError/,
        'BR-465: у drill-down по пользователю должно быть состояние ошибки',
      )
      assert.match(
        STATS_STORE,
        /setStatsSongEventsError/,
        'BR-465: у drill-down по песне должно быть состояние ошибки',
      )
      assert.match(
        STATS_STORE,
        /loadStatsUserEvents\(ctx[\s\S]*?setStatsUserEvents', \[\]\)/,
        'BR-465: прежние события пользователя очищаются ДО запроса',
      )
      assert.match(
        STATS_STORE,
        /loadStatsSongEvents\(ctx[\s\S]*?setStatsSongEvents', \[\]\)/,
        'BR-465: прежние события песни очищаются ДО запроса',
      )
    })

    test('модалки показывают ошибку, а не «нет событий»', () => {
      assert.match(
        USER_EVENTS_MODAL,
        /error/,
        'BR-465: модалка событий пользователя показывает ошибку',
      )
      assert.match(SONG_EVENTS_MODAL, /error/, 'BR-465: модалка событий песни показывает ошибку')
      assert.match(STATS_VIEW, /:error="userEventsError"/, 'BR-465: ошибка передаётся в модалку')
      assert.match(STATS_VIEW, /:error="songEventsError"/, 'BR-465: ошибка передаётся в модалку')
    })

    test('отказ drill-down очищает события и даёт текст ошибки', async () => {
      const store = await importStore('../Stats/store.js')
      assert.ok(store, 'BR-465: стор статистики должен существовать')
      const ctx = createCtx(store)
      ctx.commit('setStatsUserEvents', [{ id: 1 }])
      ctx.commit('setStatsUserEventsTotalCount', 7)
      const promise = ctx.dispatch('loadStatsUserEvents', { siteUserId: 5 })
      const xhr = FakeXhr.last((x) => x.url.includes('/api/stats/user-events'))
      assert.ok(xhr, 'BR-465: запрос drill-down должен уйти')
      assert.deepEqual(
        ctx.state.userEvents,
        [],
        'BR-465: прежние события другого пользователя должны быть очищены ДО запроса',
      )
      xhr.respond(500, 'boom')
      const ok = await promise
      assert.equal(ok, false, 'BR-465: действие обязано сообщить об отказе')
      assert.ok(ctx.state.userEventsError, 'BR-465: текст ошибки обязателен')
    })
  })

  describe('BR-466 — ошибки загрузки sync видны в UI', () => {
    test('у сущностей и статуса автозапуска есть состояния ошибки', () => {
      assert.match(SYNC_STORE, /entitiesError/, 'BR-466: нужен текст ошибки загрузки сущностей')
      assert.match(SYNC_STORE, /autoStatusError/, 'BR-466: нужен текст ошибки статуса автозапуска')
      assert.match(
        SYNC_TABLE,
        /getSyncEntitiesError/,
        'BR-466: таблица показывает ошибку загрузки сущностей',
      )
    })

    test('отказ загрузки сущностей кладёт ошибку в state и чистит список', async () => {
      const store = await importStore('../Sync/store.js')
      assert.ok(store, 'BR-466: стор Sync должен существовать')
      const ctx = createCtx(store)
      ctx.commit('setSyncEntities', [{ key: 'Song' }])
      const promise = ctx.dispatch('loadSyncEntitiesPromise')
      const xhr = FakeXhr.last((x) => x.url === '/api/sync/entities')
      assert.ok(xhr, 'BR-466: запрос сущностей должен уйти')
      xhr.respond(502, 'bad gateway')
      await assert.rejects(() => promise, 'BR-466: отказ пробрасывается вызывающему')
      assert.ok(ctx.state.entitiesError, 'BR-466: текст ошибки обязателен')
      assert.deepEqual(ctx.state.entities, [], 'BR-466: устаревший список не должен показываться')
    })

    test('отказ статуса автозапуска не даёт unhandled rejection, а даёт ошибку', async () => {
      const store = await importStore('../Sync/store.js')
      assert.ok(store, 'BR-466: стор Sync должен существовать')
      const ctx = createCtx(store)
      const promise = ctx.dispatch('loadSyncAutoStatusPromise')
      const xhr = FakeXhr.last((x) => x.url === '/api/sync/auto-status')
      assert.ok(xhr, 'BR-466: запрос статуса должен уйти')
      xhr.respond(500, 'boom')
      const result = await promise
      assert.equal(result, null, 'BR-466: отказ статуса не должен ронять монтирование')
      assert.ok(ctx.state.autoStatusError, 'BR-466: текст ошибки обязателен')
    })
  })

  describe('BR-475 — главная страница: отказ операции виден', () => {
    test('все цепочки dispatch(...).then(...) имеют catch', () => {
      const dispatches = HOME_VIEW.match(/dispatch\('[^']+'/g) || []
      assert.ok(dispatches.length > 10, 'BR-475: на главной странице много операций')
      const thenCount = (HOME_VIEW.match(/\.then\(/g) || []).length
      const catchCount = (HOME_VIEW.match(/\.catch\(/g) || []).length
      assert.ok(
        catchCount >= thenCount,
        `BR-475: цепочек .then() — ${thenCount}, а .catch() — ${catchCount}: каждая операция обязана ловить отказ`,
      )
    })

    test('ответ-счётчик проверяется по формату', () => {
      assert.match(
        HOME_VIEW,
        /isCounterResponse/,
        'BR-475: ответ-счётчик обязан проверяться по формату, а не «не равно 0»',
      )
    })
  })

  describe('BR-476 — флаг authYmInProgress не залипает', () => {
    test('флаг сбрасывается в finally у обеих команд', () => {
      assert.match(
        HOME_VIEW,
        /autorizeYMstartPromise[\s\S]*?finally\(\(\) => \{[\s\S]*?authYmInProgress = false/,
        'BR-476: «Auth YM 1» обязана сбрасывать флаг при любом исходе',
      )
      assert.match(
        HOME_VIEW,
        /autorizeYMstart2Promise[\s\S]*?finally\(\(\) => \{[\s\S]*?authYmInProgress = false/,
        'BR-476: «Auth YM 2» обязана сбрасывать флаг при любом исходе',
      )
      assert.match(
        HOME_VIEW,
        /autorizeYMstopPromise[\s\S]*?finally\(\(\) => \{[\s\S]*?authYmInProgress = false/,
        'BR-476: «Auth YM: Stop» обязана сбрасывать флаг при любом исходе',
      )
    })
  })

  describe('BR-477 — ошибка сохранения шаблона не прячет редактор', () => {
    test('ошибка сохранения — локальная (у карточки шаблона)', () => {
      assert.match(
        PUBLISH_TEMPLATES_VIEW,
        /tpl\.saveError/,
        'BR-477: ошибка сохранения обязана быть локальной для карточки шаблона',
      )
      assert.doesNotMatch(
        PUBLISH_TEMPLATES_VIEW,
        /this\.error = data\.error/,
        'BR-477: общий флаг error прячет весь редактор — его нельзя использовать для ошибки сохранения',
      )
      assert.match(
        PUBLISH_TEMPLATES_VIEW,
        /v-if="tpl\.saveError"/,
        'BR-477: текст ошибки должен показываться у карточки',
      )
    })
  })
})
