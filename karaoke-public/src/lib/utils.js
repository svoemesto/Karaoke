export function promisedXMLHttpRequest(obj) {
  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest()
    xhr.open(obj.method || 'GET', obj.url, true)
    // Content-Type должен ставиться всегда, не только когда headers целиком не задан — иначе любой
    // вызов с доп. заголовками (например Authorization для залогиненных, см. api.js authHeader())
    // терял form-urlencoded, браузер слал тело как text/plain, и Spring не мог распарсить
    // @RequestParam Map<String,Any> — событие тихо терялось (баг найден на QW-13, история
    // прослушиваний работала только для анонимов).
    const headers = { 'Content-type': 'application/x-www-form-urlencoded', ...(obj.headers || {}) }
    Object.keys(headers).forEach((key) => {
      xhr.setRequestHeader(key, headers[key])
    })
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response)
      } else {
        reject(xhr.statusText)
      }
    }
    xhr.onerror = () => reject(xhr.statusText)
    xhr.send(getParamStringToSend(obj.params))
  })
}

function getParamStringToSend(params) {
  let urlEncodedDataPairs = [],
    name
  for (name in params) {
    if (params[name] === undefined || params[name] === null) continue
    urlEncodedDataPairs.push(encodeURIComponent(name) + '=' + encodeURIComponent(params[name]))
  }
  return urlEncodedDataPairs.join('&')
}
