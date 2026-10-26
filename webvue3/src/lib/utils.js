// Уникальный на вкладку браузера идентификатор. sessionStorage переживает F5 (та же вкладка - тот же
// tabId), но не наследуется новой вкладкой/копией - у каждой вкладки/компьютера будет свой SSE-канал
// (см. подписку /api/subscribe?tabId=... в App.vue) и адресная доставка тостов MESSAGE/ERROR (см.
// TabIdFilter/SseNotificationService в karaoke-app).
export function getTabId() {
  const STORAGE_KEY = 'tabId'
  let tabId = sessionStorage.getItem(STORAGE_KEY)
  if (!tabId) {
    tabId = crypto.randomUUID()
    sessionStorage.setItem(STORAGE_KEY, tabId)
  }
  return tabId
}

// BR-395 (аудит #336, пакет PKG-S3-32, OpenProject #404): поддержка отмены запроса.
//
// ДО ПРАВКИ транспорт не умел отменяться: `promisedXMLHttpRequest` возвращал промис,
// который нельзя было прервать. Компонент, ушедший со страницы, оставался с «висящим»
// запросом — его ответ применялся к уже другой записи (см. Pictures/store.js, автосейв).
//
// Теперь можно передать `obj.signal` (стандартный `AbortSignal`): при `abort()` запрос
// прерывается, а промис отклоняется с ошибкой `AbortError` (не «зависает»).
// Обратная совместимость сохранена: без `signal` поведение прежнее.
// BR-395: ошибка отменённого запроса. Имя `AbortError` — стандартный признак отмены,
// её не надо показывать пользователю как «ошибку сохранения».
export function createAbortError() {
  const error = new Error('Запрос отменён')
  error.name = 'AbortError'
  return error
}

// BR-481 (аудит #336, пакет PKG-S3-33, OpenProject #405): конечный таймаут запроса.
//
// ДО ПРАВКИ `promisedXMLHttpRequest` не задавал `xhr.timeout` и не обрабатывал
// `ontimeout`/`onabort`. Если сервер принял соединение, но не ответил (зависший
// поток бэкенда, потерянный ответ прокси), промис НИКОГДА не завершался: ни
// resolve, ни reject. Все вызывающие цепочки (в том числе HomeView) оставались
// в состоянии «ожидание» навсегда, а отмены не было — `xhr` наружу не
// возвращается. Для `setWebvueProp` это выглядело как «фильтр молча не
// сохранился».
export const DEFAULT_REQUEST_TIMEOUT_MS = 30000

/**
 * Создаёт ошибку таймаута запроса.
 *
 * @param {number} timeoutMs — предел ожидания, мс.
 * @returns {Error} ошибка с именем `TimeoutError`.
 */
export function createTimeoutError(timeoutMs) {
  const error = new Error(`Превышено время ожидания ответа сервера (${timeoutMs} мс)`)
  error.name = 'TimeoutError'
  error.timeout = timeoutMs
  return error
}

