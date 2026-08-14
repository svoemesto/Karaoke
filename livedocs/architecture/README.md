# architecture/ — C4 слой (индекс)

> Архитектурные диаграммы (Mermaid) — C4 L1/L2/L3 + тематические документы.

## Содержимое

### C4 уровни

| Файл | Уровень | Что показывает |
|------|---------|----------------|
| [L1-system-context.md](L1-system-context.md) | L1 | Karaoke ↔ внешние системы (браузер, Postgres, MinIO, Ollama, ...) |
| [L2-containers.md](L2-containers.md) | L2 | Приложения + хранилища внутри Karaoke (karaoke-app, karaoke-web, SPA, БД) |
| [L3-components.md](L3-components.md) | L3 | Компоненты внутри karaoke-app (Model, MLT, Queue, LLM, SSE) |

### Тематические (drill-down по конкретной теме)

| Файл | Тема |
|------|------|
| [data-sync.md](data-sync.md) | LOCAL ↔ SERVER синхронизация (SyncRegistry, recordhash) |
| [queue-lanes.md](queue-lanes.md) | Async-очередь (threadId lanes, HEAVY_RENDER, LIGHT_BACKGROUND, REMOTE_STORE_UPLOAD, STEM_JOBS) |

### Паттерны / конвенции (мигрированы из `AGENTS.md` Q&A)

| Файл | Тема |
|------|------|
| [jackson-conventions.md](jackson-conventions.md) | Jackson `is`-prefix в Kotlin DTO (`@JsonProperty`) |
| [docker-conventions.md](docker-conventions.md) | Образы Docker (nginx:stable, node:22-alpine, JRE) |
| [documentation-conventions.md](documentation-conventions.md) | KDoc backticks, JSDoc coverage, blame-ignore-revs |
| [webvue3-patterns.md](webvue3-patterns.md) | Персистентность страницы пагинации в webvue3 (Vuex store + watcher) |

## Конвенции

- C4 уровни: имя файла `L<n>-<topic>.md`, тип `c4-level`, frontmatter с `level: L1|L2|L3`.
- Тематические: имя файла `<topic>.md`, тип `topic`.
- Все архитектурные документы содержат Mermaid-блок (минимум 1 диаграмма).
- Размер: ≤ 2 стр. (≤ 80 строк) для C4 уровней, ≤ 3 стр. (≤ 120 строк) для topic.

## Иерархия drill-down

```
L1 (system context)
 └─ L2 (containers)
     └─ L3 (components внутри karaoke-app)
         ├─ topic: data-sync
         ├─ topic: queue-lanes
         └─ pattern: jackson-conventions, docker-conventions, ...
```

## Когда добавлять новый документ

1. Новая крупная подсистема или изменение архитектуры → обновить L3 или создать новый topic.
2. Новая ловушка / паттерн, мигрированная из AGENTS.md → создать pattern-документ.
3. Новая внешняя интеграция → обновить L1.