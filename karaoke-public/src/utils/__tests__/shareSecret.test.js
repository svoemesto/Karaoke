// BR-299 / BR-301 (аудит #336, пакет PKG-S3-27, OpenProject #399): секрет share-ссылки.
//
// BR-299 — ЧТО БЫЛО. Capability-URL (`/share/{linkId}/{secret}` — полный доступ к
// премиум-плееру без авторизации, на сервере только SHA-256) хранился в `localStorage` под
// ключом `karaoke-share-url:<linkId>` и НЕ удалялся ни при выходе из аккаунта, ни по
// истечении ссылки. На общем устройстве профиль браузера сохранял действующие capability-URL
// прежнего владельца (до 7 дней).
//
// BR-301 — ЧТО БЫЛО. 30-секундный polling `loadCurrent()` обновлял только `currentLink`, а
// шаблон рендерил блок `created` со СТАТИЧЕСКИМ `shareUrl`. Если ссылку перевыпустили или
// отозвали с другого устройства, модалка продолжала показывать мёртвый URL как активный.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  SHARE_URL_STORAGE_PREFIX,
  clearAllShareUrls,
  clearShareUrl,
  readShareUrl,
  reconcileCreatedLink,
  resolveSecretStorage,
  saveShareUrl,
  shareUrlStorageKey,
} from '../shareSecret.js'
import { readSource } from './sourceContract.js'

/** Минимальный Storage. */
function fakeStorage(initial = {}) {
  const map = new Map(Object.entries(initial))
  return {
    map,
    get length() {
      return map.size
    },
    key(i) {
      return [...map.keys()][i] ?? null
    },
    getItem(k) {
      return map.has(k) ? map.get(k) : null
    },
    setItem(k, v) {
      map.set(k, String(v))
    },
    removeItem(k) {
      map.delete(k)
    },
  }
}

describe('BR-299: секрет не хранится в localStorage', () => {
  test('ключ сохраняет префикс', () => {
    assert.equal(shareUrlStorageKey(42), `${SHARE_URL_STORAGE_PREFIX}42`)
  })

  test('sessionStorage доступен — возвращается он', () => {
    const storage = fakeStorage()
    assert.equal(resolveSecretStorage({ sessionStorage: storage }), storage)
  })

  test('sessionStorage бросает при записи — возвращается null (секрет только в памяти)', () => {
    const broken = {
      get length() {
        return 0
      },
      key() {
        return null
      },
      getItem() {
        return null
      },
      setItem() {
        throw new Error('SecurityError')
      },
      removeItem() {},
    }
    assert.equal(resolveSecretStorage({ sessionStorage: broken }), null)
  })

  test('нет sessionStorage — null', () => {
    assert.equal(resolveSecretStorage({}), null)
  })

  test('save/read/clear работают через переданное хранилище', () => {
    const storage = fakeStorage()
    assert.equal(saveShareUrl(storage, 7, 'https://site/share/7/secret'), true)
    assert.equal(readShareUrl(storage, 7), 'https://site/share/7/secret')
    clearShareUrl(storage, 7)
    assert.equal(readShareUrl(storage, 7), '')
  })

  test('хранилище недоступно — операции безопасны (без исключения)', () => {
    assert.equal(saveShareUrl(null, 7, 'x'), false)
    assert.equal(readShareUrl(null, 7), '')
    assert.doesNotThrow(() => clearShareUrl(null, 7))
  })

  test('clearAllShareUrls удаляет ТОЛЬКО секреты share-ссылок', () => {
    const storage = fakeStorage({
      'karaoke-share-url:1': 'a',
      'karaoke-share-url:2': 'b',
      km_auth_token: 'token',
      km_news_last_seen_id: '5',
    })
    const removed = clearAllShareUrls(storage)
    assert.equal(removed, 2)
    assert.equal(storage.getItem('karaoke-share-url:1'), null)
    assert.equal(storage.getItem('karaoke-share-url:2'), null)
    assert.equal(storage.getItem('km_auth_token'), 'token', 'сессия не должна затрагиваться')
    assert.equal(storage.getItem('km_news_last_seen_id'), '5')
  })
})

describe('BR-301: «созданная» ссылка сверяется с сервером', () => {
  test('не создавали — ничего не меняется', () => {
    const out = reconcileCreatedLink(false, null, { linkId: 1 })
    assert.deepEqual(out, { created: false, stale: false })
  })

  test('сервер отозвал ссылку (currentLink = null) — created сбрасывается', () => {
    const out = reconcileCreatedLink(true, 1, null)
    assert.equal(out.created, false, 'показывать отозванный URL как активный нельзя')
    assert.equal(out.stale, true)
  })

  test('ссылку перевыпустили (другой linkId) — created сбрасывается', () => {
    const out = reconcileCreatedLink(true, 1, { linkId: 2, active: true })
    assert.equal(out.created, false, 'показывать старый URL после перевыпуска нельзя')
    assert.equal(out.stale, true)
  })

  test('сервер отметил ссылку неактивной — created сбрасывается', () => {
    const out = reconcileCreatedLink(true, 1, { linkId: 1, active: false })
    assert.equal(out.created, false)
    assert.equal(out.stale, true)
  })

  test('ссылка та же и активна — created остаётся', () => {
    const out = reconcileCreatedLink(true, 1, { linkId: 1, active: true })
    assert.equal(out.created, true)
    assert.equal(out.stale, false)
  })

  test('id разных типов (число/строка) — считаются одним', () => {
    const out = reconcileCreatedLink(true, '1', { linkId: 1, active: true })
    assert.equal(out.created, true)
  })
})

describe('BR-299 / BR-301: контракт исходников (падает на старом коде)', () => {
  test('ShareLinkModal НЕ использует localStorage для capability-URL', () => {
    const src = readSource('src/components/ShareLinkModal.vue')
    assert.ok(
      !/localStorage\.setItem\(storageKey/.test(src),
      'capability-URL не должен попадать в localStorage (BR-299)',
    )
    assert.ok(
      !/localStorage\.getItem\(storageKey/.test(src),
      'capability-URL не должен читаться из localStorage (BR-299)',
    )
    assert.ok(
      src.includes('resolveSecretStorage'),
      'нужен sessionStorage-хранилище для секрета',
    )
  })

  test('ShareLinkModal сверяет created с ответом polling', () => {
    const src = readSource('src/components/ShareLinkModal.vue')
    assert.ok(
      src.includes('reconcileCreatedLink'),
      'polling должен сверять созданную ссылку с фактическим состоянием (BR-301)',
    )
    assert.ok(
      src.includes('createdLinkId'),
      'нужно хранить id созданной ссылки для сверки',
    )
  })

  test('useAuth чистит секреты при выходе и смене пользователя', () => {
    const src = readSource('src/composables/useAuth.js')
    assert.ok(
      src.includes('clearAllShareUrls'),
      'clearSession/setSession должны чистить capability-URL (BR-299)',
    )
    assert.ok(
      /function clearSession\(\)[\s\S]{0,400}clearAllShareUrls/.test(src),
      'именно clearSession должен чистить секреты',
    )
  })
})
