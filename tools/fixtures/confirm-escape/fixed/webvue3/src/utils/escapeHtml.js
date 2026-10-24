/**
 * ФИКСТУРА (исправленная версия) — спека #558, OpenProject #360, BR-123.
 *
 * Минимальная копия канонического модуля экранирования: фикстура должна
 * разбираться тем же guard'ом, что и настоящий код.
 */
export function escapeHtml(text) {
  return String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
