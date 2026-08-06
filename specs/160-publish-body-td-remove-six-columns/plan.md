# Implementation Plan: Упрощение PublishTableBodyTd + полная чистка DTO от processColor*

**Branch**: `160-publish-body-td-remove-six-columns` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/160-publish-body-td-remove-six-columns/spec.md`

**Note**: Этот шаблон заполняется `/speckit.plan`; структура описывает execution workflow.

## Summary

Удалить визуальный шум из таблицы «Публикации» в `webvue3` (`PublishTableBodyTd.vue`) — оставить только ячейку `.publish-name` шириной 210 px (было 150 px + 6 цветовых колонок по 10 px). Расширить применение: снять раскраску PLAY-кнопок в `SongEdit.vue` и почистить DTO (`SongDTO`, `SongDTOdigest`) от 27 неиспользуемых полей `processColor*`. Оставить ровно одно — `processColorPlayerDemo` (живой потребитель: бейдж `DE` в `SongsTable.vue`).

Геттеры `Song.processColor*` и diff-логика LOCAL↔SERVER в `Song.kt` **сохраняются** (конституционный Принцип III NON-NEGOTIABLE + нужны `Publication` для собственных геттеров `publishXcolorMeltY` + нужны серверным шаблонам `${song.processColorX}`).

Технический подход (см. [`research.md`](./research.md)): surgical edit с точной картой позиций в файлах (4 backend-блока + 1 Vue-компонент 241 строка + 4 строки в `SongEdit.vue` 6350 строк). Никаких миграций БД, никаких изменений wire-protocol на уровне эндпоинтов (только объём JSON).

## Technical Context

| Параметр | Значение | Источник |
|---|---|---|
| **Language/Version** | Kotlin 1.x (JDK 17) для backend, JavaScript/Vue 3 для frontend | `karaoke-app/build.gradle.kts`, `webvue3/package.json` |
| **Primary Dependencies** | Spring Boot (Web MVC), Jackson (JSON serialization), Vue 3 + Vite + Vuex + Bootstrap-vue-next | constitution §Технологический стек |
| **Storage** | PostgreSQL (через сырой JDBC, `KaraokeConnection`); MinIO (не затрагивается этим PR) | constitution Principle II NON-NEGOTIABLE |
| **Testing** | Нет автоматических тестов в этом PR; валидация — ручная (5 шагов в `quickstart.md`) + CI линтеры | `AGENTS.md` → «Тесты» |
| **Target Platform** | Linux-сервер (admin-машина для `karaoke-app`, прод для `karaoke-web`); современный браузер для `webvue3` | constitution §Runtime |
| **Project Type** | Web application (Option 2: backend + frontend, мульти-модуль Gradle + SPA) | структура `karaoke-app/`, `webvue3/` |
| **Performance Goals** | Сокращение payload `/api/songsdigests` на ~5 МБ (~92% по `processColor*` блоку); runtime UI без задержек | research.md §3.6 |
| **Constraints** | Не ломать LOCAL↔SERVER sync (FR-014, FR-015); не ломать серверные Thymeleaf-шаблоны; baseline линтеров не должен расти | research.md §2.3, §3.5 |
| **Scale/Scope** | 18 858 песен на проде; 4 backend-файла, 2 frontend-файла; ~50 строк правок | research.md §2 |

**Storage volume**: 27 полей × ~12 байт JSON × 18 858 = ~6 МБ (до) → ~530 КБ (после).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ N/A | Не вводит внешних SaaS в горячий путь медиа-обработки; фича — UI-чистка DTO. |
| **II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE)** | ✅ Pass | Не трогаем SQL, JDBC, `KaraokeConnection`, `KaraokeDbTable.save()`. DTO-изменения чисто косметические на уровне JSON. |
| **III. Двух-БД синхронизация через SyncRegistry (NON-NEGOTIABLE)** | ✅ Pass | FR-014/015 явно сохраняют `Song.kt` геттеры и diff-логику LOCAL↔SERVER. SyncRegistry (`SyncTarget.kt:215`) — только ссылка в комментарии, не правится. `recordhash`-триггеры `tbl_settings` не затрагиваются (FR-015). |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Не вводит/трогает подпроцессы. |
| **V. Двух-фронтенд: админка и публичный сайт — разные приложения** | ✅ Pass | Правки только в `webvue3` (admin). `karaoke-public` уже проверен grep'ом — 0 ссылок на `processColor*`. |
| **VI. Code Standards (NON-NEGOTIABLE)** | ✅ Pass | FR-006: JSDoc на `export default PublishTableBodyTd` сохранён (строки 57–61). KDoc на `SongDTO`/`SongDTOdigest` сохранён. FR-007: линтеры ktlint + ESLint + baseline-check проходят (FR-009 в `quickstart.md` шаг 5). FR-009: per-feature документ `docs/features/songs-table.md` обновляется в том же PR (см. Assumptions спеки). |
| **VII. Cross-Machine Setup (NON-NEGOTIABLE)** | ✅ N/A | Не затрагивает AI-конфиги, `.git-blame-ignore-revs`, `.gitattributes`. |
| **VIII. Секреты и git-гигиена (NON-NEGOTIABLE)** | ✅ N/A | Не затрагивает секреты, `.env`, `deploy/`. |

**Итог**: Constitution Check — все принципы NON-NEGOTIABLE проходят. Phase 0 research может стартовать (и стартовал).

**Re-check после Phase 1 design** (post-design): те же принципы проходят, потому что Phase 1 не вводит новых архитектурных решений — только фиксирует форму данных (data-model.md) и контракт (contracts/).

## Project Structure

### Documentation (this feature)

```text
specs/160-publish-body-td-remove-six-columns/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command) — DONE
├── data-model.md        # Phase 1 output (/speckit.plan command) — DONE
├── quickstart.md        # Phase 1 output (/speckit.plan command) — DONE
├── contracts/
│   └── api-songsdigests.md  # Phase 1 output (/speckit.plan command) — DONE
├── tasks.md             # Phase 2 output (/speckit.tasks command — НЕ создаётся /speckit.plan)
├── spec.md              # /speckit.specify — DONE
└── checklists/
    └── requirements.md  # /speckit.specify checklist — DONE