export function promisedXMLHttpRequest(obj) {
  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest()
    if (obj.signal) {
      if (obj.signal.aborted) {
        reject(createAbortError())
        return
      }
      obj.signal.addEventListener(
        'abort',
        () => {
          xhr.abort()
          reject(createAbortError())
        },
        { once: true },
      )
    }
    xhr.open(obj.method || 'GET', obj.url, true)
    // BR-481: конечный таймаут. `obj.timeout` позволяет вызывающему задать свой
    // предел; `0`/`null` отключают таймаут (как в XHR).
    const timeoutMs =
      obj.timeout === undefined || obj.timeout === null
        ? DEFAULT_REQUEST_TIMEOUT_MS
        : Number(obj.timeout)
    if (Number.isFinite(timeoutMs) && timeoutMs > 0) {
      xhr.timeout = timeoutMs
    }
    if (obj.headers === undefined)
      obj.headers = { 'Content-type': 'application/x-www-form-urlencoded' }
    obj.headers['X-Tab-Id'] = getTabId()
    if (obj.headers) {
      Object.keys(obj.headers).forEach((key) => {
        xhr.setRequestHeader(key, obj.headers[key])
      })
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response)
      } else {
        // spec 235: пробросить и status, и response body — нужно для обработки 409 Conflict
        // в /api/sync/oneclick (UI должен показать "Автосинхронизация уже выполняется в фоне").
        const err = new Error(xhr.statusText || `HTTP ${xhr.status}`)
        err.status = xhr.status
        err.responseBody = xhr.response
        reject(err)
      }
    }
    xhr.onerror = () => {
      const err = new Error(xhr.statusText || 'Network error')
      err.status = xhr.status
      err.responseBody = xhr.response
      reject(err)
    }
    // BR-481: таймаут — промис ОБЯЗАН завершиться. Раньше запрос, на который
    // сервер не ответил, висел вечно, и вызывающий ждал его безнадёжно.
    xhr.ontimeout = () => {
      reject(createTimeoutError(timeoutMs))
    }
    // BR-481: отмена запроса (в т.ч. по AbortSignal) — тоже завершение промиса.
    xhr.onabort = () => {
      reject(createAbortError())
    }
    // specs/315-admin-ui-karaoke-process-v5 (RC-1 iter #3): если передан obj.body — шлём JSON
    // (Content-Type application/json), иначе form-urlencoded (getParamStringToSend). Без этого
    // POST edit/delete/retry с JSON-телом получает Spring 415 Unsupported Media Type.
    if (obj.body !== undefined) {
      xhr.setRequestHeader('Content-Type', 'application/json')
      xhr.send(JSON.stringify(obj.body))
    } else {
      xhr.send(getParamStringToSend(obj.params))
    }
  })
}

// BR-366 (аудит #336, пакет PKG-S3-31, OpenProject #403).
//
// Отказ записи больше не уходит в unhandled rejection: раньше промис игнорировался,
// и ошибка была видна только как «Unhandled Promise Rejection» в консоли. Теперь
// промис возвращается, а отказ обрабатывается предупреждением.
//
// BR-482 (аудит #336, пакет PKG-S3-33, OpenProject #405): запись фильтра —
// дебаунс + сериализация по ключу.
//
// ДО ПРАВКИ `setWebvueProp` отправлял POST на КАЖДОЕ изменение значения фильтра.
// Значения фильтров пишутся через computed-сеттер, который срабатывает на
// каждое нажатие клавиши: при вводе «Песня» уходило 5 POST'ов со значениями
// «П», «Пе», «Пес», «Песн», «Песня». Бэкенд пишет «слепо» (`WVP.set(key, value)`,
// без сверки версии), а порядок обработки запросов не гарантирован (параллельные
// соединения браузера), поэтому итоговое серверное значение могло оказаться
// промежуточным префиксом («Пес» вместо «Песня»): локально фильтр показывал полное
// значение, после перезагрузки страницы — усечённое.
//
// ПОСЛЕ ПРАВКИ: значения одного ключа пишутся с дебаунсом (промежуточные
// префиксы не уходят) и строго по очереди (в полёте не бывает двух запросов по
// одному ключу), а если во время запроса пришло новое значение — оно дописывается
// следующим. Итоговое серверное значение ВСЕГДА равно последнему клиентскому.
export const PROP_WRITE_DEBOUNCE_MS = 300

// key -> { timer, value, version, sentVersion, inFlight, waiters }
const propWriteQueue = new Map()

/**
 * Разрешает ожидающие промисы записи, версия которых не новее отправленной.
 *
 * @param {Object} entry — запись очереди по ключу.
 * @param {number} sentVersion — версия, которая только что записана (или устарела).
 * @param {boolean} ok — успешна ли запись.
 * @returns {void}
 */
const resolvePropWaiters = (entry, sentVersion, ok) => {
  const remaining = []
  entry.waiters.forEach((waiter) => {
    if (waiter.version <= sentVersion) {
      waiter.resolve(ok)
    } else {
      remaining.push(waiter)
    }
  })
  entry.waiters = remaining
}

