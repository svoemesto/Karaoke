# Contract: `POST /api/songeditor/approve` (дельта для feature 184)

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md) | **Research**: [research.md](../research.md) D-1..D-4
**Base contract**: [../../094-fix-approve-news-failure/contracts/approve-endpoint.md](../../094-fix-approve-news-failure/contracts/approve-endpoint.md)

Базовый контракт (`SongEditorController.approve`, `SongEditorController.kt:312-487`,
зафиксирован в `specs/094-fix-approve-news-failure`) **сохраняется целиком**.
Этот документ фиксирует **только дельту** — что добавляется, что остаётся
как было.

## Изменения в Request

```diff
 POST /api/songeditor/approve
 Content-Type: application/x-www-form-urlencoded

 id=<Long>             # id записи tbl_song_assignments
 target=remote|<пусто> # опционально; "remote" — читать/писать assignment на Connection.remote()
+idStatus=5|6|<пусто> # NEW: финальный статус песни при апруве; пусто = 6 (backward-compatible)
```

### Семантика `idStatus`

| Значение | Смысл | Поведение |
|---|---|---|
| **отсутствует** или `null` | дефолт — backward-compatible | статус = `6`, render + sync related запускаются (как сегодня) |
| **`5`** | «Маркеры проверены» (канон из `specs/022-song-status-lifecycle`, label `MARKERS_VERIFIED`) | статус = `5`, render и sync related **пропускаются**; push песни — как обычно |
| **`6`** | «Готово» | эквивалентно отсутствию (явный выбор того же дефолта) |
| **любое другое** (`4`, `7`, `"abc"`, `-1`) | невалидное | **HTTP 400** с телом `{"ok": false, "status": "error", "error": "invalid_idstatus: must be 5 or 6"}` |

Невалидное значение проверяется **после** short-circuit `already_approved` и
**до** записи в БД (data-model INV-2). Лог в этом случае:
`[approve/feature-184] INVALID idStatus=<value> for assignmentId=<id>`.

### Валидация выполняется в порядке:

1. `id` — Spring `Long?` (nullable, но на практике обязателен — сегодня нет
   дефолта; не трогаем).
2. `aRead.adminStatus == APPROVED` → `already_approved` (как сегодня,
   `SongEditorController.kt:333-335`) — **ДО** валидации `idStatus`.
3. **NEW** `idStatus` ∈ {5, 6, null} → иначе 400 `invalid_idstatus`.

## Изменения в Response

Существующие коды — без изменений (см. базовый контракт specs/094):

```jsonc
// success — поле idStatus теперь ФАКТИЧЕСКОЕ (а не всегда 6):
{"ok": true, "status": "success", "idStatus": <фактический id_status песни>}

// already_approved — без изменений:
{"ok": true, "status": "already_approved"}

// error — без изменений (коды из specs/094):
{"ok": false, "status": "error", "error": "<код>"}
```

**Новое в error-ветке**: код `"invalid_idstatus"` (HTTP 400). В лог:
`[approve/feature-184] INVALID idStatus=<value> for assignmentId=<id>`.

**Изменение в success-ветке**: поле `idStatus` теперь возвращает **фактическое**
значение после применения (а не запрошенное). Это уже так
(`SongEditorController.kt:477` возвращает `song.idStatus`), но фича явно
фиксирует инвариант, потому что UI использует его для сообщения и read-only
бейджа (FR-012).

## Гейты (новое поведение в бэкенд-логике)

Пусть `T` = запрошенный `idStatus` (`null` → `6`),
`C` = текущий `song.idStatus` до апрува,
`A` = фактический после применения (`max(C, T)`).

| Блок кода (сегодня) | Строка | Условие гейта (после) | Логи |
|---|---|---|---|
| `triggerRenderMp4DemoIfNeeded(song)` | `SongEditorController.kt:427` | `A >= 6L` | `[approve/feature-184] render-demo SKIPPED for songId=NN reason=idStatus=5` |
| `thread { updateRemoteDatabaseFromLocalDatabase(...) }` | `SongEditorController.kt:435-456` | `A >= 6L` | `[approve/feature-184] sync-related SKIPPED for songId=NN reason=idStatus=5` |
| `updateRemoteSongFromLocalDatabase(song.id)` | `SongEditorController.kt:401` | **без гейта** (всегда, research D-3) | (без изменений) |

**Причина гейта по `A`, а не по `T`**: при `T=5, C=6` (downgrade-ignore) песня
по факту финальна, и конвейер должен сработать — иначе регрессия в единственном
не-5-кейсе (research D-2, таблица 4-х вариантов).

**Причина push без гейта**: одобренная разметка редактора (маркеры/текст/`.srt`)
должна попасть на PROD при любом выборе — иначе смысл апрува теряется (research D-3).
Безопасность: `id_status=5` на сервере не делает песню доступной (`isContentReady`
требует `>= 6`, `Song.kt:1132-1139`).

## Логирование (US3 — observability)

Обязательные строки в логах `karaoke-app`:

```text
[approve/feature-184] songId=NN idStatus=5 reason=manual_choice
[approve/feature-184] songId=NN idStatus=6 reason=default
[approve/feature-184] idStatus downgrade IGNORED songId=NN current=6 requested=5
[approve/feature-184] render-demo SKIPPED for songId=NN reason=idStatus=5
[approve/feature-184] sync-related SKIPPED for songId=NN reason=idStatus=5
[approve/feature-184] news SKIPPED for songId=NN reason=idStatus=5
[approve/feature-184] INVALID idStatus=<value> for assignmentId=<id>
[approve/timing] push на SERVER: NN ms, created=… updated=…
```

Префикс `feature-184` — стабильный тег для фильтрации при разборе инцидентов.

## Инварианты контракта (проверяются в quickstart.md)

Дополнительно к общим 6 инвариантам ([README.md](./README.md)):

- **INV-A1**: запрос без `idStatus` (старый клиент) ⇒ `id_status = 6` ⇒ рендер
  + sync related + push песни (идентично сегодняшнему поведению, SC-003).
- **INV-A2**: запрос с `idStatus=5` ⇒ фактический `id_status = 5` (если `C <= 5`)
  или `C` (если `C > 5`); рендер и sync related пропускаются в первом случае
  и выполняются во втором (как в D-2 таблица).
- **INV-A3**: запрос с невалидным `idStatus` ⇒ HTTP 400 + `error: "invalid_idstatus"`,
  БД не изменяется (assignment `adminStatus` не становится `approved`).
- **INV-A4**: `idStatus` в success-ответе равен фактическому `song.idStatus`
  в БД сразу после ответа (проверяется прямым `SELECT` в той же БД).
- **INV-A5**: повторный клик по одобренному заданию (любой `idStatus`) ⇒
  `already_approved` без `idStatus` в ответе, без побочных эффектов.
- **INV-A6**: при `T=5` в логах присутствует строка `reason=idStatus=5` для
  render-demo и/или sync-related (для песен в `C < 6`).
