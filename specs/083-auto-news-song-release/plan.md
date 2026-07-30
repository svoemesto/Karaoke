# Implementation Plan: Автоматические новости о выходе песни в эфир

**Branch**: `083-auto-news-song-release` | **Date**: 2026-07-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/083-auto-news-song-release/spec.md`

## Summary

Когда очередная песня становится публично доступна пользователям (строгое условие «можно
смотреть»: `id_status >= 6` + наступившая дата эфира + готовы стемы/обложки/маркеры — то же самое,
что уже определяет `canWatch` в `PublicPlayerController`), система должна автоматически создать и
опубликовать новость об этом, без участия администратора. Проверка выполняется на PROD в момент
существующего механизма синхронизации таблиц (`POST /changerecords` в `karaoke-web`, единственная
точка, где сервер применяет присланные с LOCAL изменения к своей БД) — отдельного
cron/`@Scheduled`-джоба не заводится. Идемпотентность и защита от «лавины» исторических новостей
обеспечиваются новой PROD-локальной таблицей-меткой `tbl_song_news_announced`, не участвующей в
LOCAL↔SERVER синхронизации.

## Technical Context

**Language/Version**: Kotlin 2.x / JDK 17 (существующий стек `karaoke-app`/`karaoke-web`, Spring Boot)

**Primary Dependencies**: Spring Boot (MVC-контроллер), существующие `KaraokeConnection`/`KaraokeDbTable` (сырой JDBC) — новых внешних зависимостей не требуется

**Storage**: PostgreSQL — новая таблица `tbl_song_news_announced` (PROD-локальная бухгалтерия) + два новых nullable-поля в `tbl_news` (`song_id`, опционально `source`)

**Testing**: В CI юнит/интеграционных тестов для этого модуля нет (см. Constitution, «Рабочий процесс» → «Тесты»); проверка — вручную на prod-like окружении по `quickstart.md`

**Target Platform**: `karaoke-web` (единственный backend-модуль, разворачиваемый на PROD; `karaoke-app` на PROD не разворачивается — Constitution Principle I)

**Project Type**: расширение существующего web-сервиса (backend-only; изменений в `karaoke-public`/`webvue3` — минимум, см. ниже)

**Performance Goals**: проверка готовности песен добавляется к уже существующему синхронному HTTP-обработчику `/changerecords`; не должна заметно увеличивать время ответа синхронизации. **Уточнено при реализации**: пул песен со статусом >= 6 на реальных данных — многие тысячи строк (14874 на dev-стенде), не единицы — наивная загрузка всех кандидатов одним `SELECT *`-вызовом вызывает `OutOfMemoryError` (тот же класс бага, что в specs/082-fix-import-folder-oom). Обязательна двухфазная загрузка: дешёвый id-скан (`Song.listHashes`, без тяжёлых текстовых полей) минус уже анонсированные, затем полные объекты — только для остатка, пачками по 25 (см. research.md, уточнение к п.5)

**Constraints**:
- `karaoke-app` не разворачивается на PROD (Principle I) → вся логика детекции и создания новости должна жить в коде, исполняемом `karaoke-web` (даже если общие модели `Song`/`News` физически лежат в модуле `karaoke-app`, используемом как библиотека)
- Только сырой JDBC, без JPA/Hibernate (Principle II)
- Новая функциональность НЕ ДОЛЖНА участвовать в существующем hash-diff LOCAL↔SERVER sync-движке для `news` (см. «Риск синхронизации» в Research) — иначе первый же admin-триггерный «1 клик» может удалить/переписать автоматически созданные новости
- Прямые DDL/DML на PROD БД и любой деплой на сервер — только по прямому согласию пользователя, на каждое действие отдельно (см. секцию «Ограничения и доступы агента» Конституции)
- Новая логика должна переиспользовать уже существующее понятие «готова к просмотру» (сейчас продублировано в `PublicPlayerController.stemsReady`/`canWatch` (Kotlin) и `StatBySong.CONTENT_READY_FILTER` (raw SQL)) — не создавать третью независимую копию этого условия

**Scale/Scope**: точечная фича; кандидатов на анонс — единицы в месяц (по масштабу проекта), таблица-метка — не более нескольких тысяч строк за всё время жизни проекта

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Никаких внешних SaaS/сетевых зависимостей — только БД-операции внутри уже существующего HTTP-обработчика `karaoke-web`. |
| II. Сырой JDBC + diff по хэшам | ✅ PASS (с условием) | Новая таблица/поля — через сырой JDBC (`KaraokeConnection`), без JPA. Условие: новые поля/таблица НЕ включаются в recordhash-триггер `tbl_news` и НЕ участвуют в generic diff-based sync (см. ниже). |
| III. Двух-БД синхронизация через SyncRegistry | ⚠️ ТРЕБУЕТ ОСОЗНАННОГО ИСКЛЮЧЕНИЯ | Автоматически созданные новости физически существуют только на PROD и **намеренно** не входят в scope `NewsSyncTarget` (см. Research, «Риск синхронизации»). Это соответствует букве принципа («Наличие recordhash-триггера... не означает участие в Синхронизации в 1 клик») — целевая таблица получает свои новые колонки/сателлит-таблицу вне текущего hash-scope. Явно задокументировать в `docs/features/dual-db-sync.md` (FR-009 Конституции). |
| IV. Async-очередь с парсингом stdout | N/A | Работа синхронная, быстрая, без подпроцессов — не проходит через `KaraokeProcess*`. |
| V. Два фронтенда — разные приложения | ✅ PASS | Публичный сайт не меняется (новость всплывает штатно через `News.loadPublished*`); в `webvue3` — минимальная доработка отображения (см. data-model.md), без смешивания с public-модулем. |
| VI. Code Standards (KDoc/лint/per-feature-doc) | ⚠️ К ВЫПОЛНЕНИЮ | Новый/изменённый публичный код должен получить KDoc с `@see docs/features/dual-db-sync.md`; сам файл `dual-db-sync.md` должен быть обновлён в этом же PR (FR-009). |
| VII. Cross-Machine Setup | N/A | Фича не касается локальных AI-конфигов/line-endings. |
| Ограничения и доступы агента | ⚠️ ТРЕБУЕТ СОГЛАСИЯ ПОЛЬЗОВАТЕЛЯ | Миграция новых колонок/таблицы на PROD БД и деплой `karaoke-web` на сервер — по прямому согласию пользователя на каждое действие (не автономно). |

**Вывод**: Gate пройден с одним намеренным, явно задокументированным исключением из Principle III
(п. «Двух-БД синхронизация») — см. Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/083-auto-news-song-release/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения и их обоснование
├── data-model.md         # Phase 1 — новые сущности/поля
├── quickstart.md         # Phase 1 — сценарий ручной проверки
└── contracts/
    └── news-api.md       # Phase 1 — что меняется в существующем News API (без новых публичных эндпоинтов)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                       # + вынесенное общее свойство готовности к просмотру
│   │                                  #   (переиспользуется PublicPlayerController И новым механизмом)
│   └── News.kt                       # + song_id/source, + createAutoAnnouncement(...)
├── sync/SyncTarget.kt                # NewsSyncTarget.listHashes — WHERE-фильтр, исключающий auto-created
└── services/
    └── SongReleaseAnnouncementService.kt   # новый: readiness-check + идемпотентная запись метки + создание News

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── MainController.kt                 # doChangeRecords(...) — вызов сервиса анонсов после applied-операций

deploy/karaoke-db/
└── NN_song_news_announced.sql        # DDL: новая таблица + новые колонки tbl_news (обе БД, вручную)

webvue3/src/
├── components/News/NewsTable.vue     # отображение источника новости (авто/ручная), если понадобится
└── views/NewsView.vue                # опционально: переключатель LOCAL/PROD-новостей (target=remote)
```

