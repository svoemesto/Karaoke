# Contract: NDJSON-стрим `/api/public/zakroma/stream` — изменение ритмики

**Spec:** [spec.md](../spec.md) | **Plan:** [plan.md](../plan.md) | **Date:** 2026-08-14

## Что НЕ меняется

Контракт NDJSON **полностью обратно совместим** со спекой 181:

- **Endpoint**: `GET /api/public/zakroma/stream`
- **Content-Type**: `application/x-ndjson`
- **Query-параметры**: `author`, `expectedCount` (опц.), `anonId` (опц.), `referrer` (опц.)
- **5 типов сообщений**: `meta` / `album` / `song` / `done` / `error`
- **Структура каждого сообщения**: та же (`ZakromaStreamMessageDto` без изменений полей)

Любой клиент, написанный под спеку 181, продолжит работать без изменений.

## Что меняется

**Ритмика отправки сообщений**: с «по 1 сообщению с явным flush после каждого» на **«album сразу + до 50 song в буфере с одним flush на пачку»**.

### До (спека 181)

```
[meta + flush]
[album + flush]
[song + flush]
[song + flush]
[song + flush]
... (×2500 для крупного автора)
[done + flush]
```

**Характеристики**:
- 1 NDJSON-строка = 1 системный вызов `write` + 2 flush (`writer.flush()` + `out.flush()`).
- На 2500 песен: 2532 write + 5064 flush.
- Backend latency: 0.5-1.5 сек (R1: N+1 SQL) + 0.5-1 сек (flush overhead) = **1-2.5 сек**.

### После (спека 186)

```
[meta + flush]
[album без flush]
[song в буфер]
[song в буфер]
... (до 50 song в StringBuilder)
[write buffer (50 song) + flush]
[album без flush]
[song в буфер]
...
[write buffer (50 song) + flush]
[write remaining buffer + done + flush]
```

**Характеристики**:
- 1 `writer.write(StringBuilder.toString())` на пачку (50 песен).
- 1 `out.flush()` на пачку.
- На 2500 песен / 30 альбомах: 1 (meta) + 30 (album без flush) + 50 (batch flush × 50 song) + 1 (done) = **82 flush** (вместо 5064).
- Backend latency: 0.2-0.5 сек (R1: batch SQL) + 0.1-0.3 сек (batched flush) = **0.3-0.8 сек**.

## Совместимость с клиентом

### Клиент НЕ ДОЛЖЕН полагаться на:
- ❌ «После `meta` сразу приходит `album`» — `album` может быть отложен на пару мс из-за `out.flush()` пачки.
- ❌ «После `album` сразу приходит ≥ 1 `song`» — порядок тот же, но пауза между ними может быть больше (пачка буферизуется).
- ❌ «Между `song` есть видимая пауза» — теперь паузы нет, frontend должен сам yield'ить для рендера.

### Клиент ДОЛЖЕН:
- ✅ Обрабатывать сообщения по мере поступления (`reader.read()`).
- ✅ Быть готов к тому, что несколько сообщений приходят в одном TCP-чанке (parse split по `\n`).
- ✅ Обновлять `receivedCount.value` на каждое `song` (как было в спеке 181).

### Гарантии сохранены

- `meta` **всегда первый** (фронт узнаёт `expectedCount` до получения данных).
- `done` **всегда последний** (содержит `actualCount` для sanity check).
- `error` приходит **вместо `done`**, если backend не смог загрузить данные (HTTP 200, не 500 — иначе fetch не сможет прочитать тело).
- Порядок: `meta → album → song(s) → album → song(s) → ... → done`. **Не** перемешивается (стрим линеен).

## Изменения в DTO

**Нет новых полей** в `ZakromaStreamMessageDto`, `ZakromaAlbumMetaPublicDto`, `ZakromaAlbumSongPublicDto`. Контракт обратно совместим.

## Nginx-конфиг

**Не меняется** в этом PR. Текущий конфиг (`deploy/80to8897`) для `/api/public/zakroma/stream`:

```nginx
location /api/public/zakroma/stream {
    proxy_buffering off;
    gzip off;
    proxy_read_timeout 300s;
}
```

**Замечание**: с batched flush nginx может буферизовать чуть больше (чанки по 50 song ≈ 8-15 KB JSON). Это в пределах дефолтного `proxy_buffer_size 4k` × 8 buffers = 32 KB, должно вместиться. Если нет — увеличить `proxy_buffer_size 16k` (но это out of scope для этой фичи — backlog).

## Миграция клиентов

**Клиенты Закромов** (`karaoke-public`):
- `useZakromaStreamProgress.js` — обновляется в том же PR (см. research.md R3).
- Других клиентов стрима нет (публичный endpoint используется только Vuex-action `loadZakromaStream`).

**Внешние потребители** (если есть): никаких. Endpoint не публикуется для третьих сторон.

## Метрики успеха

| Метрика | До (спека 181) | После (спека 186) | Критерий |
|---------|----------------|-------------------|----------|
| Backend latency (N=2500 песен, localhost) | 1-2.5 сек | 0.3-0.8 сек | Улучшение ×2-5 |
| Число flush на стрим | 5064 | 82 | Улучшение ×62 |
| Frontend perceived latency (N=2500 песен) | 17 сек (5+12) | 3-5 сек | Улучшение ×3-5 |
| Tab-switching bug | Да (прогрессометр не сдвигается) | Нет (queueMicrotask + visibilitychange) | FR-005, FR-006 |

## См. также

- [research.md](../research.md) — детальный анализ узких мест R1-R5 и обоснование решений.
- [spec.md](../spec.md) — функциональные требования FR-001..FR-010 и success criteria SC-001..SC-006.
- [docs/features/zakroma-stream-progress.md](../../../docs/features/zakroma-stream-progress.md) — per-feature документ (обновляется в этом PR).