```

### Source Code (repository root)

```text
# Option 2: Web application (frontend + backend) — ВЫБРАН

backend/  →  karaoke-app/  (Spring Boot / Kotlin)
  └─ src/main/kotlin/com/svoemesto/karaokeapp/
       ├─ model/
       │    ├─ Song.kt                       # правка toDTO() 8232–8259 (-27)
       │    ├─ SongDTO.kt                    # правка 68–95 (-27) + 353–380 (-27)
       │    ├─ SongDTOdigest.kt              # правка 61–88 (-27)
       │    └─ Publication.kt                # НЕ правится (FR-014)
       └─ resources/templates/
            ├─ publications.html             # НЕ правится (raw Song)
            ├─ unpublications.html           # НЕ правится
            ├─ songs.html                    # НЕ правится
            ├─ songs2.html                   # НЕ правится
            └─ area_left_column.html         # НЕ правится

frontend/  →  webvue3/  (Vue 3 / Vite)
  └─ src/components/
       ├─ Publish/components/
       │    └─ PublishTableBodyTd.vue        # правка template 17–48 (-6) + computed 97–153 (-20)
       │                                       #        + methods 163–171 (-3) + CSS (width 150→210, 200→210)
       └─ Songs/edit/
            └─ SongEdit.vue                  # правка 2297–2328 (-4 inline-style строки)

# НЕ затрагивается (подтверждено grep'ом):
#  - karaoke-public/  (0 ссылок на processColor*)
#  - karaoke-web/     (0 ссылок на processColor*)
#  - SongsTable.vue:329 — живой потребитель processColorPlayerDemo
#  - SongsTable.vue:362–385 — закомментированные блоки (out of scope)
```

**Structure Decision**: Option 2 (web application). Проект уже multi-module: backend (`karaoke-app`) + 2 frontend SPA (`webvue3`, `karaoke-public`). Этот PR правит только `karaoke-app` (4 файла, surgical) и `webvue3` (2 файла, surgical).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution Check. Все принципы NON-NEGOTIABLE проходят. Таблица не заполняется.

---

## Phase 0 (Research) — DONE

Артефакт: [`research.md`](./research.md). Содержит:
- Подтверждённый технический контекст (см. таблицу выше).
- Карту изменяемых файлов с точными диапазонами строк (§2).
- Best practices для Kotlin data class (§3.1), Vue computed (§3.2), CSS width (§3.3), SSE-дифф (§3.5).
- Расчёт экономии payload: ~5 МБ → ~530 КБ (§3.6).
- Закрытие всех 5 `NEEDS CLARIFICATION` (§4).
- Открытые риски для отслеживания в `tasks.md` (§5): lint baseline, JSDoc coverage, визуальная регрессия.

## Phase 1 (Design & Contracts) — DONE

Артефакты:
- [`data-model.md`](./data-model.md) — финальная форма сущностей после PR (SongDTO/SongDTOdigest/Song/Publication/PublishTableBodyTd/SongEdit), инварианты, валидация.
- [`contracts/api-songsdigests.md`](./contracts/api-songsdigests.md) — публичный контракт JSON-ответов: какие endpoints затронуты, до/после примеры, совместимость, миграция.
- [`quickstart.md`](./quickstart.md) — 5 шагов ручной валидации end-to-end (визуальная проверка, JSON-проверка, бейдж DE, линтеры).

## Готовность к `/speckit.tasks`

Все артефакты Phase 0/1 на месте. Спека complete (0 `[NEEDS CLARIFICATION]`). Constitution Check passes. Карта файлов с точными строками готова для декомпозиции в задачи.

**Push/PR**: за пользователем, как договаривались (см. AGENTS.md → «CI-gate для master»). Реализация — в отдельной сессии по явному запросу пользователя.
