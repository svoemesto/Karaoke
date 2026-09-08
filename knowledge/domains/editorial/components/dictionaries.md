# Component: dictionaries

> **Домен**: [editorial](../domain.md)
> **Компонент**: централизованное хранение магических кодов редакторского
> домена: `ApprovalStatus`, `TargetIdStatus`, error codes.

## Ответственность | Responsibility

В editorial-домене часто возникают ситуации, когда:

- статус ревью (`PENDING`/`APPROVED`/`REJECTED`) кочует между API и БД;
- целевой `idStatus` (5 или 6) определяет, какой pipeline запустится;
- error codes (`song_already_taken`, `song_not_found`) возвращаются
  из разных эндпоинтов.

Эта компонента — единственное место, где эти константы определены.
Использование литералов в L3-спецификациях и коде без ссылки на эту
страницу — **критический дефект** (см. `audit-living-docs` «Magic Codes»).

## Интерфейсы и Контракты | Interfaces and Contracts

### `ApprovalStatus` — статус ревью

| Значение | Описание |
| --- | --- |
| `PENDING` | Ревью создано, ожидает админа. |
| `APPROVED` | Админ одобрил, pipeline запущен. |
| `REJECTED` | Админ отклонил. |

**Место определения**: `karaoke-app/.../model/ApprovalStatus.kt`.

**Использование**:

- В БД: колонка `tbl_review_tasks.status` (varchar).
- В API: поле `ReviewTaskDTO.status`.

**Запрещено**: строковые литералы `"PENDING"`/`"APPROVED"`/`"REJECTED"`.

### `TargetIdStatus` — целевой статус после апрува

| Значение | Pipeline | Render Version |
| --- | --- | --- |
| `5` | LYRICS + KARAOKE рендер | `RenderVersion.LYRICS`, `KARAOKE` |
| `6` | DEMO рендер + Telegram-новость | `RenderVersion.DEMO` |

**Место определения**: `karaoke-app/.../model/TargetIdStatus.kt`.

**Связь с [catalog domain](../../catalog/domain.md)**: `idStatus=5`
означает `RENDERED`, `idStatus=6` — `APPROVED` (см.
[song-lifecycle](../../catalog/components/song-lifecycle.md)).

**Запрещено**: числовые литералы `5`/`6` в коде (вместо —
`TargetIdStatus.RENDERED` / `TargetIdStatus.APPROVED`).

### Error codes

| Код | HTTP | Когда |
| --- | --- | --- |
| `song_already_taken` | 409 | Попытка взять чужое задание. |
| `song_not_found` | 404 | Песня не существует. |
| `user_not_editor` | 403 | У пользователя нет `canSelfAssign=true`. |
| `assignment_not_found` | 404 | Задание не существует (для revoke). |
| `assignment_not_mine` | 403 | Попытка revoke чужого задания. |
| `review_already_done` | 409 | Повторный approve/reject. |

**Место определения**: константы в `PublicSongEditorController.kt`,
`AdminReviewTaskController.kt`.

**Запрещено**: расхождение error code между контроллером и документацией.
Любое изменение error code = обновить эту таблицу.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм выбора `TargetIdStatus` при approve

```kotlin
fun resolveTargetIdStatus(request: ApprovalRequest): TargetIdStatus = when {
    request.demoMode -> TargetIdStatus.APPROVED    // idStatus=6, DEMO + Telegram
    else -> TargetIdStatus.RENDERED               // idStatus=5, LYRICS + KARAOKE
}
```

**[WARN]** Если `demoMode=false` и `targetIdStatus=APPROVED` —
рассинхрон, идемпотентность не гарантирована.

### Алгоритм проверки `canSelfAssign`

```kotlin
fun canSelfAssign(user: SiteUser, song: Song): Boolean {
    if (!user.canSelfAssign) return false           // см. identity domain
    if (song.publishDate?.isAfter(Instant.now()) == true) return false  // нельзя брать пре-релиз
    return true
}
```

Пре-релиз песни (publishDate в будущем) **нельзя** брать в работу —
это защищает от случайной публикации.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `EditorAssignment`, `ReviewTask`.
- → [identity](../identity/domain.md) — `SiteUser.canSelfAssign`.
- → [catalog domain](../../catalog/domain.md) — `Song.idStatus`,
  `Song.publishDate`.
- → [rendering dictionaries](../../rendering/components/dictionaries.md) —
  `RenderVersion`.

## Связанные ADR | Related ADRs

- [0001-raw-jdbc](../../adr/0001-raw-jdbc.md) — БД для хранения.
