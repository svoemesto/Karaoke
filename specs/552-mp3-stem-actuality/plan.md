# Implementation Plan: Ложная неактуальность mp3-стемов после рендера демо

**Branch**: `552-mp3-stem-actuality` | **Date**: 2026-10-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/552-mp3-stem-actuality/spec.md`

## Summary

HealthReport для типов без `LOCAL_FILESYSTEM` (пять mp3-типов) перестаёт сравнивать дисковую копию с объектами хранилищ и сравнивает только `size` объектов LOCAL↔REMOTE. При совпадении — OK, при расхождении — WARNING с корректным текстом и без автоматических действий. Ветки восстановления из дисковой копии, когда объектов в хранилищах нет, не трогаем. Изменение точечное: две ветки `actionsLocalStorage`/`actionsRemoteStorage` + два чистых helper'а + регрессионные unit-тесты + обновление knowledge.

## Technical Context

**Language/Version**: Kotlin 2.2.20, JVM 17

**Primary Dependencies**: Spring Boot (karaoke-app), MinIO SDK, PostgreSQL JDBC, JUnit 5

**Storage**: PostgreSQL (метаданные и `tbl_storage_metadata_cache`), MinIO LOCAL/REMOTE (объекты)

**Testing**: JUnit 5 (`./gradlew :karaoke-app:test`), существующие unit-тесты без поднятия Spring-контекста

**Target Platform**: Linux server (admin-машина, `karaoke-app`)

**Project Type**: web-service (single backend module `karaoke-app` + фронтенды, фронт не затрагивается)

**Performance Goals**: без роста числа обращений к MinIO; сравнение размеров идёт из `StorageMetadataCache` (как сейчас)

**Constraints**: не менять поведение для типов с `LOCAL_FILESYSTEM`; не менять specs/544; не пересматривать кодировщик

**Scale/Scope**: 5 типов файлов, 2 функции HealthReport, 2 helper'а, 2–3 теста, 1–2 knowledge-документа

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| I. Self-contained автопайплайн | PASS | изменение не добавляет внешних зависимостей и ручных шагов |
| II. Сырой JDBC + дифф по хэшам | PASS | схема БД не меняется, кеш читается как сейчас |
| III. Двух-БД синхронизация | PASS | `tbl_songs` не затрагивается |
| IV. Async-очередь задач | PASS | новых процессов нет; actions для storage-only расхождения не создаются |
| V. Два фронтенда | PASS | фронт не меняется |
| VI. Code Standards (KDoc/FR-009) | PASS | KDoc на helper'ах, обновление knowledge и per-feature документа |
| VII. Cross-Machine Setup | PASS | машинных файлов не касаемся |
| VIII. Секреты и git-гигиена | PASS | секретов не добавляется |
| IX. Knowledge-first | PASS | секция Knowledge References заполнена, домены health/storage прочитаны |

Пост-дизайн: изменений, нарушающих принципы, нет. Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/552-mp3-stem-actuality/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/requirements.md
└── tasks.md            # создаётся /speckit.tasks
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── HealthReport.kt                 # helper'ы + две ветки сравнения
├── KaraokeFileType.kt              # модель locations (не меняется)
└── services/StorageMetadataCache.kt

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
└── HealthReportMp3ActualityTest.kt # новый регрессионный тест

knowledge/domains/health/components/health-report.md   # правило актуальности
docs/features/health-report.md (если существует)         # FR-009
```

**Structure Decision**: изменяется только backend-модуль `karaoke-app`; фронтенды, `karaoke-web` и публичный контур не затрагиваются.

## Complexity Tracking

Не требуется: нарушений Constitution Check нет.
