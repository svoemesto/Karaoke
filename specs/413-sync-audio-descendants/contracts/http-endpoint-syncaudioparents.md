# HTTP Contract: `POST /api/utils/syncaudioparents`

**Branch**: `413-sync-audio-descendants`
**Date**: 2026-09-19
**Spec**: `../spec.md`
**Backend**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`
**Vuex action**: `webvue3/src/components/Songs/store.js` → `syncAudioParentsPromise`

## Назначение

Запустить фоновую массовую синхронизацию всех аудио-потомков: перебрать
потомков (`audio_parent_id <> 0`, `id_status < 6`), сгруппировать по родителю,
каждого уникального родителя обработать один раз (последовательно). По завершении
— SSE-уведомление со сводкой. Аналог существующего
`POST /api/utils/customfunction` / `findaudioparentforauthor`.

---

## Запрос

`POST /api/utils/syncaudioparents`

### Параметры

Нет. Тело — пустое.

### Примеры

```
POST /api/utils/syncaudioparents
```

---

## Ответ

### `200 OK` — запущено в фоне

Тело: строка `"OK"`.

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

OK
```

### `200 OK` — уже идёт (любой из проходов: auto или admin)

Тело: строка `"ALREADY_RUNNING"`.

```http
HTTP/1.1 200 OK
Content-Type: text/plain;charset=UTF-8

ALREADY_RUNNING
```

### Ошибка

`500` — только при необработанном исключении (не ожидается: фон). Ошибки на
отдельных песнях не влияют на HTTP-ответ — они идут в лог и в сводку.

---

## SSE-уведомление по завершении

Канал — как у `customFunction` (`SNS.send(SseNotification.message(Message(...)))`):

- **head**: `"Синхронизация аудио-потомков"`
- **body**: `"Обработано родителей N, потомков M: синхронизировано X, пропущено Y"`
- тип: `info`.

---

## Фронт

- **Кнопка**: `webvue3/src/views/HomeView.vue` — «Синхронизировать аудио-потомков
  (Custom Function)», рядом с «Поиск родителей и аудио-родителей».
- **Модалка подтверждения**: `CustomConfirm` (как у `customFunction`), тело
  описывает: перебираются аудио-потомки (`audio_parent_id <> 0`, статус <6),
  для каждого родителя ≥5 идёт повторная аудио-сверка (порог 95 %), при успехе
  потомок получает текст/маркеры/форматирование со сдвигом и статус 5; операция
  тяжёлая, идёт в фоне, итог — уведомлением.
- **Vuex action**: `syncAudioParentsPromise()` — POST без параметров.
- **Тост**: `"OK"` → info «запущено в фоне»; `"ALREADY_RUNNING"` → warning
  «уже запущено — дождитесь завершения».

---

## Ограничения и краевые случаи

- Одновременно может идти только один проход (auto-очередь или admin) —
  общий single-flight (FR-021).
- Родители со статусом <5 на момент обработки пропускаются с логом.
- Потомки со статусом ≥6 пропускаются.
- Потомки с активным `KaraokeProcess` пропускаются с логом.
- Никакого push на прод (FR-022).
