// Отдельный клиент для auth-эндпоинтов (не переиспользует services/api.js): promisedXMLHttpRequest
// отбрасывает тело ответа при ошибке (reject получает только xhr.statusText), а нам нужно тело даже
// на 401/403/400 (например {"error":"banned","reason":"..."}), поэтому здесь всегда резолвим промис
// с { status, body } и решение о том, что считать ошибкой, остаётся за вызывающим кодом.
// BR-339 (аудит #336, пакет PKG-S2-14): таймаут XHR. Без него зависший запрос не отличим от
// долгой загрузки — `loading` крутится бесконечно, а `xhr.onerror` не срабатывает (браузер
// держит соединение). 15 с — заметно больше p99 обычного ответа API и меньше терпения
// пользователя. Отдельная ошибка 'timeout' позволяет UI написать «сервер не ответил вовремя».
const DEFAULT_TIMEOUT_MS = 15000
// Загрузка аудиофайла (authUpload) законно длится дольше — отдельный, более щедрый лимит.
const UPLOAD_TIMEOUT_MS = 120000

function request(method, path, params, token) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, path, true)
    xhr.setRequestHeader('Content-type', 'application/x-www-form-urlencoded')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.onload = () => {
      let body = null
      if (xhr.response) {
        try {
          body = JSON.parse(xhr.response)
        } catch (e) {
          body = null
        }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))
    xhr.timeout = DEFAULT_TIMEOUT_MS
    xhr.ontimeout = () => reject(new Error('timeout'))

    const pairs = []
    for (const key in params) {
      if (params[key] === undefined || params[key] === null) continue
      pairs.push(encodeURIComponent(key) + '=' + encodeURIComponent(params[key]))
    }
    xhr.send(pairs.join('&'))
  })
}

export function authGet(path, token) {
  return request('GET', path, null, token)
}

export function authPost(path, params, token) {
  return request('POST', path, params, token)
}

/**
 * JSON-body POST (Pass 361, specs/361-playlists-membership-uri-length).
 * Зеркало authPost(), но с `Content-Type: application/json` и JSON.stringify тела.
 * Существующий authPost(form-encoded) НЕ трогаем — используется в 10+ местах
 * (LoginView/RegisterView/CartApi/etc.). Новые эндпоинты с большими payloads
 * используют authPostJson (например, POST /api/public/account/playlists/membership).
 *
 * @param {string} path — путь endpoint'а (например, '/api/public/account/playlists/membership').
 * @param {object} jsonBody — объект, сериализуемый в JSON (например, { ids: [1,2,3] }).
 * @param {string} token — Bearer JWT (km_auth_token из localStorage).
 * @returns {Promise<{status: number, body: any}>}
 */
export function authPostJson(path, jsonBody, token) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path, true)
    xhr.setRequestHeader('Content-Type', 'application/json')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.onload = () => {
      let body = null
      if (xhr.response) {
        try {
          body = JSON.parse(xhr.response)
        } catch (e) {
          body = null
        }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))
    xhr.timeout = DEFAULT_TIMEOUT_MS
    xhr.ontimeout = () => {
      reject(new Error('timeout'))
    }
    xhr.send(JSON.stringify(jsonBody))
  })
}

// Multipart-загрузка файла (StemJobsView) — отдельная функция, а не ветка request(): FormData
// требует НЕ выставлять Content-Type руками (браузер сам проставит multipart/form-data с boundary),
// и нужен onProgress для прогресс-бара загрузки большого аудиофайла.
export function authUpload(path, file, fields, token, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', path, true)
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.upload.onprogress = (e) => {
      if (onProgress && e.lengthComputable) onProgress(Math.round((e.loaded * 100) / e.total))
    }
    xhr.onload = () => {
      let body = null
      if (xhr.response) {
        try {
          body = JSON.parse(xhr.response)
        } catch (e) {
          body = null
        }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))
    // Загрузка большого стема идёт дольше обычного запроса — таймаут здесь 120 с: он ловит
    // «соединение зависло», а не медленную загрузку (BR-339).
    xhr.timeout = UPLOAD_TIMEOUT_MS
    xhr.ontimeout = () => reject(new Error('timeout'))

    const formData = new FormData()
    formData.append('file', file)
    for (const key in fields) {
      if (fields[key] === undefined || fields[key] === null) continue
      formData.append(key, fields[key])
    }
    xhr.send(formData)
  })
}
