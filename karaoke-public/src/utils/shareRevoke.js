// Подтверждение отзыва share-ссылки сервером.
//
// BR-295 (аудит #336, пакет PKG-S2-14, OpenProject #377): `revoke()` в
// ShareLinkModal.vue безусловно обнулял currentLink/created/shareUrl и вызывал
// clearSavedUrl(). `authPost` резолвит промис на ЛЮБОМ `onload`, поэтому 401
// (истёкший 30-дневный JWT) и 5xx тоже «отзывали» ссылку в UI: единственная
// сохранённая копия URL удалялась из localStorage, а на бэкенде ссылка оставалась
// active=true и продолжала давать полный доступ к премиум-плееру.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * @param {number} status — HTTP-статус ответа revoke
 * @param {any} body — тело ответа
 * @returns {boolean} true только если сервер явно подтвердил отзыв ({revoked: true})
 */
export function isRevokeConfirmed(status, body) {
  return status === 200 && !!body && body.revoked === true
}
