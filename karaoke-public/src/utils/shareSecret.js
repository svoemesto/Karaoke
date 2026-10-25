// BR-299 / BR-301 (аудит #336, пакет PKG-S3-27, OpenProject #399): секрет share-ссылки.
//
// BR-299 — ЧТО БЫЛО. Capability-URL (`/share/{linkId}/{secret}` — даёт полный доступ к
// премиум-плееру БЕЗ авторизации, на сервере хранится только SHA-256) сохранялся в
// `localStorage` под ключом `karaoke-share-url:<linkId>` и НЕ удалялся ни при выходе из
// аккаунта, ни по истечении ссылки. На общем устройстве (киоск/семейный ПК) профиль
// браузера сохранял действующие capability-URL прежнего владельца (до 7 дней).
//
// BR-301 — ЧТО БЫЛО. После успешного создания `created = true`, и шаблон рендерит блок
// `created` со СТАТИЧЕСКИМ `shareUrl`, скрывая блок `currentLink`. 30-секундный polling
// `loadCurrent()` обновляет только `currentLink` — но не `created`/`shareUrl`. Если ссылку
// перевыпустили или отозвали с другого устройства, модалка продолжала показывать мёртвый URL
// как активный (дата — из свежего `currentLink`, URL — старый).
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

export const SHARE_URL_STORAGE_PREFIX = 'karaoke-share-url:'

/**
 * Ключ хранения capability-URL.
 *
 * @param {number|string} linkId
 * @returns {string}
 */
export function shareUrlStorageKey(linkId) {
  return `${SHARE_URL_STORAGE_PREFIX}${linkId}`
}

/**
 * Хранилище для capability-URL.
 *
 * Используется `sessionStorage` (живёт только во вкладке), а НЕ `localStorage` (живёт в
 * профиле браузера месяцами). В закрытой вкладке секрет исчезает; при перезагрузке той же
 * вкладки — остаётся (удобно) и при этом не переживает закрытие браузера.
 *
 * Если sessionStorage недоступен (приватный режим, заблокированное хранилище) — возвращаем
 * `null`, и вызывающий код держит URL только в памяти компонента.
 *
 * @param {object} [win] — окно (для тестов)
 * @returns {Storage|null}
 */
export function resolveSecretStorage(win) {
  const target = win || (typeof globalThis !== 'undefined' ? globalThis : null)
  try {
    const storage = target && target.sessionStorage
    if (!storage) return null
    // Проба записи: sessionStorage может существовать, но бросать при setItem.
    const probe = '__km_share_probe__'
    storage.setItem(probe, '1')
    storage.removeItem(probe)
    return storage
  } catch (_) {
    return null
  }
}

/**
 * @param {Storage|null} storage
 * @param {number|string} linkId
 * @returns {string} сохранённый URL или ''
 */
export function readShareUrl(storage, linkId) {
  if (!storage) return ''
  try {
    return storage.getItem(shareUrlStorageKey(linkId)) || ''
  } catch (_) {
    return ''
  }
}

/**
 * @param {Storage|null} storage
 * @param {number|string} linkId
 * @param {string} url
 * @returns {boolean} true — сохранено
 */
export function saveShareUrl(storage, linkId, url) {
  if (!storage || !url) return false
  try {
    storage.setItem(shareUrlStorageKey(linkId), url)
    return true
  } catch (_) {
    return false
  }
}

/**
 * @param {Storage|null} storage
 * @param {number|string} linkId
 */
export function clearShareUrl(storage, linkId) {
  if (!storage) return
  try {
    storage.removeItem(shareUrlStorageKey(linkId))
  } catch (_) {
    /* ignore */
  }
}

/**
 * Удалить ВСЕ сохранённые capability-URL (logout / смена пользователя).
 *
 * @param {Storage|null} storage
 * @returns {number} сколько ключей удалено
 */
export function clearAllShareUrls(storage) {
  if (!storage) return 0
  let removed = 0
  try {
    const keys = []
    for (let i = 0; i < storage.length; i++) {
      const key = storage.key(i)
      if (key && key.startsWith(SHARE_URL_STORAGE_PREFIX)) keys.push(key)
    }
    for (const key of keys) {
      storage.removeItem(key)
      removed += 1
    }
  } catch (_) {
    /* ignore */
  }
  return removed
}

/**
 * Согласовать «созданную» ссылку с фактическим состоянием с сервера.
 *
 * @param {boolean} created — локальный флаг «ссылка только что создана»
 * @param {number|string|null} createdLinkId — id созданной ссылки
 * @param {object|null} currentLink — актуальная ссылка с сервера ({linkId, active, ...})
 * @returns {{created: boolean, stale: boolean}} stale — сервер отозвал/заменил ссылку
 */
export function reconcileCreatedLink(created, createdLinkId, currentLink) {
  if (!created) return { created: false, stale: false }
  // Сервер не отдал ссылку — её отозвали или она истекла. Показывать старый URL как активный нельзя.
  if (!currentLink) return { created: false, stale: true }
  // Ссылка перевыпущена (другой linkId) — показываем фактическое состояние, а не старый URL.
  if (
    createdLinkId != null &&
    currentLink.linkId != null &&
    String(createdLinkId) !== String(currentLink.linkId)
  ) {
    return { created: false, stale: true }
  }
  // Сервер прямо отметил ссылку неактивной.
  if (currentLink.active === false) return { created: false, stale: true }
  return { created: true, stale: false }
}
