// Анонимный пер-браузерный идентификатор, общий для всего трекинга и атрибуции просмотров
// страниц. Вынесен в отдельный модуль (а не в tracking.js), чтобы им мог пользоваться и api.js
// без циклической зависимости api.js ↔ tracking.js. Ключ localStorage — тот же 'kp_cid', что
// исторически использовался жестом разблокировки плеера (не переименовывать).
export function getAnonId() {
  const KEY = 'kp_cid'
  let id = localStorage.getItem(KEY)
  if (!id) {
    id = crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
    localStorage.setItem(KEY, id)
  }
  return id
}
