# Implementation Plan: Переименование параметров/переменных типа Song с имени `settings` на `song`

**Branch**: `102-rename-song-settings-vars` | **Date**: 2026-08-02 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/102-rename-song-settings-vars/spec.md`

## Summary

Исторический рефакторинг класса `Settings` → `Song` оставил десятки мест, где
Kotlin-идентификаторы (параметры, локальные переменные, поля классов) типа
`Song` всё ещё называются `settings`. По результатам `/speckit-clarify`
охват расширен за пределы чисто внутренних Kotlin-имён: переименованию
подлежат также два DTO-поля, сериализуемые в JSON (`HealthReportDTO`,
`KaraokeProcessDTO`), один HTTP-параметр и 54 параметра legacy-формы
(`@RequestParam`, биндящиеся по имени), один SSE-ключ — вместе со всеми их
потребителями в `webvue3` и трёх legacy Thymeleaf-шаблонах. Физическая схема
БД (колонка `settings_id`) и модуль `karaoke-public` явно исключены из
области. Технический подход: механическое, построчно проверяемое
переименование идентификаторов без изменения логики, одним PR, с
компиляцией/линтом/ручной UI-проверкой на каждом значимом срезе перед
финальной сборкой PR.

## Technical Context

**Language/Version**: Kotlin 2.2.20 (JVM, JDK 17), Spring Boot 3.x — backend
(`karaoke-app`, `karaoke-web`); JavaScript/Vue 3 (Vite) — `webvue3`; Thymeleaf
(server-rendered HTML) — legacy admin-формы в `karaoke-app`.

**Primary Dependencies**: Spring MVC (`@RequestParam`, `@RequestMapping`),
Jackson (неявная JSON-сериализация DTO для webvue3), Vuex (`store.js` в
webvue3), Server-Sent Events (`SseNotification.kt` + `EventSource`-потребители
в webvue3), Thymeleaf.

**Storage**: PostgreSQL через сырой JDBC (`KaraokeDbTable`,
`@KaraokeDbTableField`). Схема БД НЕ меняется — колонка `settings_id` и любые
другие физические имена остаются как есть (см. FR-005 spec.md).

**Testing**: `karaoke-app/src/test` — преимущественно `@Disabled`
интеграционные тесты (сеть/браузер/credentials), не используются как gate
(см. constitution.md, «Рабочий процесс»). Проверка — компиляция
(`./gradlew build`), статический анализ (`ktlintCheck`, `npm run lint:check`)
и ручная UI/HTTP-проверка пользователем согласно `quickstart.md`.

**Target Platform**: Backend — Docker-контейнеры (`karaoke-app` только на
admin-машине, `karaoke-web` на проде); Frontend — статическая сборка
`webvue3`, обслуживаемая тем же nginx, что и `karaoke-web`.

**Project Type**: Web-приложение (backend + admin-frontend), рефакторинг
существующего кода без новых экранов/эндпоинтов.

**Performance Goals**: Не применимо — переименование не меняет
производительность (FR-008: поведение не меняется).

**Constraints**:
- Ноль изменений поведения/логики (FR-008).
- Ноль изменений физической схемы БД (FR-005).
- `karaoke-public` не затрагивается (FR-014).
- Все контрактные переименования (DTO/HTTP/SSE) обязаны сопровождаться
  синхронной правкой всех известных потребителей в том же PR (FR-010…FR-012,
  FR-015).
- Единый PR, единый атомарный деплой backend+frontend (FR-016).
- `ktlintCheck`, ESLint/Prettier (`webvue3`), KDoc/JSDoc coverage-гейты
  (constitution VI) не должны получить новых нарушений.

**Scale/Scope**: ~35 Kotlin-файлов (`karaoke-app`, `karaoke-web`), 281
объявление (`val`/`var`/`fun`-параметр/поле) с типом или значением `Song` под
именем `settings*` — 54 прямые сигнатуры (в 14 файлах) + 227 `val`/`var`
(в 30 файлах); 2 DTO (4 поля); 1 HTTP-эндпоинт с одним параметром + 2
метода legacy-формы (54 параметра, по 27 в каждом) + 3 Thymeleaf-шаблона;
1 SSE-ключ + его потребитель в `webvue3`; 1 per-feature документ с
устаревшим примером (`docs/features/premium-stems.md`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Оценка | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Рефакторинг не трогает ffmpeg/melt/Demucs/Sheetsage пайплайн и не вводит внешние SaaS-зависимости. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | JPA/Hibernate не вводится. `associateBy { it.id }`-паттерн не затрагивается (переименование не меняет алгоритм diff). Колонка `settings_id` и `@KaraokeDbTableField(name = "settings_id")` остаются как есть (FR-005) — маппинг колонка↔свойство не ломается. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS (с проверкой) | `Song`/`tbl_songs` — цель синхронизации. Рефакторинг не добавляет/не удаляет/не переименовывает колонки → **recordhash-триггер пересоздавать не требуется**. Явно зафиксировать в tasks.md как проверочный пункт (не просто предположение). |
| IV. Async-очередь задач с парсингом stdout | ✅ PASS | `KaraokeProcess.settingsId` → `songId` — переименование поля, не меняющее `ProcessBuilder`/`redirectErrorStream`/threadId-логику. |
| V. Двух-фронтенд: admin и public — разные приложения | ✅ PASS | Изменения контракта (DTO/HTTP/SSE) затрагивают только `webvue3` (admin) и legacy Thymeleaf-admin-формы. `karaoke-public` явно исключён (FR-014) — принцип «не смешивать admin/public» соблюдён. |
| VI. Code Standards (NON-NEGOTIABLE) | ⚠️ ТРЕБУЕТ ДЕЙСТВИЯ | (a) `ktlintCheck`/ESLint не должны дать новых нарушений — проверяется после каждого файла. (b) KDoc/JSDoc coverage — переименование параметра может задеть `@param`-теги в KDoc, если они ссылаются на старое имя `settings` — требуется точечная проверка/правка KDoc у публичных функций из FR-001. (c) **FR-009 (per-feature документ)**: код, который меняется, относится к подсистемам `async-process-queue` (`KaraokeProcess*`), `sse-notifications` (`SseNotification.kt`), `songs-table` (`webvue3/Songs/*`) и упоминается в `premium-stems.md`. Проверка существующих per-feature документов показала, что **только `docs/features/premium-stems.md:44`** содержит фактически устаревающий пример кода (`settingsId=0` → должно стать `songId=0`; там же ещё более старая формулировка «нет привязки к `Settings`/`tbl_settings»`, тоже требующая обновления на `Song`/`tbl_songs`). Остальные per-feature документы (`async-process-queue.md`, `dual-db-sync.md`, `sse-notifications.md`, `songs-table.md`) либо не содержат конкретных идентификаторов, которые устаревают (используют только физическое имя колонки `settings_id`, не меняющееся), либо не содержат кода вовсе — обновление не требуется по существу, но нужно включить их короткий review-проход в tasks.md, чтобы не пропустить скрытый пример. |
| VII. Cross-Machine Setup | ✅ PASS (с действием) | Коммит(ы) этого PR — механический мультифайловый рефакторинг → добавить хэш в `.git-blame-ignore-revs` после мержа (VII.2), см. tasks.md. |

**Итог**: Gate пройден без непримиримых нарушений. Единственное
действие-требование — точечное обновление `docs/features/premium-stems.md`
(FR-009) и ревью KDoc/JSDoc на переименованных публичных сигнатурах (VI) —
оба перенесены в `tasks.md` как обычные задачи, не как исключение
Complexity Tracking.

**Пере-проверка после Phase 1** (`research.md`, `data-model.md`,
`contracts/`, `quickstart.md`): дизайн не добавил новых нарушений и не
расширил исключения из Категории 6 (`data-model.md`) — в частности,
разведка подтвердила, что `KaraokeProcessDTO.settingsId` не имеет
фронтенд-потребителя (Решение 1, `research.md`), что снижает, а не
увеличивает риск по сравнению с оценкой до Phase 1. Gate остаётся PASS.

## Project Structure

### Documentation (this feature)

```text
specs/102-rename-song-settings-vars/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения по неоднозначным местам обнаружения
├── data-model.md         # Phase 1 — таблица идентификаторов "было → стало"
├── quickstart.md        # Phase 1 — сценарии ручной проверки
├── contracts/           # Phase 1 — HTTP/SSE/JSON контракты "было → стало"
└── tasks.md             # Phase 2 (/speckit-tasks, отдельная команда)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                          # ~10 сигнатур/локальных var, settingsId (форма-параметры)
├── UtilsAI.kt                        # 5 сигнатур
├── UtilsPictures.kt                  # ~10 сигнатур
├── Functions.kt                      # 2 сигнатуры
├── KaraokePlatform.kt                # 1 сигнатура (settingsFieldPublicationId/-VersionNumber — НЕ трогать, FR-004)
├── KaraokePlatformPublication.kt     # 1 сигнатура
├── KaraokeProcess.kt                 # var settingsId: Int -> songId (+ все обращения), НЕ трогать @KaraokeDbTableField(name="settings_id")
├── KaraokeProcessWorker.kt           # settingsId-обращения, settingsLocal
├── KaraokeProcessDTO.kt              # val settingsId -> songId (DTO, проверить потребителей)
├── HealthReport.kt                   # val settings: Song (конструктор) -> song; settingsId-параметры функций
├── HealthReportDTO.kt                # settingsId, settingsFileName -> songId, songFileName (DTO, JSON-контракт)
├── SseNotification.kt                # "settingsId" to settingsId -> "songId" to songId
├── StemJobPollScheduler.kt           # .settingsId = 0 (поле KaraokeProcess, переименовать по месту)
├── StemJobProcessing.kt              # .settingsId = 0
├── model/Song.kt                     # внутренние `settings`-параметры компаньон-методов
├── model/SongRenderContext.kt        # val settings: Song (конструктор) -> song
├── model/Zakroma.kt                  # settingsByAuthor, settingsByAlbum -> songsByAuthor/songsByAlbum
├── mlt/mko/*.kt (13 файлов)          # settings: Song параметры MKO-рендер функций
├── mlt/Mlt.kt                        # settings: Song параметры
├── services/PlayerMp4MuxService.kt   # settings: Song параметры
├── controllers/ApiController.kt      # settings: Song параметры + settingsId локальные
├── controllers/MainController.kt     # @RequestParam settingsId (/changesettingsstatus), 54 settings_xxx (/songs_update x2, 27+27)
├── controllers/SongEditorController.kt
└── resources/
    ├── static/settings_context.js    # fetch(...).../changesettingsstatus body key settingsId -> songId
    └── templates/{songs.html,songs2.html,area_center_column.html}  # form-поля settings_xxx -> song_xxx

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
├── PublicPlayerController.kt         # settings: Song параметры
└── (прочие контроллеры из первичного скана)

webvue3/src/components/
├── Songs/store.js                    # userEventData.settingsId -> songId (SSE-потребитель)
├── Songs/SongsTable.vue              # локальные переменные settingsId (цикл по id песни, не сам контракт — проверить при реализации)
└── Common/HealthReport/
    ├── store.js                      # item.settingsId -> item.songId (DTO-потребитель)
    └── components/{HealthReportTableBody.vue,HealthReportTableHeader.vue}

docs/features/premium-stems.md        # FR-009: settingsId=0 -> songId=0 (+ терминология Settings->Song)
.git-blame-ignore-revs                # VII.2: добавить хэш(и) финального коммита после мержа
```

**Structure Decision**: Изменения остаются в существующей структуре
монорепозитория (`karaoke-app`, `karaoke-web`, `webvue3`) — новых
директорий/модулей не создаётся. `karaoke-public` и `karaoke-vue` (legacy) не
затрагиваются. Порядок работы (см. `tasks.md` после `/speckit-tasks`):
сначала изолированные Kotlin-файлы без внешнего контракта (FR-001…FR-003,
FR-013), затем контрактные пары backend+frontend вместе (FR-010…FR-012) —
компилировать/линтовать после каждого файла, но мержить и деплоить только
одним финальным PR (FR-016).

## Complexity Tracking

> Пусто — Constitution Check не выявил нарушений, требующих обоснования.
> Единственные два не-нулевых пункта (обновление `premium-stems.md`,
> `.git-blame-ignore-revs`) — штатные обязательные действия по существующим
> принципам VI/VII, а не отклонения от них.
