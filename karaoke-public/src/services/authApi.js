// Отдельный клиент для auth-эндпоинтов (не переиспользует services/api.js): promisedXMLHttpRequest
// отбрасывает тело ответа при ошибке (reject получает только xhr.statusText), а нам нужно тело даже
// на 401/403/400 (например {"error":"banned","reason":"..."}), поэтому здесь всегда резолвим промис
// с { status, body } и решение о том, что считать ошибкой, остаётся за вызывающим кодом.
function request(method, path, params, token) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open(method, path, true)
    xhr.setRequestHeader('Content-type', 'application/x-www-form-urlencoded')
    if (token) xhr.setRequestHeader('Authorization', `Bearer ${token}`)
    xhr.onload = () => {
      let body = null
      if (xhr.response) {
        try { body = JSON.parse(xhr.response) } catch (e) { body = null }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))

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
