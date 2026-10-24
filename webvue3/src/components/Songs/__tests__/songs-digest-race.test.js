// Регрессионный тест на гонку ответов дайджеста песен (BR-093, аудит #336,
// пакет PKG-S2-17, OpenProject #380).
//
// ДО ПРАВКИ `loadSongsDigests` применял ЛЮБОЙ пришедший ответ: при двух перекрывающихся
// загрузках (применение фильтра + смена «Строк на странице») таблица показывала строки
// прежнего фильтра, `countRows`/`totalDuration` и все массовые операции считались по
// устаревшему набору id. Флаг `songsDigestIsLoading` снимал первый завершившийся запрос,
// а при ошибке не снимался вовсе (action не возвращал промис — `await dispatch(...)`
// завершался до ответа).
//
// Тест управляет порядком ответов через подставной транспорт и проверяет, что применяет
// только ВЛАДЕЛЕЦ последнего поколения.
//
// Запуск: cd webvue3 && node --test src/components/Songs/__tests__/songs-digest-race.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import { loadSongsDigestsWith } from '../store.js'
import { createRequestGeneration } from '../../../lib/target-request-guard.js'

function makeTransport() {
  const pending = []
  return {
    pending,
    request(request) {
      return new Promise((resolve, reject) => {
        pending.push({ request, resolve, reject })
      })
    },
  }
}

function makeCommitLog() {
  const log = []
  return {
    log,
    commit(type, payload) {
      log.push([type, payload])
    },
  }
}

const flush = () => new Promise((resolve) => setImmediate(resolve))

describe('дайджест песен: применяется только последний запрос (BR-093)', () => {
  test('поздний ответ прежнего фильтра не затирает новый', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    // Запрос A (тяжёлый фильтр) ушёл первым, запрос B — вторым.
    const a = loadSongsDigestsWith({
      request: transport.request,
      commit,
      generation,
      params: { filterSongName: 'старый' },
    })
    const b = loadSongsDigestsWith({
      request: transport.request,
      commit,
      generation,
      params: { filterSongName: 'новый' },
    })

    // B приходит первым, A — позже.
    transport.pending[1].resolve(JSON.stringify({ songsDigests: [{ id: 2 }], countRows: 1 }))
    await flush()
    transport.pending[0].resolve(JSON.stringify({ songsDigests: [{ id: 1 }], countRows: 99 }))
    await a
    await b

    const applied = log.filter(([type]) => type === 'updateSongsDigests')
    assert.equal(applied.length, 1, 'применён должен быть ровно один ответ')
    assert.equal(
      applied[0][1].songsDigests[0].id,
      2,
      'побеждает последний запрос, а не последний ответ',
    )
    assert.equal(applied[0][1].countRows, 1)
  })

  test('устаревший ответ не снимает флаг загрузки актуального', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const a = loadSongsDigestsWith({
      request: transport.request,
      commit,
      generation,
      params: {},
    })
    const b = loadSongsDigestsWith({
      request: transport.request,
      commit,
      generation,
      params: {},
    })

    transport.pending[0].resolve(JSON.stringify({ songsDigests: [], countRows: 0 }))
    await a

    const loadingAfterStale = log.filter(([type]) => type === 'setSongsDigestIsLoading').pop()
    assert.deepEqual(
      loadingAfterStale,
      ['setSongsDigestIsLoading', true],
      'пока актуальный запрос в полёте, таблица остаётся в состоянии загрузки',
    )

    transport.pending[1].resolve(JSON.stringify({ songsDigests: [], countRows: 0 }))
    await b
    const loadingAfterCurrent = log.filter(([type]) => type === 'setSongsDigestIsLoading').pop()
    assert.deepEqual(loadingAfterCurrent, ['setSongsDigestIsLoading', false])
  })

  test('ошибка снимает флаг загрузки и показывается пользователю', async () => {
    const transport = makeTransport()
    const { log, commit } = makeCommitLog()
    const generation = createRequestGeneration()

    const p = loadSongsDigestsWith({ request: transport.request, commit, generation, params: {} })
    transport.pending[0].reject(new Error('HTTP 500'))
    const result = await p

    assert.equal(result.applied, false)
    const lastLoading = log.filter(([type]) => type === 'setSongsDigestIsLoading').pop()
    assert.deepEqual(lastLoading, ['setSongsDigestIsLoading', false], 'busy не должен залипнуть')
    const errorCommit = log.find(([type]) => type === 'setSongsDigestsError')
    assert.ok(errorCommit, 'ошибка должна попадать в state, а не только в console.log')
    assert.match(errorCommit[1], /HTTP 500/)
  })

  test('action возвращает промис (await dispatch дожидается ответа)', () => {
    const transport = makeTransport()
    const { commit } = makeCommitLog()
    const generation = createRequestGeneration()
    const result = loadSongsDigestsWith({
      request: transport.request,
      commit,
      generation,
      params: {},
    })
    assert.equal(
      typeof result.then,
      'function',
      'без возврата промиса вызывающий не может дождаться данных',
    )
    transport.pending[0].resolve(JSON.stringify({ songsDigests: [], countRows: 0 }))
    return result
  })
})
