# Contract: `POST /api/songeditor/approve`

**Feature**: [spec.md](../spec.md) | **Date**: 2026-07-30

Существующий endpoint (`SongEditorController.kt:312`), контракт уточняется
(не меняется маршрут/метод/параметры) — уточняется ФОРМА ОТВЕТА, чтобы она
всегда была валидным JSON, различающим три исхода из `data-model.md`
(`ApproveOutcome`).

## Request

Без изменений:

```
POST /api/songeditor/approve
Content-Type: application/x-www-form-urlencoded

id=<Long>            # id записи tbl_song_assignments
target=remote|<пусто> # опционально; "remote" — читать/писать assignment на Connection.remote()
```

## Response — ДО фикса (текущее поведение, зафиксировано как baseline)

| Ветка | HTTP | Тело |
|---|---|---|
| `assignment_not_found` / `draft_not_found` / `song_not_found` / `bad_markers` | 200 | `{"ok": false, "error": "<code>"}` |
| Успех (весь блок 320-398 отработал без исключений) | 200 | `{"ok": true, "idStatus": <Long>}` |
| Необработанное исключение (в частности — из `aRead.save()`, см. research.md п. 2) | **5xx, тело не гарантировано валидным JSON** | — источник симптома «Ошибка запроса» |

## Response — ПОСЛЕ фикса (контракт этой фичи)

Все три исхода возвращаются с HTTP 200 (сам факт того, что сервер ответил и
понял запрос, не является ошибкой транспорта — ошибка бизнес-уровня
передаётся полем `status`/`ok`, как и в остальных ручках этого контроллера
— `reject`/`delete`/`revoke` уже следуют этому паттерну). Исключения,
которые сегодня могут дойти до Spring необработанными (`aRead.save()`),
ДОЛЖНЫ перехватываться и превращаться в ветку `error` ниже.

```jsonc
// status: "success" — FR-001. Локальное применение к Song УДАЛОСЬ и
// admin_status реально сохранён как approved.
{
  "ok": true,
  "status": "success",
  "idStatus": 6
}

// status: "already_approved" — FR-002/FR-006. Задание уже было approved
// до этого клика — повторный клик не переприменяет изменения.
{
  "ok": true,
  "status": "already_approved"
}

// status: "error" — FR-003. Существовавшие штатные ошибки (assignment_not_found
// и т.п.) ДОЛЖНЫ сохранить свои существующие значения "error" (обратная
// совместимость по кодам ошибок для уже существующих потребителей), плюс
// новый код для ранее-необработанного случая записи статуса.
{
  "ok": false,
  "status": "error",
  "error": "assignment_not_found" // | "draft_not_found" | "song_not_found" |
                                    // "bad_markers" | "save_failed" (новый —
                                    // см. research.md п.2, замена
                                    // необработанного исключения)
}
```

**Обратная совместимость**: поле `ok` сохраняется (существующие
потребители, если появятся, продолжат работать по нему); `status` —
дополнение, не замена. `idStatus` в ветке `success` — как и раньше.

## Инварианты контракта (проверяются в quickstart.md)

1. Любой ответ на `POST /api/songeditor/approve` — валидный JSON с HTTP
   200 (или уже существующими штатными 4xx-кодами `Spring`
   auth/routing-уровня, не связанными с бизнес-логикой апрува — не
   меняются). HTTP 5xx из-за необработанного исключения внутри бизнес-логики
   недопустим (SC-001, SC-003 spec.md).
2. `status: "success"` в ответе ⇒ `SELECT admin_status FROM
   tbl_song_assignments WHERE id = <id>` (в БД, из которой было прочитано
   задание) возвращает `approved` сразу после ответа (инвариант
   `data-model.md`).
3. `status: "error"` в ответе (после того, как локальное применение к
   `Song` уже могло начаться) ⇒ `admin_status` НЕ становится `approved`
   этим вызовом (см. FR-003 spec.md, data-model.md state machine).
4. Появление новости (`tbl_news`/`tbl_song_news_announced`) НЕ входит в
   контракт синхронного ответа этого эндпоинта — оно зависит только от
   фактического состояния боевой копии `Song` (FR-004 spec.md), может
   произойти уже ПОСЛЕ того, как ответ на `approve()` отправлен (push и
   `checkAndAnnounce()` остаются best-effort, см. Clarifications).
