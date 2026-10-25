// Общий помощник для загрузки списков в сторах webvue3.
//
// Контекст: аудит #336, волна E, пакет PKG-S3-31 (OpenProject #403) — записи
// BR-363/BR-364 (альбомы), BR-373 (авторы), BR-383 (история прослушиваний),
// BR-391 (картинки), BR-392/BR-393 (новости).
//
// ДЕФЕКТ (один на все пять сторов). Загрузка списка была «без владения»:
// (1) промис не возвращался — `await dispatch(...)` завершался до ответа;
// (2) флаг загрузки снимался только в успешной ветке — при 500/таймауте/обрыве сети
//     таблица навсегда оставалась в состоянии «Loading...» (обход — только F5);
// (3) ошибка уходила только в консоль — пользователь не видел отказа;
// (4) ответ применялся безусловно — при двух перекрывающихся загрузках выигрывал
//     ПОСЛЕДНИЙ ПРИШЕДШИЙ ответ, а не последний запрошенный: таблица показывала
//     строки старой страницы/старого фильтра, а пагинатор и селекторы — новые.
//
// РЕШЕНИЕ. Единый помощник: поколение запроса (побеждает последний запрос),
// флаг загрузки снимает только владелец поколения, `finally` снимает его при любой
// ошибке, текст ошибки кладётся в state, чтобы UI мог его показать.
//
// ПОЧЕМУ ЗДЕСЬ, А НЕ В КАЖДОМ СТОРЕ. Пять сторов страдали от одного и того же
// механизма; копирование пяти почти одинаковых функций — это пять мест, где
// следующий дефект появится снова. Здесь поведение задано один раз и покрыто
// регрессионными тестами.

/**
 * Загружает список с поколением запроса.
 *
 * @param {Object} params
 * @param {function(Object): Promise<string>} params.request — транспорт (XHR).
 * @param {function(string, any): void} params.commit — коммит мутации.
 * @param {{next: function(): number, isCurrent: function(number): boolean}} params.generation
 * @param {Object} params.requestOptions — параметры запроса ({method, url, params}).
 * @param {string} params.loadingCommit — мутация флага загрузки.
 * @param {string} params.errorCommit — мутация текста ошибки.
 * @param {string} params.applyCommit — мутация, применяющая разобранный ответ.
 * @param {string} params.errorMessage — текст ошибки по умолчанию.
 * @returns {Promise<{applied: boolean, stale: boolean, result?: Object, error?: Error}>}
 */
export function loadListWith({
  request,
  commit,
  generation,
  requestOptions,
  loadingCommit,
  errorCommit,
  applyCommit,
  errorMessage,
}) {
  const ticket = generation.next()
  commit(loadingCommit, true)
  commit(errorCommit, '')
  return request(requestOptions)
    .then((data) => {
      if (!generation.isCurrent(ticket)) return { applied: false, stale: true }
      const result = JSON.parse(data)
      commit(applyCommit, result)
      commit(errorCommit, '')
      return { applied: true, stale: false, result }
    })
    .catch((error) => {
      if (!generation.isCurrent(ticket)) return { applied: false, stale: true }
      // Ошибку видно в UI, а не только в консоли.
      commit(errorCommit, (error && error.message) || errorMessage)
      // eslint-disable-next-line no-console
      console.log(error)
      return { applied: false, stale: false, error }
    })
    .finally(() => {
      if (generation.isCurrent(ticket)) commit(loadingCommit, false)
    })
}
