## Критический фикс: drainTo повреждал ThreadPoolExecutor (PR #497)

Владелец сообщил:
> Бэк перестал запускать задания из пула, только добавляет их и "сортирует".
> Движения по работе с заданиями нет.

### Причина

В PR #494 (revert + redo) я использовал `queue.drainTo(all)` в `setActiveSongIds` для
атомарного reorder. **`drainTo` конфликтует с внутренним `getTask()` `ThreadPoolExecutor`** —
worker'ы перестают получать уведомления о новых задачах и «теряют» очередь.

### Фикс

- `StorageMetadataCache.setActiveSongIds`: убран `drainTo` + rebuild. Возвращён
  `pollFirst/addFirst/addLast` в цикле — стандартный безопасный способ для
  `LinkedBlockingDeque`.
- `StorageMetadataCache.init { cacheFillerExecutor }`: eager инициализация executor'а
  при создании Spring bean. Без этого worker'ы создаются только при первом submit'е.

CI 12/12 PASS на PR #497.
