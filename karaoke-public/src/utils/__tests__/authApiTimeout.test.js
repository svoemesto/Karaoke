// BR-339 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// таймаут XHR в authApi.
//
// ЧТО БЫЛО. `xhr.timeout` не выставлялся вообще: зависший запрос (сервер принял
// соединение и молчит) не отличим от долгой загрузки — `xhr.onerror` не срабатывает,
// промис не реджектится, `loading` у вызывающих компонентов крутится бесконечно.
// Именно поэтому PlaylistsView/PlaylistEditView «залипали» на «Загрузка...».
//
// ЧТО ПРОВЕРЯЕТСЯ. Таймаут выставлен на всех публичных функциях authApi, а его
// срабатывание даёт reject с отдельным кодом 'timeout' (не 'network_error'), чтобы
// UI мог написать «сервер не ответил вовремя».
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test, beforeEach } from 'node:test'
import assert from 'node:assert/strict'

import { authGet, authPost, authPostJson, authUpload } from '../../services/authApi.js'

// Минимальный XMLHttpRequest: запоминает экземпляр, ничего не отправляет.
class FakeXHR {
  constructor() {
    FakeXHR.instances.push(this)
    this.headers = {}
    this.upload = { onprogress: null }
  }
  open(method, url) {
    this.method = method
    this.url = url
  }
  setRequestHeader(name, value) {
    this.headers[name] = value
  }
  send() {
    this.sent = true
  }
  static last() {
    return FakeXHR.instances[FakeXHR.instances.length - 1]
  }
}

FakeXHR.instances = []

beforeEach(() => {
  FakeXHR.instances = []
  globalThis.XMLHttpRequest = FakeXHR
})

describe('BR-339: authApi задаёт xhr.timeout', () => {
  test('authGet выставляет таймаут', async () => {
    authGet('/api/public/x', 'token')
    await Promise.resolve()
    assert.equal(typeof FakeXHR.last().timeout, 'number')
    assert.ok(FakeXHR.last().timeout > 0, 'xhr.timeout должен быть задан')
  })

  test('authPost выставляет таймаут', async () => {
    authPost('/api/public/x', { a: 1 }, 'token')
    await Promise.resolve()
    assert.ok(FakeXHR.last().timeout > 0)
  })

  test('authPostJson выставляет таймаут', async () => {
    authPostJson('/api/public/x', { a: 1 }, 'token')
    await Promise.resolve()
    assert.ok(FakeXHR.last().timeout > 0)
  })

  test('authUpload (загрузка стема) выставляет более щедрый таймаут', async () => {
    authUpload('/api/public/upload', { size: 1 }, {}, 'token')
    await Promise.resolve()
    assert.ok(
      FakeXHR.last().timeout >= 60000,
      'загрузка аудио законно длится дольше обычного запроса',
    )
  })

  test('срабатывание таймаута — reject с кодом timeout, а не network_error', async () => {
    const promise = authGet('/api/public/x', 'token')
    await Promise.resolve()
    FakeXHR.last().ontimeout()
    await assert.rejects(promise, (err) => err.message === 'timeout')
  })

  test('регресс-контроль: сетевая ошибка по-прежнему network_error', async () => {
    const promise = authGet('/api/public/x', 'token')
    await Promise.resolve()
    FakeXHR.last().onerror()
    await assert.rejects(promise, (err) => err.message === 'network_error')
  })
})
