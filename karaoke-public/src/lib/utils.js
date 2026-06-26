export function promisedXMLHttpRequest(obj) {
  return new Promise((resolve, reject) => {
    let xhr = new XMLHttpRequest();
    xhr.open(obj.method || "GET", obj.url, true);
    if (obj.headers === undefined) obj.headers = { 'Content-type': 'application/x-www-form-urlencoded' };
    if (obj.headers) {
      Object.keys(obj.headers).forEach(key => {
        xhr.setRequestHeader(key, obj.headers[key]);
      });
    }
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response);
      } else {
        reject(xhr.statusText);
      }
    };
    xhr.onerror = () => reject(xhr.statusText);
    xhr.send(getParamStringToSend(obj.params));
  });
}

function getParamStringToSend(params) {
  let urlEncodedDataPairs = [], name;
  for (name in params) {
    if (params[name] === undefined || params[name] === null) continue;
    urlEncodedDataPairs.push(encodeURIComponent(name) + '=' + encodeURIComponent(params[name]));
  }
  return urlEncodedDataPairs.join('&');
}
