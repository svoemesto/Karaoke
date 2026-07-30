# Implementation Plan: Апрув задания редактора завершается ошибкой запроса, новость не появляется

**Branch**: `094-fix-approve-news-failure` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/094-fix-approve-news-failure/spec.md`

## Summary

`SongEditorController.approve()` (кнопка «Одобрить и премиить») выполняет
внутри ОДНОГО синхронного HTTP-запроса: применение разметки к LOCAL-записи
песни, best-effort push на боевую копию (`updateRemoteSongFromLocalDatabase`),
условный вызов `SongReleaseAnnouncementService.checkAndAnnounce()` (полное
сканирование ~15 тыс. «готовых» записей на REMOTE через `Song.listHashes(db,
"WHERE id_status >= 6")`) и, наконец, безусловную запись
`aRead.save()` — пометку самого задания редактора одобренным. Исследование
(research.md) показало, что (а) каждый из трёх вызовов `Connection.remote()`
в этой цепочке создаёт НОВОЕ физическое JDBC-соединение к боевой БД
(`KaraokeConnection.threadLocalConnection` кеширует connection на экземпляр
`Connection`, а не глобально — новый `Connection.remote()` = новое
соединение), и (б) финальный `aRead.save()` — единственный шаг, НЕ
защищённый try/catch в `approve()`, и внутри него `connection.prepareStatement(sql)`
(`KaraokeDbTable.save()`) тоже без catch. Если после трёх последовательных
сетевых JDBC-подключений к прод-серверу и полного 15k-скана это финальное
соединение оказывается протухшим/сброшенным, исключение уходит необработанным
из Spring-контроллера → HTTP 500 → фронтенд (`ReviewModal.doApprove()`)
показывает «Ошибка запроса» несмотря на то, что запись песни уже успешно
обновлена. Задание при этом остаётся неодобренным, поэтому карта логов
второй попытки (без пересохранения песни, без пуша, без «Запрос хешей...
15243» — то есть без повторного вызова `checkAndAnnounce`, потому что
push второй раз не находит изменений) в точности совпадает с наблюдаемым
поведением.

Технический подход: (1) сделать `approve()` устойчивым к сбою на любом шаге
после успешного локального применения — обернуть push+анонс+запись статуса
задания в единую конструкцию, которая ВСЕГДА возвращает корректный typed-JSON
ответ (никогда не даёт исключению дойти до Spring как HTTP 500), приводя
логику в соответствие уже сформулированным FR-001/FR-003/FR-005; (2) убрать
причину, по которой финальная запись статуса задания вообще рискует
столкнуться с протухшим соединением — не открывать три независимых
`Connection.remote()` подряд в одном запросе, а переиспользовать одно
соединение для push + checkAndAnnounce (оба уже используют `Connection.remote()`
внутри одного и того же запроса); (3) на фронтенде — различать «успех»,
«уже одобрено» и «ошибка» явными сообщениями (FR-002/FR-005), вместо
единственного обобщённого `catch → 'Ошибка запроса'`.

## Technical Context

**Language/Version**: Kotlin (JVM 17, Spring Boot) — backend; Vue 3 (Vuex,
Vite) — admin-фронтенд (`webvue3`)

**Primary Dependencies**: существующий backend-стек `karaoke-app`
(`SongEditorController`, `Song`/`KaraokeDbTable`/`SyncTarget`/`SyncRegistry`
sync-движок, `SongReleaseAnnouncementService`, `SongAssignment`); admin SPA
`webvue3` (`SongEditor/ReviewModal.vue`, `SongEditor/store.js`,
`lib/utils.js` → `promisedXMLHttpRequest`). Новых зависимостей фича не
добавляет.

**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`/`Connection.local()`/
`Connection.remote()`) — LOCAL (admin-машина) и SERVER (прод,
`79.174.95.69:8832`, `socketTimeout=30`). Новых таблиц/колонок фича не вводит.

**Testing**: В CI автотестов нет (см. Конституцию, «Рабочий процесс» —
существующие тесты `@Disabled`, требуют сеть/credentials). Проверка —
вручную через `webvue3` UI + логи `docker logs karaoke-app` + прямые
SQL-запросы к LOCAL/SERVER (сравнение `admin_status`/`id_status`/
`tbl_song_news_announced`/публичной ленты новостей) — сценарии см.
`quickstart.md`.

**Target Platform**: `karaoke-app` — Docker-контейнер на admin-машине
(`dev-pc`), обращается по сети к прод Postgres и к прод `karaoke-web`
(`/changerecords`); `webvue3` — статическая SPA, раздаётся тем же
admin-стеком.

**Project Type**: web-service (существующий Spring Boot backend) +
существующая admin SPA — правится код внутри уже существующих модулей, без
новых проектов/модулей.

**Performance Goals**: Явных SLA по времени ответа `approve()` спецификация
не вводит (см. Clarifications, вопрос про contention) — требуется только
ДОСТОВЕРНОСТЬ результата независимо от длительности. Тем не менее устранение
лишних повторных `Connection.remote()` — прямое следствие устранения бага
(не отдельная performance-цель), см. Summary.

