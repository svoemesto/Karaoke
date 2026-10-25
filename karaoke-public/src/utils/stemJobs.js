// BR-362 (аудит #336, пакет PKG-S3-28, OpenProject #400): список заданий стемов.
//
// ЧТО БЫЛО. Удаление карточки — оптимистичное и только в локальном массиве (`setTimeout`
// фильтрует `jobs` через 300 мс), а любой следующий `load(true)` заменял `jobs` ответом
// сервера целиком. Серверная строка удалённого задания живёт до фонового cleanup
// (раз в 5 минут, `StemJobPollScheduler.cleanup`), поэтому при наличии другого
// WAITING/WORKING задания ближайший поллинг (≤7 с) возвращал карточку назад —
// с `deleteRequested=true` («Удаляется...», кнопка disabled).
//
// ЧТО СТАЛО. Задания, помеченные к удалению, не показываются в списке.
//
// Модуль без зависимостей — тест запускается node --test без node_modules.

/**
 * Отфильтровать задания, помеченные к удалению.
 *
 * @param {Array<{deleteRequested?: boolean}>} jobs — ответ `/api/public/account/stemjobs/list`
 * @returns {Array} задания, которые ещё не удалены (исходный массив не мутируется)
 */
export function visibleJobs(jobs) {
  const list = Array.isArray(jobs) ? jobs : []
  return list.filter((job) => !(job && job.deleteRequested === true))
}

/**
 * Есть ли активные задания (нужен ли опрос списка).
 *
 * @param {Array<{status?: string}>} jobs — задания
 * @returns {boolean}
 */
export function hasActiveJobs(jobs) {
  const list = Array.isArray(jobs) ? jobs : []
  return list.some((job) => job && (job.status === 'WAITING' || job.status === 'WORKING'))
}
