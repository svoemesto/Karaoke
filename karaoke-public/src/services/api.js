import { promisedXMLHttpRequest } from '../lib/utils'

function buildUrl(path, params) {
  const query = Object.entries(params || {})
    .filter(([, v]) => v !== undefined && v !== null && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&')
  return query ? `${path}?${query}` : path
}

export async function apiGet(path, params) {
  const response = await promisedXMLHttpRequest({ method: 'GET', url: buildUrl(path, params) })
  return JSON.parse(response)
}

export async function apiPost(path, params, headers) {
  const response = await promisedXMLHttpRequest({ method: 'POST', url: path, params, headers })
  return response ? JSON.parse(response) : null
}
