## Дополнительные исправления (PR #496)

Владелец сообщил две проблемы после PR #495:

### 1. Бейдж показывает '0'

**Причина**: `promisedXMLHttpRequest` возвращает `xhr.responseText` (string), не
parsed object. Я делал `data?.queueSize` — для string response это `undefined` → `0`.

**Фикс** (ProcessWorker.vue):
```js
const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
size = parsed?.queueSize ?? 0
```

Для `countWaiting` backend возвращает Long: `JSON.parse('12345') === 12345`.
Для `queueSize` возвращает `{queueSize: N}`.

### 2. Реверс порядка обработки при всплытии

**Причина**: при reorder'е активные задачи вставали в начало дека, но в
обратном порядке по songId. Worker брал из головы → обработка начиналась с
**нижней** песни (max songId).

**Фикс** (StorageMetadataCache.setActiveSongIds):
```kotlin
for (r in active.sortedByDescending { it.songId }) {
    queue.addFirst(r)
}
```

`addFirst` вставляет в ГОЛОВУ. Итерируемся `[max, mid, min]` → конечный порядок
в голове: `[min, mid, max]`. Worker берёт из головы → сначала `min` → обработка
сверху вниз.

## PR #496 merged

CI 12/12 PASS. Оба фикса в master.
