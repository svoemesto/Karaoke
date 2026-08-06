# Implementation Plan: Убрать мониторинг запланированных публикаций

**Branch**: `154-remove-scheduled-publications-monitoring` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/154-remove-scheduled-publications-monitoring/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Убрать из системного мониторинга (`MonitorRegistry`/`MonitoringService`, «светофор»
в хедере webvue3) единственную проверку, отслеживающую горизонт ещё не вышедших
запланированных публикаций в Telegram (`TelegramHorizonCheck`), вместе с
управляющей ею настройкой порога (`monitorTelegramHorizonDays`), потому что
публикации больше не создаются заранее вручную как «отложенные» — система сама
генерирует их к нужному моменту, и алерт о «малом горизонте» стал ложным
сигналом. Технический подход: точечное удаление проверки из реестра и её
исходного файла, удаление связанной настройки из `KaraokeProperties.kt`
(автоматически убирает её из UI списка настроек в webvue3 — отдельной
frontend-правки не требуется), обновление per-feature документа
`docs/features/monitoring.md` (FR-009). Остальные семь проверок мониторинга и
REST-контракт (`/api/monitor/*`) не меняются по форме.

## Technical Context

**Language/Version**: Kotlin (JDK 17), Spring Boot backend — модуль `karaoke-app`
(модуль `karaoke-web`/`karaoke-public` не затрагиваются: `MonitorRegistry`/
`MonitoringService` существуют только в `karaoke-app`, разворачиваемом на
admin-машине).

**Primary Dependencies**: существующий фреймворк проверок мониторинга
(`MonitorCheck`/`MonitorRegistry`/`MonitoringService`/`MonitorAlert`),
`KaraokeProperties` (персистентные настройки), `Song.loadListFromDb` (источник
данных удаляемой проверки — перестаёт вызываться).

**Storage**: N/A для этой фичи — `monitorTelegramHorizonDays` и `monitorDismissed`
живут в `Karaoke.properties` (base64-properties файл на admin-машине), не в
PostgreSQL; SQL-миграций и изменений `recordhash`/`SyncRegistry` не требуется.

**Testing**: выделенных unit/integration-тестов на `MonitorRegistry`/проверки в
репозитории нет (константа проекта — см. Конституцию, раздел «Рабочий процесс»:
«Тесты: в CI нет»). Проверка — ручная, через webvue3 UI (см. `quickstart.md`) и
сборку (`ktlintCheck`, KDoc-coverage).

**Target Platform**: backend `karaoke-app` на admin-машине (единственное место
развёртывания этого модуля); admin-фронтенд `webvue3` — только как потребитель
уже существующего REST-контракта (`/api/monitor/alerts`, список настроек), без
прямых правок кода фронтенда.

**Project Type**: web-service (backend Kotlin/Spring + admin SPA webvue3) —
существующая структура проекта, новых модулей/директорий не добавляется.

**Performance Goals**: N/A — чистое удаление; побочный эффект — на один
`Song.loadListFromDb` запрос меньше на каждом тике `MonitoringService` (раз в
минуту), что снижает, а не увеличивает нагрузку.

**Constraints**: удаление НЕ ДОЛЖНО менять поведение остальных семи проверок
мониторинга и форму REST-ответов `/api/monitor/*` (кроме отсутствия ключа
`telegram.horizon` в алертах и записи `monitorTelegramHorizonDays` в списке
настроек); должно пройти `ktlintCheck` и `check-kdoc-coverage.sh` (FR-006/FR-007
Конституции).

**Scale/Scope**: одна проверка мониторинга, одна настройка `KaraokeProperty`,
один per-feature документ (`docs/features/monitoring.md`, FR-009) — точечное
изменение в одном backend-модуле, без миграций БД и без правок frontend-кода.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Применимость | Вердикт |
|---|---|---|
| I. Self-contained автопайплайн | Не затрагивает ffmpeg/melt/Demucs/Sheetsage/внешние API | N/A — PASS |
| II. Сырой JDBC + дифф по хэшам | Изменений схемы БД/`recordhash` нет — настройка живёт в `Karaoke.properties`, не в PostgreSQL | N/A — PASS |
| III. Двух-БД синхронизация через SyncRegistry | `monitorTelegramHorizonDays`/`monitorDismissed` не участвуют в LOCAL↔SERVER sync (не в `SyncRegistry.all`), `karaoke-app` вообще не разворачивается на проде | N/A — PASS |
| IV. Async-очередь задач | Проверка мониторинга не использует `ProcessBuilder`/очередь заданий | N/A — PASS |
| V. Двух-фронтенд: admin/public — разные приложения | Изменение видно только в admin (`webvue3`, через уже существующий generic-список настроек и панель мониторинга); `karaoke-public` не затрагивается вовсе | PASS |
| VI. Code Standards (KDoc/ktlint/FR-009) | Удаляемый файл `TelegramHorizonCheck.kt` убирается целиком (не оставляем недокументированный дохлый код); `ktlintCheck` запускается перед коммитом; `docs/features/monitoring.md` — per-feature документ подсистемы «monitoring», обновляется в этом же PR (FR-009) | PASS (гейт закрывается по ходу Phase 1/задач) |
| VII. Cross-Machine Setup | Локальные AI-конфиги/`.gitattributes`/`.git-blame-ignore-revs` не затрагиваются | N/A — PASS |
| VIII. Секреты и git-гигиена | Секреты не затрагиваются | N/A — PASS |

Нарушений нет → секция «Complexity Tracking» не заполняется.

## Project Structure

### Documentation (this feature)

```text
specs/154-remove-scheduled-publications-monitoring/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── monitor-alerts-contract.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Существующая структура monorepo (web-application: Kotlin backend + Vue admin
frontend), правки — точечные, в уже существующих файлах/пакетах:

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── monitor/
│   ├── MonitorRegistry.kt        # убрать TelegramHorizonCheck из checks
│   ├── MonitorCheck.kt           # не меняется (интерфейс)
│   ├── MonitoringService.kt      # не меняется (общий runner)
│   └── checks/
│       └── TelegramHorizonCheck.kt   # УДАЛИТЬ целиком
└── KaraokeProperties.kt          # убрать KaraokeProperty("monitorTelegramHorizonDays")

docs/features/
└── monitoring.md                 # обновить: 8→7 проверок, убрать пункт про горизонт (FR-009)

webvue3/                          # правок кода НЕ требуется — список настроек и
                                   # панель мониторинга рендерятся generic-компонентами
                                   # из тех же REST-ответов; удалённые ключ/настройка
                                   # просто перестают в них приходить
```

**Structure Decision**: Изменения ограничены модулем `karaoke-app` (пакет
`monitor` + `KaraokeProperties.kt`) и документацией. `karaoke-web`,
`karaoke-public` и `webvue3` не редактируются напрямую — оба UI-поверхности
(панель мониторинга и список настроек в webvue3) читают данные через уже
существующие generic REST/UI-компоненты, поэтому удаление проверки и настройки
на backend автоматически убирает их из UI без отдельных frontend-правок (тот же
паттерн, что и при добавлении новых `KaraokeProperty`/`MonitorCheck` — см.
`docs/features/monitoring.md`, «Инварианты / правила»).

## Complexity Tracking

*(не требуется — нарушений Constitution Check нет)*
