# Implementation Plan: Триггеры авто-новостей независимо от синхронизации + альбом/год в тексте

**Branch**: `092-fix-auto-news-triggers` | **Date**: 2026-07-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/092-fix-auto-news-triggers/spec.md`

## Summary

Механизм автоматических новостей о выходе песни (`specs/089-auto-news-song-release`,
`SongReleaseAnnouncementService.checkAndAnnounce`) сегодня вызывается из ровно одной точки —
`MainController.doChangeRecords` (`POST /changerecords`, единственный код `karaoke-web`, реально
исполняемый на PROD в момент синхронизации таблиц). Эта фича добавляет ещё **два независимых вызова
той же самой идемпотентной функции**, не меняя саму логику детекции/идемпотентности:

1. Новый `@Scheduled`-компонент в `karaoke-web` (`SongReleaseAnnouncementScheduler`, по образцу
   `StatsCacheScheduler`) — периодическая проверка раз в ~5 минут, независимая от `/changerecords`
   (закрывает разрыв «эфир наступил, но никто не запустил синхронизацию»).
2. Дополнительный вызов прямо в `SongEditorController.approve()` (`karaoke-app`, admin-машина) сразу
   после существующего best-effort push песни на сервер (`updateRemoteSongFromLocalDatabase`) — если
   push реально применился (непустой `SyncResult`), сразу вызывается `checkAndAnnounce` на
   `Connection.remote()` (тот же паттерн прямой записи на remote, что уже используется в этом же
   методе для `SongAssignment` при `target == "remote"`). Закрывает разрыв «апрув сделал песню
   доступной на проде, но новость не появляется без отдельной синхронизации».

Третье изменение — `checkAndAnnounce` при формировании title/body новости теперь включает альбом и
год песни (`Song.album`/`Song.year`, уже существующие read-only свойства), когда они заполнены.

Никаких новых таблиц/колонок не требуется — переиспользуется существующая идемпотентная бухгалтерия
`tbl_song_news_announced` из specs/089 без изменений схемы.

## Technical Context

**Language/Version**: Kotlin 2.x / JDK 17 (существующий стек `karaoke-app`/`karaoke-web`, Spring Boot)

**Primary Dependencies**: Spring `@Scheduled` (уже используется в `karaoke-web` —
`StatsCacheScheduler`, `StemJobTempCleanupScheduler`); существующие `KaraokeConnection`/
`KaraokeDbTable` (сырой JDBC). Новых внешних зависимостей не требуется.

**Storage**: PostgreSQL — без схемных изменений. Переиспользуется `tbl_song_news_announced` и поля
`tbl_news.song_id`/`source` из specs/089 как есть.

**Testing**: В CI юнит/интеграционных тестов для этого модуля нет (см. Constitution, «Рабочий
процесс» → «Тесты»); проверка — вручную на prod-like окружении по `quickstart.md`.

**Target Platform**: и `karaoke-web` (новый периодический джоб на PROD), и `karaoke-app` (новый вызов
внутри `approve()` на admin-машине) — в отличие от specs/089 (только `karaoke-web`), эта фича
затрагивает оба backend-модуля, т.к. два новых триггера физически живут там, где реально происходит
соответствующее событие (эфир по расписанию — там, где непрерывно работает прод; апрув — там, где
выполняется сам клик администратора).

**Project Type**: точечное расширение существующего backend-кода (без изменений в
`karaoke-public`/`webvue3`)

**Performance Goals**: периодическая проверка эфира — раз в ~5 минут (см. Clarifications spec.md),
переиспользует уже оптимизированный двухфазный дешёвый id-скан (`Song.listHashes` минус уже
анонсированные, затем полные объекты пачками по 25 — тот же код, что и в specs/089, без изменений).
Апрув-триггер — синхронный вызов той же функции один раз на клик «Апрувить», по объёму данных не
превышает объём проверки самой периодической — не самостоятельная новая нагрузка.

**Constraints**:
- `karaoke-app` не разворачивается на PROD (Principle I) → периодическая проверка эфира ДОЛЖНА жить в
  `karaoke-web` — единственном модуле, непрерывно работающем на PROD.
- Апрув-триггер, наоборот, ДОЛЖЕН жить в `karaoke-app` (единственный код, который реально выполняется
  в момент клика «Апрувить» на admin-машине) и обязан использовать прямое JDBC-соединение
  `Connection.remote()` — тот же паттерн прямой записи на удалённую БД, что уже используется в этом же
  методе `approve()` для `SongAssignment.save()` при `target == "remote"` (см. research.md, п.2-3).
- Только сырой JDBC, без JPA/Hibernate (Principle II) — не меняется, вся используемая инфраструктура
  уже написана на сыром JDBC.
- Новая функциональность НЕ ДОЛЖНА создавать вторую независимую копию критерия «песня публично
  доступна» или идемпотентности — все три триггера обязаны вызывать один и тот же
  `SongReleaseAnnouncementService.checkAndAnnounce()` без дублирования логики (FR-006/FR-010 spec.md).
- Прямые DDL/DML на PROD БД и любой деплой на сервер — только по прямому согласию пользователя, на
  каждое действие отдельно (см. секцию «Ограничения и доступы агента» Конституции). В данном случае
  схема БД не меняется вовсе, но деплой обоих модулей (`karaoke-web` и `karaoke-app`/перезапуск на
  admin-машине) на PROD — по согласию.

**Scale/Scope**: точечное расширение уже существующей точечной фичи (specs/089); новых сущностей БД —
ноль; изменяемых файлов — три (см. Project Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Никаких внешних SaaS-зависимостей — только БД-операции внутри уже существующих JVM-процессов (`karaoke-web` периодический джоб, `karaoke-app` вызов внутри уже существующего HTTP-обработчика `approve()`). |
| II. Сырой JDBC + diff по хэшам | ✅ PASS | Изменений схемы нет. Вся используемая инфраструктура (`Song.listHashes`, `SongNewsAnnounced`) уже написана на сыром JDBC (specs/089) и не меняется. |
| III. Двух-БД синхронизация через SyncRegistry | ⚠️ Тот же осознанный exception, что и в specs/089 | Не вводит новое исключение — переиспользует уже существующее (авто-новости `source='auto'` вне `NewsSyncTarget`-scope). Новый апрув-триггер использует `Connection.remote()` для ЧТЕНИЯ/ЗАПИСИ `tbl_news`/`tbl_song_news_announced` напрямую из `karaoke-app` — это НЕ проходит через `SyncRegistry`/hash-diff вообще (та же природа операции, что и прямая запись `SongAssignment` при `target=="remote"` в том же методе, уже существующая сегодня в коде без нарушения Principle III — там тоже прямой JDBC-доступ к remote в обход generic-sync). |
| IV. Async-очередь с парсингом stdout | N/A | Обе новые точки вызова — быстрые синхронные DB-операции, не идут через `KaraokeProcess*`/`ProcessBuilder` (тот же характер работы, что и исходный `checkAndAnnounce`, specs/089). |
| V. Два фронтенда — разные приложения | N/A | Изменений в `webvue3`/`karaoke-public` нет. |
| VI. Code Standards (KDoc/lint/per-feature-doc) | ⚠️ К выполнению | Новый/изменённый код должен получить KDoc с `@see docs/features/dual-db-sync.md`; сам файл должен быть обновлён в этом же PR (FR-009 Конституции), т.к. фича меняет модель работы этой подсистемы (было: 1 триггер, стало: 3). |
| VII. Cross-Machine Setup | N/A | Фича не касается локальных AI-конфигов/line-endings. |
| Ограничения и доступы агента | ⚠️ Требует согласия пользователя | Схема БД не меняется, но деплой `karaoke-web` (новый scheduler) и обновление/перезапуск `karaoke-app` на admin-машине (новый вызов в `approve()`) на PROD — по прямому согласию пользователя на каждое действие, не автономно. |

**Вывод**: Gate пройден. Единственное «исключение» (Principle III) — не новое, а переиспользование уже
принятого в specs/089 решения; новый апрув-триггер добавляет ещё один вызов уже существующего паттерна
прямой remote-записи (не новый прецедент — см. Complexity Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/092-fix-auto-news-triggers/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения и их обоснование
├── data-model.md         # Phase 1 — что переиспользуется, что не меняется
├── quickstart.md         # Phase 1 — сценарии ручной проверки
└── contracts/
    └── news-triggers.md  # Phase 1 — контракт трёх точек вызова checkAndAnnounce + формат текста
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/
│   └── SongEditorController.kt              # approve() — доп. вызов checkAndAnnounce() на
│                                             #   Connection.remote() сразу после успешного push
└── services/
    └── SongReleaseAnnouncementService.kt     # checkAndAnnounce() — title/body теперь включают
                                               #   альбом и год песни, когда заполнены

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/
└── SongReleaseAnnouncementScheduler.kt       # новый: @Scheduled(fixedDelay ~5 мин), вызывает
                                               #   тот же SongReleaseAnnouncementService.checkAndAnnounce
```