/**
 * Отправляет накопленное значение ключа на сервер (одна запись за раз).
 *
 * @param {string} key — ключ настройки.
 * @returns {void}
 */
const flushPropWrite = (key) => {
  const entry = propWriteQueue.get(key)
  if (!entry) return
  // Запрос по этому ключу уже в полёте — он допишет самое свежее значение сам.
  if (entry.inFlight) return
  if (entry.sentVersion === entry.version) {
    resolvePropWaiters(entry, entry.sentVersion, true)
    propWriteQueue.delete(key)
    return
  }
  const version = entry.version
  const value = entry.value
  entry.sentVersion = version
  entry.inFlight = true
  promisedXMLHttpRequest({
    method: 'POST',
    url: '/api/setwebvueprop',
    params: { key: key, value: value },
  })
    .then(() => true)
    .catch((error) => {
      console.warn(`Не удалось сохранить настройку ${key}:`, error)
      return false
    })
    .then((ok) => {
      entry.inFlight = false
      resolvePropWaiters(entry, version, ok)
      if (entry.version > entry.sentVersion) {
        // Во время запроса пришло новое значение — дописываем его следующим.
        flushPropWrite(key)
      } else {
        propWriteQueue.delete(key)
      }
    })
}

/**
 * Ставит значение настройки в очередь записи (дебаунс + сериализация по ключу).
 *
 * @param {string} key — ключ настройки.
 * @param {string} value — значение.
 * @returns {Promise<boolean>} true — значение записано (или вытеснено более новым).
 */
const enqueuePropWrite = (key, value) => {
  let entry = propWriteQueue.get(key)
  if (!entry) {
    entry = { timer: null, value: '', version: 0, sentVersion: 0, inFlight: false, waiters: [] }
    propWriteQueue.set(key, entry)
  }
  entry.version += 1
  entry.value = value
  const promise = new Promise((resolve) => {
    entry.waiters.push({ version: entry.version, resolve: resolve })
  })
  if (entry.timer !== null) clearTimeout(entry.timer)
  entry.timer = setTimeout(() => {
    entry.timer = null
    flushPropWrite(key)
  }, PROP_WRITE_DEBOUNCE_MS)
  return promise
}

/**
 * Немедленно отправляет отложенные значения настроек (без ожидания дебаунса).
 *
 * @returns {void}
 */
export function flushPendingPropWrites() {
  propWriteQueue.forEach((entry, key) => {
    if (entry.timer !== null) {
      clearTimeout(entry.timer)
      entry.timer = null
    }
    flushPropWrite(key)
  })
}

export function setWebvueProp(field, key, value) {
  if (value === undefined || value === null) value = ''
  if (field === undefined || field === null) return Promise.resolve(false)
  return enqueuePropWrite(key, value)
}

// BR-379 / BR-426 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// Сериализация form-urlencoded для POST-запросов админки.
//
// ДЕФЕКТ (до правки): ключи со значением `undefined`/`null` попадали в тело как
// строки `pageSize=undefined` / `filterAssigneeId=undefined`. Бэкенд объявляет такие
// параметры как `Int`/`Long?` с `defaultValue`; конверсия строки «undefined» в число
// бросает `MethodArgumentTypeMismatchException` → HTTP 400. То есть значение, которого
// у вызывающего НЕТ, превращалось в заведомо неверное значение, и запрос падал вместо
// того, чтобы получить серверный дефолт. Это ломало «Применить фильтр» в истории
// прослушиваний (BR-379) и массовое удаление одобренных заданий (BR-426).
//
// ПОЧЕМУ ЗДЕСЬ, А НЕ В ВЫЗЫВАЮЩИХ: `undefined` не имеет смысла в query/form-строке
// ни в одном сценарии — пропуск ключа и есть «не передавать параметр». Правка в одном
// месте закрывает корень для всех вызывающих, а не только для двух найденных.
//
// Экспортируется для регрессионного теста (lib/__tests__/params-serializer.test.js).
export function getParamStringToSend(params) {
  let urlEncodedDataPairs = [],
    name
  for (name in params) {
    const value = params[name]
    // Отсутствующий параметр не передаём вовсе — иначе сервер получит строку
    // «undefined» и ответит 400 вместо применения defaultValue.
    if (value === undefined || value === null) continue
    urlEncodedDataPairs.push(encodeURIComponent(name) + '=' + encodeURIComponent(value))
  }
  return urlEncodedDataPairs.join('&')
}