**Constraints**: Не менять бизнес-правила `specs/092-fix-auto-news-triggers`
(критерий «песня публично доступна», правило «одна новость на одно событие
готовности», best-effort семантика push — см. Clarifications); не вводить
JPA/Hibernate (Конституция, принцип II); не расширять `SyncRegistry`/
recordhash-триггеры (изменений синхронизируемых таблиц не требуется); не
трогать `karaoke-public` (принцип V — фикс целиком admin-side).

**Scale/Scope**: ~15–18 тыс. записей `tbl_songs` на проде, из них
кандидатный набор `checkAndAnnounce()` (`id_status >= 6`) — порядка 15 тыс.
(наблюдалось `Получено хешей: 15243` в логе инцидента). Один administrator
(`SongEditorController.approve` вызывается по одному запросу за раз, без
конкурентной нагрузки со стороны других администраторов).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | N/A | Фикс не касается ffmpeg/melt/Demucs/Sheetsage. |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | PASS | Фикс остаётся в рамках существующего `KaraokeConnection`/JDBC-паттерна; `associateBy { it.id }` в diff-логике (`collectSyncOps`) не меняется. Новых O(n²)-сравнений не вводится. |
| III. Двух-БД синхронизация через SyncRegistry | PASS | Ни одна таблица не добавляется/не убирается из `SyncRegistry`; recordhash-триггеры не трогаются; флаги `sync_*_allowed` не меняются. |
| IV. Async-очередь с парсингом stdout | N/A | `ProcessBuilder`/`KaraokeProcess*` не затрагиваются. |
| V. Два фронтенда — разные приложения | PASS | Меняется только `webvue3` (admin); `karaoke-public` не затрагивается. |
| VI. Code Standards (NON-NEGOTIABLE) | PASS (с обязательством) | Правки в `SongEditorController.kt`/`SongReleaseAnnouncementService.kt`/`ReviewModal.vue`/`store.js` — публичные функции уже имеют KDoc/JSDoc, при правке логики обновить их `@see`/текст, не оставляя устаревших утверждений (см. Research, п. «Стале KDoc `checkAndAnnounce`» — сам факт находки уже является поводом для правки в рамках этого PR). Per-feature документ `docs/features/dual-db-sync.md` (тот же, что обновляли 089 и 092) ДОЛЖЕН быть обновлён в этом PR (FR-009). |
| VII. Cross-Machine Setup | N/A | Изменений в персональных конфигах/`.gitattributes`/onboarding-документах не требуется. |

Нарушений, требующих обоснования в Complexity Tracking, не выявлено.

**Повторная проверка после Phase 1 (data-model.md/contracts/quickstart.md)**:
дизайн не добавил ни одной новой таблицы/колонки, ни одного нового внешнего
вызова, ни изменений в `SyncRegistry`/recordhash — `ApproveOutcome`
(data-model.md) целиком укладывается в существующий JSON-контракт
контроллера (лишь новое поле `status` в уже существующем `Map<String, Any?>`
ответе). Таблица выше остаётся в силе без изменений; gate пройден повторно.

## Project Structure

### Documentation (this feature)

```text
specs/094-fix-approve-news-failure/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md         # Phase 1 output (/speckit.plan command)
├── quickstart.md         # Phase 1 output (/speckit.plan command)
├── contracts/            # Phase 1 output (/speckit.plan command)
│   └── approve-endpoint.md
└── tasks.md              # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Существующий multi-module репозиторий (`karaoke-app` backend, `webvue3`
admin SPA) — правки точечные, внутри уже существующих файлов, новых
директорий/модулей фича не создаёт.

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── SongEditorController.kt        # approve() — устойчивый ответ на все ветки исхода
├── services/
│   └── SongReleaseAnnouncementService.kt  # checkAndAnnounce() — актуализировать KDoc
│                                            # (стале "единственный вызывающий" — уже неверно
│                                            # после specs/092), убрать лишний Connection.remote()
├── model/
│   └── KaraokeDbTable.kt               # save() — рассмотреть try/catch вокруг
│                                            # connection.prepareStatement (единственное
│                                            # незащищённое место записи)
└── Utils.kt                            # updateRemoteSongFromLocalDatabase/updateDatabases —
                                            # без изменений логики, только если понадобится
                                            # переиспользование Connection для checkAndAnnounce

webvue3/src/components/SongEditor/
├── ReviewModal.vue                     # doApprove() — различать успех / "уже одобрено" / ошибку
└── store.js                            # approveAssignment — прокинуть typed-результат из ответа

docs/features/
└── dual-db-sync.md                     # per-feature документ (FR-009, тот же, что 089/092)
```

**Structure Decision**: Изменения не выходят за рамки уже существующих
модулей `karaoke-app` (backend) и `webvue3` (admin SPA) — вариант «Web
application», но без новых top-level директорий: правки точечные внутри
файлов, перечисленных выше. `karaoke-public` не затрагивается (принцип V).

## Complexity Tracking

Нет нарушений Constitution Check, требующих обоснования — таблица не
заполняется.
