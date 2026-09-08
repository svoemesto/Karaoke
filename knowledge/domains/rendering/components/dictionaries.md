# Component: dictionaries

> **Домен**: [rendering](../domain.md)
> **Компонент**: централизованное хранение enum'ов и констант рендеринга.

## Ответственность | Responsibility

В rendering-домене единственный значимый enum — `RenderVersion`. Он
определяет формат выходного MP4 и используется в URLах, MinIO-ключах,
JS-фильтрах. Все литералы `LYRICS`/`KARAOKE`/`DEMO` обязаны быть
определены здесь.

Использование литералов в L3-спецификациях и коде без ссылки на эту
страницу — **критический дефект** (см. `audit-living-docs` «Magic Codes»).

## Интерфейсы и Контракты | Interfaces and Contracts

### `RenderVersion` (enum)

| Значение | MP4 в URL | Описание |
| --- | --- | --- |
| `LYRICS` | `lyrics` | Только текст + таймлайн, без фоновой картинки. |
| `KARAOKE` | `karaoke` | Текст + таймлайн + фоновая картинка альбома. |
| `DEMO` | `demo` | Полный микс (текст + фон + анимация), «рекламная» версия. |

**Место определения**: `karaoke-app/.../model/RenderVersion.kt`.

**Использование**:

- В БД: колонка `karaoke_process.render_version` (varchar).
- В API: `RenderMp4Params.renderVersion`.
- В MinIO: ключ `done_files/<songId>/<lowercase>.mp4`.
- В URL: `/api/videos/<songId>/<lowercase>.mp4`.

**Запрещено**:

- Строковые литералы `"LYRICS"`/`"KARAOKE"`/`"DEMO"` в коде (вместо
  этого — `RenderVersion.LYRICS.name`).
- Хардкод lowercase-версий (`"lyrics"`) без `RenderVersion.LYRICS.name.lowercase()`.

### Константа `MLT_CPU_LIMIT`

| Значение | Контекст |
| --- | --- |
| `2` | Прод: Docker `--cpus=2` на melt-процесс. |
| (не задано) | Локально: без ограничения. |

**Место определения**: `deploy/karaoke-app/docker-compose.yml` (env-var).

**Контракт**: на проде **обязательно** `2`, иначе деградация UI.

### Константа `HEAVY_RENDER_THREAD_ID`

| Значение | Описание |
| --- | --- |
| `0` | Основной lane для тяжёлого рендера. |

**Место определения**: `karaoke-app/.../KaraokeProcess.kt`.

**Контракт**: тяжёлые задачи (`RENDER_MP4_*`) ставятся в этот lane
через `KaraokeProcess.submit(..., threadId = 0)`.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `MLTProject`, `KaraokeVideo`.
- → [mlt-pipeline](mlt-pipeline.md) — использует `RenderVersion` для
  выбора шаблона mko.

## Логика и Алгоритмы | Logic and Algorithms

### Алгоритм выбора lowercase-имени файла

При загрузке MP4 в MinIO ключ строится через `RenderVersion.name.lowercase()`:

```kotlin
val fileKey = "done_files/${songId}/${renderVersion.name.lowercase()}.mp4"
```

Это означает: код в Kotlin (`RenderVersion.LYRICS.name == "LYRICS"`)
→ lowercase → `"lyrics"` → часть URL. Прямое использование литерала
`"lyrics"` в коде или SQL — **запрещено**, всегда через enum.

### Алгоритм выбора lane при submit рендера

```kotlin
KaraokeProcess.submit(
    taskType = TaskType.RENDER_MP4_LYRICS,
    threadId = HEAVY_RENDER_THREAD_ID,  // всегда 0 для тяжёлого рендера
    ...
)
```

Lane определяется **только** через `HEAVY_RENDER_THREAD_ID`. Хардкод
`threadId = 0` в местах вызова — **запрещён**.

## Связанные ADR | Related ADRs

- ADR-0002 — обоснование MLT (где `RenderVersion` определён).