function addZero(num) {
  if (num >= 0 && num <= 9) {
    return '0' + num
  } else {
    return num
  }
}

export function isStringContainThisSymbols(sourceString, symbolString) {
  for (let i = 0; i < sourceString.length; i++) {
    const symbolInString = sourceString[i]
    if (symbolString.includes(symbolInString)) return true
  }
  return false
}

// BR-411 (аудит #336, пакет PKG-S2-17, OpenProject #380): разбор даты формата `dd.MM.yy`.
//
// ДО ПРАВКИ `stringDDMMYYtoDate('')` давал `new Date(NaN, NaN, NaN)` (Invalid Date), а
// `stringDDMMYYaddDays('', 90)` — строку `NaN.NaN.N`. Эта строка уходила в
// `POST /api/publicationsdigest` как `filterDateTo`; бэкенд парсил её `SimpleDateFormat`
// и падал с HTTP 500, а значение ещё и сохранялось в webvueProp — то есть отравленный
// фильтр переживал F5. Один клик по штатной кнопке `EXCLUSIVE` (штатный ответ
// `/api/publications/date` — пустая строка) ломал грид публикаций.
//
// Теперь невалидный вход даёт `null`/пустую строку: «даты нет» — это отсутствие даты,
// а не «NaN». Проверка строгая и по формату, и по календарю (31.02.26 отвергается).
const DDMMYY_PATTERN = /^(\d{1,2})\.(\d{1,2})\.(\d{2})$/

/**
 * Проверяет, что строка — существующая дата в формате `dd.MM.yy`.
 *
 * @param {string} value
 * @returns {boolean}
 */
export function isValidDDMMYY(value) {
  if (typeof value !== 'string') return false
  const match = DDMMYY_PATTERN.exec(value.trim())
  if (!match) return false
  const day = Number(match[1])
  const month = Number(match[2])
  const year = 2000 + Number(match[3])
  const date = new Date(year, month - 1, day)
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day
}

/**
 * Разбирает `dd.MM.yy`. Возвращает `null`, если строки нет или она не является датой.
 *
 * @param {string} stringDDMMYY
 * @returns {Date|null}
 */
export function stringDDMMYYtoDate(stringDDMMYY) {
  if (!isValidDDMMYY(stringDDMMYY)) return null
  const parts = stringDDMMYY.trim().split('.')
  return new Date(2000 + Number(parts[2]), Number(parts[1]) - 1, Number(parts[0]))
}

export function dateToStringDDMMYY(date) {
  return `${addZero(date.getDate())}.${addZero(date.getMonth() + 1)}.${date.getFullYear().toString().substring(2)}`
}

/**
 * Прибавляет дни к дате `dd.MM.yy`.
 *
 * @param {string} stringDDMMYY — исходная дата; пустая строка допустима (окно без границы).
 * @param {number} days
 * @returns {string} новая дата `dd.MM.yy` либо '' — если исходная дата пустая/невалидная.
 *   Раньше здесь возвращалось `NaN.NaN.N`, что ломало серверный фильтр (BR-411).
 */
export function stringDDMMYYaddDays(stringDDMMYY, days) {
  const date = stringDDMMYYtoDate(stringDDMMYY)
  if (date === null) return ''
  date.setDate(date.getDate() + Number(days))
  if (Number.isNaN(date.getTime())) return ''
  return dateToStringDDMMYY(date)
}
