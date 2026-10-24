// BR-088 (аудит #336, пакет PKG-S2-17, OpenProject #380).
//
// Очередь автосохранения карточки песни.
//
// ДЕФЕКТ (до правки). В `SongEdit.vue` стояло:
//
//   async executeSave() {
//     if (this.isSaving) return      // <-- вызов ПРОСТО ОТБРАСЫВАЛСЯ
//     this.isSaving = true
//     ...
//   }
//
// Пока шёл запрос `POST /api/song/update`, любой новый вызов сохранения исчезал.
// Поэтому принудительный flush перед сменой песни или размонтированием карточки мог
// не выполниться — правка, сделанная менее секунды назад, оставалась только в памяти
// вкладки. `snapshotSong` при переходе перезаписывался, `diff` обнулялся, отложенный
// таймер срабатывал уже на пустом diff, а локальная копия песни в `songPages` выглядела
// сохранённой — после F5 правка исчезала.
//
// РЕШЕНИЕ. Очередь: вызов во время идущего запроса запоминается и выполняется сразу
// после текущего (а не отбрасывается). `whenIdle()` позволяет дождаться и текущего, и
// поставленного в очередь — это нужно `flushPendingSave()` перед навигацией.
//
// Модуль не знает ничего про Vue: это позволяет проверить поведение обычным
// node-тестом (`src/lib/__tests__/autosave-queue.test.js`).

/**
 * Создаёт очередь последовательных асинхронных задач (одна в работе, одна в ожидании).
 *
 * @returns {{
 *   run: function(function(): Promise<any>): Promise<any>,
 *   whenIdle: function(): Promise<void>,
 *   isRunning: function(): boolean,
 *   hasQueued: function(): boolean,
 * }}
 */
export function createAutosaveQueue() {
  let running = null
  let pending = null

  const run = (task) => {
    if (running) {
      // Задача уже идёт — новая не теряется, а ждёт своей очереди.
      pending = task
      return running
    }
    const promise = (async () => {
      try {
        return await task()
      } finally {
        running = null
        const next = pending
        pending = null
        if (next) {
          // Задача сама обрабатывает свои ошибки (в SongEdit это try/catch с тостом),
          // поэтому «хвост» запускаем без ожидания и без unhandled rejection.
          void run(next)
        }
      }
    })()
    running = promise
    return promise
  }

  return {
    run,
    /**
     * Ждёт завершения текущей задачи и всех поставленных в очередь.
     *
     * @returns {Promise<void>}
     */
    async whenIdle() {
      while (running) {
        try {
          await running
        } catch (_error) {
          // Ошибку обработал вызывающий; очередь от этого не ломается.
        }
      }
    },
    isRunning() {
      return running !== null
    },
    hasQueued() {
      return pending !== null
    },
  }
}
