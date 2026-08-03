# Implementation Plan: Премиум-автопубликация в Telegram и ВК при появлении песни в коллекции

**Branch**: `122-premium-auto-publish` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/122-premium-auto-publish/spec.md`

## Summary

Фича уже реализована и смёржена в master (коммиты `1386f063`, `59d13e72`) без
собственной спеки: `PremiumAutoPublishScheduler` (тик 30 сек) публикует
премиум-пост («песня появилась в коллекции», категория `premium`) в Telegram
и ВК при первом переходе `Song.newsAvailableAnnounced` false→true — без
сохранения id публикации (в отличие от AIR), через
`TelegramAutoPublishService`/`VkAutoPublishService` с `persistMessageId=false`/
`persistPostId=false`.

Баг-репорт («Telegram не публикуется, хотя новость+ВК публикуются») **не**
объясняется невключённой настройкой (`premiumAutoPublishEnabled=true`,
`telegramAutoPublishEnabled=true` на момент проверки) — root cause
структурный: ВК-премиум публикует ТОЛЬКО текст (community-токен не имеет
прав `video.save`) и завершается синхронно за один тик; Telegram-премиум
ВСЕГДА требует готовое демо-видео (`sendVideo` не имеет текстового fallback)
и, если видео нет, переходит в асинхронное состояние `rendering`, довершение
которого сегодня целиком зависит от `TelegramAutoPublishScheduler.resumeRenderingSongs()`
— планировщика **другой** фичи (`specs/113`, Phase 2 «в эфире»), гейтящегося
собственным флагом `telegramAutoPublishEnabled`. Плюс — у администратора
нет видимости состояния премиум-публикации нигде в `webvue3`, кроме
одноразового диалога бэкфилла на `HomeView.vue`.

План: (1) перенести логику «продолжить рендерящуюся премиум-песню» из
`TelegramAutoPublishScheduler`/`VkAutoPublishScheduler` (AIR-планировщики) в
сам `PremiumAutoPublishScheduler.tick()`, чтобы премиум-цикл не зависел от
чужих флагов; (2) добавить в `webvue3` (`SongEdit.vue`) видимый статус
премиум-публикации по обоим каналам + кнопку «Повторить» поверх уже
существующих `/api/song/publishPremiumTelegram`/`/api/song/publishPremiumVk`;
(3) уточнить учёт попыток (`premiumAttemptCount`) — раздельно по каналам.

## Technical Context

**Language/Version**: Kotlin (JDK 17, Spring Boot, см. `constitution.md`
«Технологический стек») для бэкенда; Vue 3 + Vite для `webvue3`.

**Primary Dependencies**:
- Существующие `TelegramAutoPublishService`/`VkAutoPublishService`
  (переиспользуются без изменения публичного API — только меняется, ОТКУДА
  вызывается `onRenderCompleted` для премиум-рендеров)
- `PremiumAutoPublishScheduler` (`@Scheduled`, тик 30 сек) — расширяется
  собственной фазой «resume rendering», по образцу
  `TelegramAutoPublishScheduler.resumeRenderingSongs()`/
  `VkAutoPublishScheduler`-аналога
- `KaraokeProcess`/`KaraokeProcessTypes.RENDER_MP4_DEMO` (существующая
  async-очередь, без изменений)
- `Song.saveToDb()` / `readinessStringFlag`/`readinessFlag` (JSON-блоб
  `player_readiness_flags`) — для нового раздельного учёта попыток на канал
- `webvue3`: `SongEdit.vue` (новый блок статуса + кнопка «Повторить»),
  существующие `/api/song/publishPremiumTelegram`/`/api/song/publishPremiumVk`

**Storage**:
- PostgreSQL (LOCAL, admin-машина): `tbl_songs.player_readiness_flags`
  (JSON) — уточнение схемы поля (раздельные счётчики попыток), БЕЗ новой
  миграции/колонки/recordhash-триггера (тот же паттерн, что и остальные
  флаги готовности, `specs/101-song-news-flag`)
- MinIO: демо-MP4 (тот же файл/путь, что AIR: `pathToFileRenderMp4ForVersion(RenderVersion.DEMO)`)

**Testing**: Интеграционное, вручную (CI-тестов нет, `constitution.md`
«Тесты»). Проверка — `quickstart.md`: принудительно перевести песню в
`newsPremiumPublishPending=true` без готового демо-MP4, выключить
`telegramAutoPublishEnabled`, убедиться, что Telegram-премиум всё равно
завершается после рендера; проверить UI-статус в `SongEdit.vue`.

**Target Platform**: admin-машина (`karaoke-app`, планировщик, рендер).
`webvue3` — тоже admin-only SPA (Constitution Principle V).

**Project Type**: backend-модуль в существующем multi-module Gradle
проекте + admin-фронтенд (`webvue3`). Новый per-feature документ
`docs/features/premium-auto-publish.md` (Constitution Principle VI FR-009) —
на сегодня фича не имела ни спеки, ни per-feature документа, это
восполняется в рамках этого плана.

**Performance Goals**: премиум-тик — 30 сек (без изменений). Завершение
рендерящейся Telegram-премиум-публикации — в течение одного тика после
терминального статуса `RENDER_MP4_DEMO`, независимо от
`telegramAutoPublishEnabled` (SC-004 spec.md).

**Constraints**:
- Идемпотентность и «не сохранять id» для премиум — уже реализованное,
  неизменяемое поведение (FR-004, FR-011 spec.md)
- Раздельные счётчики попыток (FR-010 spec.md) — без миграции БД, тот же
  JSON-блоб `player_readiness_flags`
- UI-статус (FR-006/FR-007 spec.md) — новый блок в `SongEdit.vue`, без
  изменения `karaoke-public` (Constitution Principle V — админка/паблик не
  смешивать)

**Scale/Scope**: премиум-цикл затрагивает только песни с
`newsPremiumPublishPending=true` (обычно — единицы одновременно, не весь
каталог ~18k песен). UI-изменение — один компонент (`SongEdit.vue`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Обоснование |
|-----------|--------|-------------|
| I. Self-contained автопайплайн | ✅ PASS | Рендер демо-MP4 остаётся локальным через `KaraokeProcess.RENDER_MP4_DEMO`, без изменений. Telegram/VK API — публикация готового контента, не горячий путь обработки медиа. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Все изменения полей — через `Song.saveToDb()` (дифф, recordhash, SSE). Новых прямых SQL-записей не вводится. |
| III. Двух-БД синхронизация | ✅ PASS | `player_readiness_flags` уже участвует в sync как единое JSON-поле; уточнение внутренней структуры (раздельные счётчики) НЕ меняет колонку/recordhash-триггер — миграция не требуется. |
| IV. Async-очередь с парсингом stdout | ✅ PASS | Рендер `RENDER_MP4_DEMO` переиспользуется как есть (`redirectErrorStream(true)` уже в `KaraokeProcess`). Изменяется только то, ЧТО вызывает `onRenderCompleted` по завершении — не сам механизм очереди. |
| V. Двух-фронтенд | ✅ PASS | Новый UI — только в `webvue3` (admin), карточка песни. `karaoke-public` не затрагивается. |
| VI. Code Standards (KDoc/JSDoc) | ✅ PASS | Изменяемые/новые методы (`PremiumAutoPublishScheduler.resumeRenderingSongs`, новые вычисляемые свойства статуса) — с KDoc/`@see docs/features/premium-auto-publish.md`. Новый per-feature документ создаётся в рамках этой фичи (backfill долга). |
| VII. Cross-Machine Setup | ✅ PASS | Изменения не затрагивают секреты/AI-конфиги/сборку. |
| VIII. Секреты и git-гигиена | ✅ PASS | Токены (`telegramBotToken`, `vkAccessToken`) не меняются и не логируются; изменения не затрагивают `.env`/секрет-файлы. |

**GATE RESULT**: ✅ PASS — все 8 принципов соблюдены. Нарушений нет.
Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/122-premium-auto-publish/
├── plan.md              # Этот файл
├── research.md          # Phase 0: диагностика root cause + решения по FR-003/FR-010
├── data-model.md        # Phase 1: поля player_readiness_flags, состояния, производные статусы
├── quickstart.md        # Phase 1: ручная проверка end-to-end (рендер + UI-статус + повтор)
├── contracts/
│   └── internal-api.md  # Phase 1: существующие + уточнённые внутренние endpoint'ы
└── tasks.md             # Phase 2 (/speckit.tasks — НЕ создаётся здесь)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── PremiumAutoPublishScheduler.kt   # ИЗМЕНЯЕТСЯ: своя фаза resumeRenderingSongs()
│   ├── TelegramAutoPublishService.kt    # БЕЗ ИЗМЕНЕНИЙ (только вызывающая сторона меняется)
│   ├── TelegramAutoPublishScheduler.kt  # БЕЗ ИЗМЕНЕНИЙ (продолжает обслуживать AIR)
│   ├── VkAutoPublishService.kt          # БЕЗ ИЗМЕНЕНИЙ
│   └── VkAutoPublishScheduler.kt        # БЕЗ ИЗМЕНЕНИЙ (продолжает обслуживать AIR)
├── model/
│   └── Song.kt                          # ИЗМЕНЯЕТСЯ: раздельные счётчики попыток (FR-010),
│                                         #   новые производные getter'ы статуса премиум-публикации (FR-006/FR-009)
└── controllers/
    └── ApiController.kt                 # БЕЗ ИЗМЕНЕНИЙ (publishPremiumTelegram/publishPremiumVk уже есть)

webvue3/src/components/Songs/edit/
└── SongEdit.vue                         # ИЗМЕНЯЕТСЯ: блок статуса премиум-публикации + кнопка «Повторить»

docs/features/
└── premium-auto-publish.md              # НОВЫЙ: per-feature документ (Constitution Principle VI FR-009, backfill долга)
```

**Structure Decision**: Изменения точечные, в пределах уже существующей
структуры `karaoke-app`/`webvue3` — новых модулей/сервисов не создаётся,
переиспользуется весь существующий цикл рендера/отправки/шаблонов из
`specs/113`/`specs/121`. Единственный новый файл — per-feature документ
`docs/features/premium-auto-publish.md` (ранее отсутствовал, хотя код на
него уже ссылался).

## Complexity Tracking

*Не требуется — Constitution Check пройден без нарушений.*
