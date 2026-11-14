// KARAOKE-400: юнит-тесты хелперов «Временного полного доступа к песне».
//
// ЧТО ПОКРЫВАЕТСЯ. `src/services/songShareLink.js` — код зоны безопасности:
// анонимный claim по секрету из URL (обмен секрета на sessionTokenHash),
// продление lease heartbeat'ом и освобождение сессии. Модуля не было ни в
// одном tests-каталоге публичного SPA (каталога services/__tests__ не
// существовало), поэтому контракты сетевого слоя не проверялись ничем.
//
// ЧТО ВАЖНО. Сетевой слой модуля сделан на `XMLHttpRequest`, а не на `fetch` —
// поэтому подменяется именно XHR (подмена `fetch` не перехватит ничего:
// см. knowledge/system/agent-mechanics-cases.md, прецедент KARAOKE-400).
// Продовый код не менялся.
//
// ОКРУЖЕНИЕ. `globalThis.navigator` и `globalThis.crypto` в Node 21+ — геттеры
// без сеттера, поэтому подменяются через `Object.defineProperty` (присваивание
// падает в строгом режиме ES-модуля). Раннер Node выполняет соседние
// describe-блоки параллельно, а мок с состоянием общий — поэтому у блоков
// `{ concurrency: 1 }`, а состояние мока сбрасывается в beforeEach.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test, beforeEach, afterEach } from 'node:test'
import assert from 'node:assert/strict'

import {
  getBrowserId,
  getBrowserHash,
  sha256Hex,
  claimShare,
  heartbeat,
  release,
} from '../songShareLink.js'

const STORAGE_KEY = 'km_share_browser_id'
const TIMEOUT = { timeout: 5000 }

// Минимальный XMLHttpRequest: запоминает экземпляр, ничего не отправляет.
class FakeXHR {
  constructor() {
    FakeXHR.instances.push(this)
    this.headers = {}
    this.status = 0
    this.response = ''
    this.payload = null
    this.sent = false
  }

  open(method, url, async) {
    this.method = method
    this.url = url
    this.async = async
  }

  setRequestHeader(name, value) {
    this.headers[name] = value
  }

  send(payload) {
    this.sent = true
    this.payload = payload
  }

  /** Имитация ответа сервера: status + тело. */
  respond(status, body) {
    this.status = status
    this.response = typeof body === 'string' ? body : JSON.stringify(body)
    this.onload()
  }

  /** Имитация сетевого отказа. */
  fail() {
    this.onerror()
  }

  static last() {
    return FakeXHR.instances[FakeXHR.instances.length - 1]
  }
}

FakeXHR.instances = []

function fakeStorage(initial = {}) {
  const data = new Map(Object.entries(initial))
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => {
      data.set(key, String(value))
    },
    removeItem: (key) => {
      data.delete(key)
    },
    clear: () => data.clear(),
    data,
  }
}

const digestCalls = []

/** Подмена Web Crypto: предсказуемый id и предсказуемый дайджест. */
function fakeCrypto({ id = 'browser-id-1', digestByte = 0xab } = {}) {
  return {
    randomUUID: () => id,
    subtle: {
      digest: async (algorithm, data) => {
        digestCalls.push({ algorithm, text: new TextDecoder().decode(data) })
        return new Uint8Array(32).fill(digestByte).buffer
      },
    },
  }
}

function setGlobal(name, value) {
  Object.defineProperty(globalThis, name, { value, configurable: true, writable: true })
}

const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
const originalCrypto = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
const originalLocalStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
const originalXHR = globalThis.XMLHttpRequest

let storage

/** Промис-микроtask-разрядка: модуль ждёт digest перед созданием XHR. */
const flush = () => new Promise((resolve) => setTimeout(resolve, 0))

beforeEach(() => {
  FakeXHR.instances = []
  digestCalls.length = 0
  storage = fakeStorage()
  setGlobal('localStorage', storage)
  setGlobal('crypto', fakeCrypto())
  setGlobal('XMLHttpRequest', FakeXHR)
  setGlobal('navigator', {})
})

afterEach(() => {
  if (originalNavigator) Object.defineProperty(globalThis, 'navigator', originalNavigator)
  if (originalCrypto) Object.defineProperty(globalThis, 'crypto', originalCrypto)
  if (originalLocalStorage) {
    Object.defineProperty(globalThis, 'localStorage', originalLocalStorage)
  } else {
    delete globalThis.localStorage
  }
  globalThis.XMLHttpRequest = originalXHR
})

