// BR-397 / BR-407 / BR-409 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// «Шов» для регрессионных тестов гонки target: подменяет глобальный XMLHttpRequest и отдаёт
// ответы в том порядке, который задаёт тест. Это позволяет воспроизвести перемежающийся
// порядок «запрос A → запрос B → ответ B → ответ A», не поднимая браузер и не заводя новых
// зависимостей (node:test).
//
// `promisedXMLHttpRequest` (webvue3/src/lib/utils.js) обращается к `sessionStorage` и
// `crypto.randomUUID` из getTabId — в node их нет, поэтому заглушка ставит и их.
class FakeXhr {
  constructor() {
    this.method = 'GET'
    this.url = ''
    this.headers = {}
    this.body = ''
    this.status = 0
    this.response = ''
    this.responded = false
    FakeXhr.created.push(this)
  }

  open(method, url) {
    this.method = method
    this.url = url
  }

  setRequestHeader(name, value) {
    this.headers[name] = value
  }

  send(body) {
    this.body = body || ''
    FakeXhr.sent.push(this)
  }

  respond(status, payload) {
    this.status = status
    this.response = typeof payload === 'string' ? payload : JSON.stringify(payload)
    this.responded = true
    this.onload()
  }

  /**
   * Имитирует таймаут запроса (BR-481, пакет PKG-S3-33).
   *
   * Настоящий XMLHttpRequest при превышении `xhr.timeout` вызывает `ontimeout`.
   * Заглушка умеет то же самое — иначе конечный таймаут нечем проверить.
   * Имя НЕ `timeout`: у настоящего XHR `timeout` — это СВОЙСТВО (число), и
   * транспорт присваивает его (`xhr.timeout = ...`).
   *
   * @returns {void}
   */
  simulateTimeout() {
    if (typeof this.ontimeout === 'function') this.ontimeout()
  }

  /**
   * Имитирует отмену запроса (BR-481, пакет PKG-S3-33).
   *
   * @returns {void}
   */
  abort() {
    this.aborted = true
    if (typeof this.onabort === 'function') this.onabort()
  }

  /** Параметры form-urlencoded тела запроса. */
  get params() {
    return Object.fromEntries(new URLSearchParams(this.body))
  }

  /** Последний отправленный запрос, удовлетворяющий предикату. */
  static last(predicate = () => true) {
    return [...FakeXhr.sent].reverse().find(predicate)
  }

  /** Все отправленные запросы, удовлетворяющие предикату. */
  static all(predicate = () => true) {
    return FakeXhr.sent.filter(predicate)
  }

  static reset() {
    FakeXhr.created = []
    FakeXhr.sent = []
  }

  /** Устанавливает заглушки глобального окружения и XMLHttpRequest. */
  static install() {
    FakeXhr.reset()
    globalThis.XMLHttpRequest = FakeXhr
    if (!globalThis.sessionStorage) {
      const store = new Map()
      globalThis.sessionStorage = {
        getItem: (key) => (store.has(key) ? store.get(key) : null),
        setItem: (key, value) => store.set(key, String(value)),
        removeItem: (key) => store.delete(key),
      }
    }
    return FakeXhr
  }
}

FakeXhr.created = []
FakeXhr.sent = []

export { FakeXhr }