**Structure Decision**: Фича не добавляет новый модуль и не меняет схему БД — три точечных изменения
в уже существующих файлах (два) плюс один новый маленький Kotlin-класс, повторяющий устоявшийся в
проекте паттерн `@Scheduled`-джоба (`StatsCacheScheduler`). Вся бизнес-логика детекции/идемпотентности
остаётся в уже существующем `SongReleaseAnnouncementService` (specs/089) — новый код только **вызывает**
его из двух дополнительных мест и **расширяет** формируемый им текст.

## Complexity Tracking

> Заполняется только при нарушениях Constitution Check, требующих обоснования.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Апрув-триггер пишет в `tbl_news`/`tbl_song_news_announced` на PROD напрямую через JDBC из `karaoke-app` (в обход `SyncRegistry`/generic-sync) | Единственный код, реально исполняемый в момент клика «Апрувить», — это `karaoke-app` на admin-машине; `karaoke-web` в этот момент никак не участвует и не может среагировать «мгновенно» (см. research.md, п.2-3: HTTP-путь `updateRemoteSongFromLocalDatabase → /changerecords` существует, но best-effort и зависит от runtime-конфигурации sync-флагов, что делает SC-002 недетерминированным). Прямая запись на `Connection.remote()` — единственный способ получить требуемую (Clarifications) практически мгновенную (секунды) реакцию. | Альтернатива «дождаться, пока push дойдёт до `/changerecords`, и положиться на уже существующий вызов `checkAndAnnounce` там» была рассмотрена и отклонена: она зависит от нескольких независимых runtime-тумблеров (`Karaoke.allowUpdateRemote`, per-key sync-permission флаги в `KaraokeProperties`), которые могут быть выключены по не связанным с этой фичей причинам — что сделало бы SC-002 ненадёжным и трудно диагностируемым (наблюдаемый сегодня баг-репорт пользователя — предположительно как раз следствие этой зависимости). Явный отдельный вызов не имеет этой скрытой зависимости. Это не новый прецедент: тот же метод `approve()` уже сегодня пишет `SongAssignment` напрямую на `Connection.remote()` при `target=="remote"` — паттерн уже принят в кодовой базе. |
