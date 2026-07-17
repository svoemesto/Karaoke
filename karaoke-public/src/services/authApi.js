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
        try { body = JSON.parse(xhr.response) } catch (e) { body = null }
      }
      resolve({ status: xhr.status, body })
    }
    xhr.onerror = () => reject(new Error('network_error'))

    const formData = new FormData()
    formData.append('file', file)
    for (const key in fields) {
      if (fields[key] === undefined || fields[key] === null) continue
      formData.append(key, fields[key])
    }
    xhr.send(formData)
  })
}
