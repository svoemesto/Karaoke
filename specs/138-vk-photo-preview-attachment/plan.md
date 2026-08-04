# Implementation Plan: Надёжное превью публикации ВК через прикрепление обложки фото

**Branch**: `132-vk-photo-preview-attachment` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/132-vk-photo-preview-attachment/spec.md`

## Summary

Обеспечить **100%**-е появление графического превью (картинки-обложки)
во всех новых постах бота ВКонтакте, загружая PNG-обложку песни через
`photos.getWallUploadServer` + `photos.saveWallPhoto` (с уже настроенным
user-token, scope `photos`) и прикрепляя её к посту через
`attachments=photo<owner>_<id>,video<owner>_<video_id>` в `wall.post`.

VK API при бот-публикации (`POST /method/wall.post`) не парсит URL в тексте
для генерации сниппета, поэтому все ранее принятые меры с Open Graph
(nginx rewrite, прогрев PNG-кэша `specs/130`) работают только для ручной
публикации через UI ВКонтакте. Прикрепление фото через `attachments` —
**надёжное** решение: VK берёт фото из API-параметра, не парся URL.

Расширяем PNG-обложку с 537×240 до 1200×630 (стандарт Open Graph,
рекомендуемый VK). При сбое `photos.*` методов — fallback через `docs.*`
(community-token имеет право `docs`). Если оба способа не сработали —
публикация в деградированном виде (без превью), но не отказ.

Все существующие инварианты сохраняются: идемпотентность по `Song.idVk`,
process-local lock, rate-limit 3 поста/час, retry 30с→2мин→5мин, прогрев PNG.

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot — без изменений;
используется существующий `VkApiClient` (`karaoke-app`) и endpoint
`/api/public/song-vk-image/{id}` (`karaoke-web`).

**Primary Dependencies**:
- `java.net.http.HttpClient` (JDK) для HTTP к VK API — уже используется в `VkApiClient`.
- `kotlinx.serialization.json` для парсинга ответов VK API — уже подключён.
- `java.awt.image.BufferedImage` + `javax.imageio.ImageIO` для генерации PNG
  — уже используется в `PublicApiController.songVkImage`.
- Никаких новых зависимостей не требуется.

**Storage**:
- `KaraokeProperties` (`/sm-karaoke/system/Karaoke.properties`) — без изменений:
  новые настройки добавляются по образцу `vkPreviewWarmup*` (specs/130).
- `tbl_songs` — без изменений: используется существующее поле `id_vk`.
- `/tmp/vk_<id>.png` (эфемерный кэш PNG в контейнере `karaoke-web`) — без изменений.
- MinIO (для исходных картинок альбома/автора) — без изменений.

**Testing**: ручная проверка через quickstart.md (5 сценариев); существующий
unit-тест `VkPreviewWarmupClientTest` — без изменений; интеграционных тестов
нет (см. `AGENTS.md` «Тесты»).

**Target Platform**: Linux server (admin-машина, где работает `karaoke-app`),
Docker-контейнеры — без изменений.

**Project Type**: Web service (backend + публичный web). Изменения в
`karaoke-app` (Kotlin, сервис публикации) и `karaoke-web` (Kotlin, endpoint PNG).

**Performance Goals**:
- Время публикации увеличивается не более чем на **+3 секунды** (SC-006) —
  загрузка одного PNG через `photos.*` методы занимает <<1 секунды при нормальной сети.
- Все остальные latency остаются на текущем уровне.

**Constraints**:
- User-token с scope `photos` уже настроен в `KaraokeProperties.vkUserAccessToken`
  (получен 02.08.2026 через Implicit Flow Standalone-приложения со scopes
  `video,photos,wall,offline` — см. `ApiController.kt:7065`).
- Community-token (`vkAccessToken`) НЕ подходит для `photos.*` методов
  (документированное ограничение VK API, `error_code=27`) — НЕ пытаемся.
- Размер PNG не более 50 МБ (лимит VK API на загрузку фото); 1200×630 PNG
  с альбомом + автором + текстом ≈ 100-300 КБ — укладывается с запасом.
- Лимит `wall.post attachments` — до 10 прикреплений; используем 1 фото + 1 видео.

**Scale/Scope**:
- Публикация — десятки-сотни постов в месяц (текущий темп ~3 поста/день,
  при росте до 10 постов/день в перспективе).
- Альбом группы ВКонтакте будет пополняться ~1 фото на пост (~30-300 фото/мес).
  Очистка — отдельная задача (backlog, не входит в первую версию).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Self-contained автопайплайн
**PASS**: фича использует только локальные компоненты (`karaoke-app`,
`karaoke-web`, `KaraokeProperties`, MinIO) + стандартный VK API (не SaaS
в горячем пути обработки медиа). Никаких новых внешних зависимостей.

### Principle II — Сырой JDBC + дифф по хэшам
**PASS**: фича НЕ вносит изменений в схему БД. Используется существующее
поле `tbl_songs.id_vk` (FR-004 specs/121). Нет новых миграций, нет новых
записей в `SyncRegistry`. Идемпотентность по `Song.idVk` сохраняется.

### Principle III — Двух-БД синхронизация через SyncRegistry
**PASS**: фича НЕ добавляет новых сущностей в sync. Используется существующее
поле `tbl_songs.id_vk`, которое уже участвует в sync (recordhash-триггер на месте).

### Principle IV — Async-очередь задач с парсингом stdout
**N/A**: фича НЕ использует `KaraokeProcess` / subprocess. Все операции
синхронные в рамках существующего `VkAutoPublishService.publishToVk()` →
`publishFile()` / `publishTextOnly()`.

### Principle V — Двух-фронтенд: админка и публичный сайт — разные приложения
**PASS**: изменения только в backend (`karaoke-app`, `karaoke-web`).
`webvue3` и `karaoke-public` НЕ затрагиваются (нет UI-изменений в этой версии).

### Principle VI — Code Standards (NON-NEGOTIABLE)
**PASS**: новый код будет покрыт KDoc с `@see` на per-feature документ
(`docs/features/vk-news-auto-publish.md` — обновляется в том же PR).
Линтеры ktlint / ESLint уже настроены; baseline-файлы не должны расти
(новый код пишется в стиле существующего `VkApiClient` / `VkPreviewWarmupClient`).

### Principle VII — Cross-Machine Setup (NON-NEGOTIABLE)
**PASS**: фича НЕ требует изменений в `AGENTS.md`, `CLAUDE.md`,
`.git-blame-ignore-revs`, `.gitattributes`, `docs/onboarding.md`.

### Principle VIII — Секреты и git-гигиена (NON-NEGOTIABLE)
**PASS**: фича использует существующий секрет `vkUserAccessToken` (уже в
`KaraokeProperties`, в git НЕ трекается — проверка `git ls-files` возвращает
пусто). Никаких новых секретов, никаких hardcoded токенов в коде.
Новых секрет-файлов в `.gitignore` не требуется.

**Итог**: все 8 принципов PASS / N/A. Нарушений нет. Phase 0 разрешён.

## Project Structure

### Documentation (this feature)

```text
specs/132-vk-photo-preview-attachment/
├── plan.md              # Этот файл (/speckit.plan command output)
├── research.md          # Phase 0 output — обоснование выбора photos.* + docs.*
├── data-model.md        # Phase 1 output — нет изменений в БД, описание transient entities
├── contracts/
│   └── vk-photo-upload.md  # Phase 1 output — поток photos.*/docs.* методов
├── quickstart.md        # Phase 1 output — 5 сценариев проверки
├── checklists/
│   └── requirements.md  # Уже создан на этапе /speckit.specify
└── spec.md              # Уже создан на этапе /speckit.specify
```

### Source Code (repository root)

**Структура изменений** (только в существующих модулях, никаких новых):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── VkApiClient.kt        # + методы uploadWallPhoto(), saveWallPhoto(), 
│   │                         #   uploadWallDoc(), saveWallDoc(); +
│   │                         #   sendPostWithPhotoAndVideo() / wallPostWithPhoto()
│   ├── VkAutoPublishService.kt  # + шаг загрузки фото между прогревом и wall.post;
│   │                            #   + fallback на docs.*
│   └── VkAutoPublishState.kt  # без изменений (используем существующий SEND_FAILED)
└── KaraokeProperties.kt      # + настройка vkPhotoAttachEnabled=true (default)

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicApiController.kt     # songVkImage: размер 537×240 → 1200×630
                               #   (один параметр, без других изменений)

docs/features/
└── vk-news-auto-publish.md    # + секция "Превью через photos.saveWallPhoto"
                               #   (FR-001..FR-017 + скорректированный поток)
```

**Структура НЕ меняется**:
- Никаких новых пакетов / модулей.
- Никаких изменений в `webvue3` или `karaoke-public` (UI не меняется в v1).
- Никаких миграций БД.
- Никаких изменений в `tbl_settings`, `tbl_songs`, `tbl_news`.

**Structure Decision**: Single project (multi-module Gradle). Изменения
добавляются в существующие модули `karaoke-app` и `karaoke-web` (по
образцу `specs/130-vk-preview-generation` — там тоже расширяли
`PublicApiController.songVkImage` без новых модулей).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

Constitution Check не выявил нарушений — таблица пуста.

## Re-evaluation Constitution Check (post-design)

После Phase 1 design переоценка не требуется — дизайн не вводит новых
сущностей в БД, не меняет sync, не добавляет секретов, не меняет UI.
Все 8 принципов остаются в PASS / N/A.