**Structure Decision**: Фича не добавляет новый модуль/проект — это точечное расширение уже
существующих `karaoke-app` (общие модели, переиспользуемые как библиотека) и `karaoke-web`
(единственная точка входа, реально работающая на PROD). Новый Kotlin-объект
`SongReleaseAnnouncementService` инкапсулирует всю бизнес-логику детекции и создания анонса, чтобы
`MainController.doChangeRecords` не разрастался и чтобы эту же логику потом можно было вызвать из
теста/CLI без HTTP.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| Автоматически созданные новости не участвуют в LOCAL↔SERVER sync для `news` (частичное исключение из Principle III) | Единственная альтернатива — включить их в обычный push/pull scope — создаёт реальный риск потери данных: при следующем admin-триггерном «1 клик» с LOCAL→SERVER и включённым `sync_news_push_delete_allowed` (переключается в UI, дефолт в коде — не гарантия реального runtime-значения) sync-движок увидел бы auto-созданную запись как «есть на SERVER, нет на LOCAL» и удалил бы её как «данные, удалённые в источнике» (mirror-delete). Отдельная, невидимая для generic-diff таблица/условие делает такую потерю данных структурно невозможной, а не «выключенной по умолчанию, но взрываемой одним чек-боксом». | Альтернатива «завести отдельный SyncTarget с `oneClickDirection = SERVER_TO_LOCAL`, чтобы утекало и на LOCAL» рассматривалась и отклонена как избыточная для этой фичи: она решает лишь «удобство просмотра админом», а не correctness, добавляет новое направление синхронизации ради данных, которые по определению актуальны только на PROD (см. Assumptions в spec.md — авто-новость это чисто PROD-событие). Видимость для администратора уже решается существующим `target=remote`-параметром `NewsController` без нового кода синхронизации. |