describe('KARAOKE-400: идентификатор устройства', { concurrency: 1 }, () => {
  test('browserId стабилен между вызовами', () => {
    const first = getBrowserId()
    const second = getBrowserId()
    assert.equal(first, second, 'id устройства не должен меняться между вызовами')
  })

  test('browserId сохраняется в localStorage под ключом km_share_browser_id', () => {
    const id = getBrowserId()
    assert.equal(storage.data.get(STORAGE_KEY), id)
  })

  test('сохранённый browserId используется повторно — новый UUID не генерируется', () => {
    storage.setItem(STORAGE_KEY, 'already-stored-id')
    let generated = 0
    setGlobal(
      'crypto',
      fakeCrypto({
        id: 'never-used',
      }),
    )
    const cryptoMock = globalThis.crypto
    cryptoMock.randomUUID = () => {
      generated += 1
      return 'never-used'
    }

    assert.equal(getBrowserId(), 'already-stored-id')
    assert.equal(generated, 0, 'при сохранённом id UUID не генерируется')
  })

  test('без randomUUID id всё равно генерируется и сохраняется', () => {
    setGlobal('crypto', {
      getRandomValues: (arr) => {
        arr[0] = 0x42
        return arr
      },
    })

    const id = getBrowserId()
    assert.ok(id && id.length > 0, 'id должен быть непустым')
    assert.equal(storage.data.get(STORAGE_KEY), id)
  })
})

describe('KARAOKE-400: хеш браузера', { concurrency: 1 }, () => {
  test('browserHash = sha256 от "browser:" + browserId', TIMEOUT, async () => {
    const hash = await getBrowserHash()

    assert.equal(digestCalls.length, 1)
    assert.equal(digestCalls[0].algorithm, 'SHA-256')
    assert.equal(digestCalls[0].text, `browser:${getBrowserId()}`)

    const expected = Array.from(new Uint8Array(32).fill(0xab))
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('')
    assert.equal(hash, expected)
    assert.equal(hash.length, 64, 'sha256 в hex — 64 символа')
  })

  test('sha256Hex пустой строки кодирует ноль байт', TIMEOUT, async () => {
    await sha256Hex('')
    assert.equal(digestCalls.length, 1)
    assert.equal(digestCalls[0].text, '')
  })

  test('sha256Hex приводит null к пустой строке', TIMEOUT, async () => {
    await sha256Hex(null)
    assert.equal(digestCalls[0].text, '')
  })
})

describe('KARAOKE-400: анонимный claim', { concurrency: 1 }, () => {
  test('claim шлёт POST /api/public/share/claim с secret и browserHash', TIMEOUT, async () => {
    const promise = claimShare('secret-1')
    await flush()

    const xhr = FakeXHR.last()
    assert.ok(xhr, 'XHR должен быть создан')
    assert.equal(xhr.method, 'POST')
    assert.equal(xhr.url, '/api/public/share/claim')
    assert.equal(xhr.headers['Content-type'], 'application/json')

    const body = JSON.parse(xhr.payload)
    assert.equal(body.secret, 'secret-1')
    assert.equal(body.browserHash.length, 64)
    assert.deepEqual(Object.keys(body).sort(), ['browserHash', 'secret'])

    xhr.respond(200, { sessionTokenHash: 'sth-1' })
    await promise
  })

  test('claim разбирает JSON-ответа', TIMEOUT, async () => {
    const promise = claimShare('secret-2')
    await flush()

    FakeXHR.last().respond(200, { sessionTokenHash: 'sth-2', redirectTo: '/player/1' })
    const result = await promise

    assert.equal(result.status, 200)
    assert.equal(result.body.sessionTokenHash, 'sth-2')
    assert.equal(result.body.redirectTo, '/player/1')
  })

  test('claim при не-JSON ответе отдаёт body = null, а не падает', TIMEOUT, async () => {
    const promise = claimShare('secret-3')
    await flush()

    FakeXHR.last().respond(502, '<html>bad gateway</html>')
    const result = await promise

    assert.equal(result.status, 502)
    assert.equal(result.body, null)
  })

  test('claim при пустом ответе отдаёт body = null', TIMEOUT, async () => {
    const promise = claimShare('secret-4')
    await flush()

    FakeXHR.last().respond(204, '')
    const result = await promise

    assert.equal(result.status, 204)
    assert.equal(result.body, null)
  })

  test('claim при сетевом отказе отклоняется с network_error', TIMEOUT, async () => {
    const promise = claimShare('secret-5')
    await flush()

    FakeXHR.last().fail()
    await assert.rejects(promise, (err) => err.message === 'network_error')
  })
})

