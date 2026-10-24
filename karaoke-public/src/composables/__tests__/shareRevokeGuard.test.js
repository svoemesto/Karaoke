// BR-295 (аудит #336, пакет PKG-S2-14, OpenProject #377): регрессионный тест на
// «отзыв share-ссылки подтверждён сервером».
//
// ЧТО БЫЛО. `revoke()` в karaoke-public/src/components/ShareLinkModal.vue ждал
// revokeShareLink() и безусловно обнулял currentLink/created/shareUrl + вызывал
// clearSavedUrl(). `authPost` резолвит промис на ЛЮБОМ `onload`, поэтому 401
// (истёкший 30-дневный JWT) и 5xx тоже «отзывали» ссылку в UI: единственная
// сохранённая копия URL удалялась из localStorage, а на бэкенде ссылка оставалась
// active=true и продолжала давать полный доступ к премиум-плееру.
//
// ЧТО ПРОВЕРЯЕТСЯ. Локальное «отозвано» допустимо только при явном подтверждении
// сервера: status === 200 И body.revoked === true.
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { isRevokeConfirmed } from '../../utils/shareRevoke.js'

describe('BR-295: отзыв share-ссылки подтверждается только ответом сервера', () => {
  test('200 + {revoked:true} — отзыв подтверждён', () => {
    assert.equal(isRevokeConfirmed(200, { revoked: true }), true)
  })

  test('401 (истёкший JWT) — НЕ подтверждён (ссылка на сервере жива)', () => {
    assert.equal(isRevokeConfirmed(401, { revoked: true }), false)
  })

  test('500 — НЕ подтверждён', () => {
    assert.equal(isRevokeConfirmed(500, null), false)
  })

  test('200 без revoked:true (старый бэкенд/пустое тело) — НЕ подтверждён', () => {
    assert.equal(isRevokeConfirmed(200, null), false)
    assert.equal(isRevokeConfirmed(200, {}), false)
    assert.equal(isRevokeConfirmed(200, { revoked: false }), false)
    assert.equal(isRevokeConfirmed(200, { revoked: 'true' }), false)
  })
})
