// Хелперы для «Временного полного доступа к песне» (add-song-share-link). Анонимный
// browserId (random UUID) в localStorage используется для идентификации устройства —
// разные вкладки одного браузера = одно устройство (см. design D4).
//
// SHA-256 через Web Crypto API доступен в любом современном браузере и в Node 16+. В SPA
// нет нужды в `crypto.subtle.digest()` — обычного hex-дайджеста достаточно для проверки
// равенства на сервере (там тоже считается SHA-256 в `MessageDigest`).

function getBrowserId() {
  const KEY = 'km_share_browser_id'
  let id = localStorage.getItem(KEY)
  if (!id) {
    id =
      crypto && crypto.randomUUID
        ? crypto.randomUUID()
        : ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, (c) =>
            (c ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))).toString(16),
          )
    localStorage.setItem(KEY, id)
  }
  return id
}

async function sha256Hex(input) {
  const data = new TextEncoder().encode(input || '')
  const buf = await crypto.subtle.digest('SHA-256', data)
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

async function getBrowserHash() {
  return sha256Hex('browser:' + getBrowserId())
}

/**
 * Анонимный claim — обмен секрета из URL на sessionTokenHash. Вызывается на /share/{id}/{secret}.
 * Возвращает Promise<{ linkId, sessionTokenHash, redirectTo } | null при ошибке>.
 */
async function claimShare(secret) {
  const browserHash = await getBrowserHash()
  const xhr = new XMLHttpRequest()
  xhr.open('POST', '/api/public/share/claim', true)
  xhr.setRequestHeader('Content-type', 'application/json')
  return new Promise((resolve, reject) => {
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
    xhr.send(JSON.stringify({ secret, browserHash }))
  })
}

/**
 * Heartbeat для продления lease. Вызывается из KaraokePlayer раз в ~25 сек.
 * Возвращает `{ ok: true }` или `{ status: 410 }` при leaseExpired.
 */
function heartbeat(sessionTokenHash) {
  const xhr = new XMLHttpRequest()
  xhr.open('POST', '/api/public/share/heartbeat', true)
  xhr.setRequestHeader('Content-type', 'application/json')
  return new Promise((resolve, reject) => {
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
    xhr.send(JSON.stringify({ sessionTokenHash }))
  })
}

/**
 * Release сессии. На beforeunload отправляется через navigator.sendBeacon.
 */
function release(sessionTokenHash, result) {
  const payload = JSON.stringify({ sessionTokenHash, result: result || 'closed' })
  try {
    if (navigator.sendBeacon) {
      const blob = new Blob([payload], { type: 'application/json' })
      navigator.sendBeacon('/api/public/share/release', blob)
      return
    }
  } catch (e) {
    /* fall through */
  }
  const xhr = new XMLHttpRequest()
  xhr.open('POST', '/api/public/share/release', true)
  xhr.setRequestHeader('Content-type', 'application/json')
  xhr.send(payload)
}

export { getBrowserId, getBrowserHash, sha256Hex, claimShare, heartbeat, release }
