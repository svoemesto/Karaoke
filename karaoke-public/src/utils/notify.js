// Общий нотификатор (тосты) для публичного фронтенда.
//
// BR-297 / BR-336 / BR-342 / BR-343 (аудит #336, пакет PKG-S3-27, OpenProject #399):
// ошибки действий (корзина, стем, плейлист, новости) НЕЛЬЗЯ проглатывать молча — пользователь
// должен видеть, что действие не удалось. Единый помощник, чтобы не плодить копипаст
// `$bvToast`-логики по компонентам.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

const KIND_META = {
  success: { title: 'Готово', variant: 'success' },
  warning: { title: 'Внимание', variant: 'warning' },
  error: { title: 'Ошибка', variant: 'danger' },
  info: { title: 'Готово', variant: 'success' },
}

/**
 * Показать сообщение пользователю.
 *
 * @param {object|null} vm — компонент Vue (нужен для `$bvToast`); может быть null
 * @param {string} message — текст
 * @param {'success'|'warning'|'error'|'info'} [kind]
 * @returns {boolean} true — сообщение показано тостом; false — показан alert
 */
export function notify(vm, message, kind = 'info') {
  if (!message) return false
  const meta = KIND_META[kind] || KIND_META.info
  const toast = vm && vm.$bvToast
  if (toast && typeof toast.toast === 'function') {
    toast.toast(message, {
      title: meta.title,
      variant: meta.variant,
      solid: true,
      noAutoHide: false,
      autoHideDelay: 4000,
    })
    return true
  }
  // Фоллбэк (нет bootstrap-vue-next или вызов вне компонента) — как в SongView.notify().
  if (typeof globalThis !== 'undefined' && typeof globalThis.alert === 'function') {
    globalThis.alert(message)
    return false
  }
  return false
}
