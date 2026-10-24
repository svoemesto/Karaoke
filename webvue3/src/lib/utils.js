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

export function promisedXMLHttpRequest(obj) {
  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest()
    xhr.open(obj.method || 'GET', obj.url, true)
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

export function setWebvueProp(field, key, value) {
  if (value === undefined || value === null) value = ''
  if (field !== undefined && field !== null) {
    promisedXMLHttpRequest({
      method: 'POST',
      url: '/api/setwebvueprop',
      params: { key: key, value: value },
    })
  }
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