describe('KARAOKE-400: heartbeat', { concurrency: 1 }, () => {
  test('heartbeat шлёт POST /api/public/share/heartbeat с sessionTokenHash', TIMEOUT, async () => {
    const promise = heartbeat('sth-1')
    await flush()

    const xhr = FakeXHR.last()
    assert.ok(xhr, 'XHR должен быть создан')
    assert.equal(xhr.method, 'POST')
    assert.equal(xhr.url, '/api/public/share/heartbeat')
    assert.equal(xhr.headers['Content-type'], 'application/json')

    const body = JSON.parse(xhr.payload)
    assert.equal(body.sessionTokenHash, 'sth-1')
    assert.deepEqual(Object.keys(body), ['sessionTokenHash'])

    xhr.respond(200, { ok: true })
    await promise
  })

  test('heartbeat разбирает JSON-ответа', TIMEOUT, async () => {
    const promise = heartbeat('sth-2')
    await flush()

    FakeXHR.last().respond(200, { ok: true })
    const result = await promise

    assert.equal(result.status, 200)
    assert.deepEqual(result.body, { ok: true })
  })

  test('истёкший lease — 410 в статусе ответа', TIMEOUT, async () => {
    const promise = heartbeat('sth-3')
    await flush()

    FakeXHR.last().respond(410, { errorCode: 'share.leaseExpired' })
    const result = await promise

    assert.equal(result.status, 410)
    assert.equal(result.body.errorCode, 'share.leaseExpired')
  })

  test('heartbeat при сетевом отказе отклоняется с network_error', TIMEOUT, async () => {
    const promise = heartbeat('sth-4')
    await flush()

    FakeXHR.last().fail()
    await assert.rejects(promise, (err) => err.message === 'network_error')
  })
})

describe('KARAOKE-400: release', { concurrency: 1 }, () => {
  test('при наличии sendBeacon запрос уходит через beacon, XHR не создаётся', TIMEOUT, async () => {
    const sent = []
    setGlobal('navigator', {
      sendBeacon: (url, blob) => {
        sent.push({ url, blob })
        return true
      },
    })

    release('sth-1')

    assert.equal(sent.length, 1, 'sendBeacon должен быть вызван один раз')
    assert.equal(sent[0].url, '/api/public/share/release')
    assert.equal(FakeXHR.instances.length, 0, 'XHR не должен создаваться при sendBeacon')
    assert.equal(
      await sent[0].blob.text(),
      JSON.stringify({ sessionTokenHash: 'sth-1', result: 'closed' }),
    )
  })

  test('результат по умолчанию — closed, переданный результат сохраняется', TIMEOUT, async () => {
    const sent = []
    setGlobal('navigator', {
      sendBeacon: (url, blob) => {
        sent.push({ url, blob })
        return true
      },
    })

    release('sth-2', 'played')
    const payload = JSON.parse(await sent[0].blob.text())

    assert.equal(payload.sessionTokenHash, 'sth-2')
    assert.equal(payload.result, 'played')
  })

  test('без sendBeacon запрос уходит через XHR', () => {
    setGlobal('navigator', {})

    release('sth-3')

    const xhr = FakeXHR.last()
    assert.ok(xhr, 'XHR должен быть создан')
    assert.equal(xhr.method, 'POST')
    assert.equal(xhr.url, '/api/public/share/release')
    assert.equal(xhr.headers['Content-type'], 'application/json')
    assert.ok(xhr.sent, 'XHR должен быть отправлен')

    const body = JSON.parse(xhr.payload)
    assert.equal(body.sessionTokenHash, 'sth-3')
    assert.equal(body.result, 'closed')
  })

  test('при падении sendBeacon уходим в XHR', () => {
    setGlobal('navigator', {
      sendBeacon: () => {
        throw new Error('beacon unavailable')
      },
    })

    release('sth-4')

    const xhr = FakeXHR.last()
    assert.ok(xhr, 'XHR должен быть создан')
    assert.equal(xhr.url, '/api/public/share/release')
    assert.equal(JSON.parse(xhr.payload).sessionTokenHash, 'sth-4')
  })
})
