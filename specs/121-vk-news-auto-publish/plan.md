# Implementation Plan: Автопубликация новостей в группу ВКонтакте

**Branch**: `121-vk-news-auto-publish` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/121-vk-news-auto-publish/spec.md`

## Summary

Автоматический кросс-постинг опубликованных на сайте новостей категории `air`
(«в эфире») в группу ВКонтакте с прикреплением демо-MP4 песни. Бот работает на
admin-машине в `karaoke-app` (как Telegram-Фаза 2, `specs/113-telegram-demo-publish`),
опрос выполняется отдельным `@Scheduled`-тиком каждые 5–10 минут по `tbl_news`
(категория `air`, `publish_at <= now()` в LOCAL-БД после sync). Идемпотентность —
по существующему полю `Song.idVk` (`tbl_songs.id_vk`): заполненное поле = публикация
уже была, бот пропускает. После успешной отправки поста ВКонтакте бот записывает
id поста в `Song.idVk` через штатный `Song.saveToDb()`. Демо-MP4 переиспользуется
тот же, что в Telegram-Фазе 2 (`RENDER_MP4_DEMO`); при отсутствии/превышении лимита
ставится задача рендера в `KaraokeProcess`. Технически — новый `VkAutoPublishService`
+ `VkApiClient` + `VkAutoPublishScheduler` по образцу `TelegramAutoPublish*`.

## Technical Context

**Language/Version**: Kotlin (JDK 17, проект уже на Kotlin 1.x / Spring Boot 2.x/3.x,
см. `constitution.md` «Технологический стек»).

**Primary Dependencies**:
- Spring Boot (`@Scheduled`, `@Component`, `@EventListener` — уже в проекте)
- JDK `java.net.http.HttpClient` (как `TelegramApiClient.kt:126` — прямой/fallback-прокси)
- `kotlinx.serialization` (JSON для VK API ответов, уже в проекте)
- `KaraokeProcess` / `KaraokeProcessTypes.RENDER_MP4_DEMO` (переиспользование
  async-очереди рендера, Constitution Principle IV)
- `Song.saveToDb()` / `SongField.ID_VK` (запись `idVk`, Constitution Principle II)
- `News` / `News.loadPublished`-аналог / `tbl_news.song_id` (источник триггера `air`)
- `KaraokeProperties` String-значения (для шаблонов `vkTemplateAir` /
  `vkTemplatePremium` — многострочные строки через base64-сериализацию,
  `KaraokePropertySerializable.create` String-branch)

**Storage**:
- PostgreSQL (LOCAL на admin-машине): `tbl_songs` (`id_vk`, `publish_date`,
  `publish_time`, `player_readiness_flags`), `tbl_news` (`publish_at`, `category`,
  `song_id`), `tbl_processes` (статус `RENDER_MP4_DEMO`)
- MinIO: демо-MP4 файлы (тот же путь, что в Telegram-Фазе 2 —
  `Song.pathToFileRenderMp4ForVersion(RenderVersion.DEMO)`)
- `KaraokeProperties` (base64-файл `/sm-karaoke/system/Karaoke.properties`,
  в git НЕ лежит): новые ключи `vkAutoPublish*`, `vkAccessToken`, `vkGroupId`

**Testing**: Интеграционное, вручную (CI-тестов нет, `constitution.md` «Тесты»:
существующие `@Disabled`, требуют сеть/credentials). Проверка — через
`quickstart.md` сценарии на admin-машине с реальной группой ВК (тестовой).

**Target Platform**: admin-машина (только `karaoke-app`, Constitution: «karaoke-app
на проде не разворачивается вовсе»). На проде бот не работает — читает LOCAL-БД
после sync `tbl_news` LOCAL↔SERVER (`NewsSyncTarget`, Constitution Principle III).

**Project Type**: backend-модуль в существующем multi-module Gradle проекте
(`karaoke-app`), новый per-feature документ `docs/features/vk-news-auto-publish.md`
(Constitution Principle VI FR-009).

**Performance Goals**:
- Окно срабатывания 5–15 минут после того, как sync принёс в LOCAL опубликованную
  `air`-новость (FR-002a, Assumptions)
- Пакетная отправка нескольких новостей с соблюдением VK API rate limit
  (FR-006, FR-011) — не более N постов/час (точное значение — research.md)

**Constraints**:
- VK API `wall.post` лимит длины текста: 10 000 символов (FR-005)
- VK API лимит размера видео через `video.save`: точное значение — research.md
  (FR-020, FR-004 в `specs/113-telegram-demo-publish` — 50 МБ для Telegram,
  для VK отличается)
- VK API rate limit на `wall.post`: точное значение — research.md (FR-006)
- Прокси-fallback для VK API (если admin-машина за firewall — по образцу
  `TelegramApiClient.send` прямой→прокси, FR-009)
- Готовность песни (FR-022): статус ≥ 6 + флаги готовности плеера (то же условие,
  что `specs/089-auto-news-song-release` FR-009, `specs/113-telegram-demo-publish`
  FR-011) — не публиковать битое демо

**Scale/Scope**: ~18k песен на проде, ~19000 новостей в `tbl_news` (большинство —
исторические, исключаются снимком-бэклогом FR-012 по заполненному `idVk`).
Новый поток постов ВК — только новые `air`-новости после включения фичи.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Обоснование |
|-----------|--------|-------------|
| I. Self-contained автопайплайн | ✅ PASS | Рендер демо-MP4 локально через `KaraokeProcess.RENDER_MP4_DEMO` (без внешних SaaS в горячем пути). VK API — только для публикации готового контента, не в горячем пути обработки медиа. Аналог Telegram-Фазе 2, уже одобренной в `specs/113-telegram-demo-publish`. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS | Запись `idVk` через штатный `Song.saveToDb()` (дифф, recordhash-триггер, SSE). Чтение `tbl_news` через существующий `News.loadPublished`-аналог (raw SQL). Никаких JPA/Hibernate. |
| III. Двух-БД синхронизация | ✅ PASS | `tbl_songs.id_vk` уже участвует в sync (есть recordhash-триггер, `SongField.ID_VK` в `getDiff`). `tbl_news` синхронизируется через `NewsSyncTarget`. **Новых полей/колонок НЕ вводится** — переиспользуется `tbl_songs.id_vk`. Никаких миграций БД и пересозданий recordhash-триггеров не требуется. |
| IV. Async-очередь с парсингом stdout | ✅ PASS | Рендер через `KaraokeProcess.createProcess(action=RENDER_MP4_DEMO, threadId=HEAVY_RENDER)`, `redirectErrorStream(true)` (уже в `KaraokeProcess`). Прогресс парсится из stdout. |
| V. Двух-фронтенд | ✅ PASS | UI — в `webvue3` (admin): признак `idVk` уже отображается в карточке песни (`haveVkGroup`, `flagVk`); кнопка «Опубликовать во ВКонтакте сейчас» — новый элемент в карточке песни. На `karaoke-public` изменения не нужны. |
| VI. Code Standards (KDoc/JSDoc) | ✅ PASS | Новые классы (`VkAutoPublishService`, `VkApiClient`, `VkAutoPublishScheduler`, `VkAutoPublishState`, `VkAutoPublishResult`) — с KDoc и `@see docs/features/vk-news-auto-publish.md`. Новый per-feature документ `docs/features/vk-news-auto-publish.md` (FR-009). CI-gate: ktlint, KDoc coverage. |
| VII. Cross-Machine Setup | ✅ PASS | Секреты (`vkAccessToken`) — в `KaraokeProperties` (base64, в git НЕ лежит), НЕ коммитить. Никаких локальных AI-конфигов. Сборка/деплой через существующие `deploy/do.sh`. |

**GATE RESULT**: ✅ PASS — все 7 принципов соблюдены. Нарушений нет. Complexity
Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/121-vk-news-auto-publish/
├── plan.md              # Этот файл
├── research.md          # Phase 0: VK API research, лимиты, токены, шаблоны
├── data-model.md        # Phase 1: сущности, поля, состояния, типы публикаций
├── quickstart.md        # Phase 1: ручная проверка end-to-end (air + premium + редактор)
├── contracts/
│   └── vk-api-contract.md   # Phase 1: VK API + внутренние endpoints
└── tasks.md             # Phase 2 (/speckit.tasks — НЕ создается здесь)
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/
│   ├── VkAutoPublishService.kt       # НОВЫЙ: цикл публикации (type: PublicationType параметр) (по образцу TelegramAutoPublishService)
│   ├── VkAutoPublishScheduler.kt     # НОВЫЙ: @Scheduled тик (air-триггер)
│   ├── VkAutoPublishSchedulerStarter.kt  # НОВЫЙ: @EventListener лог старта
│   ├── VkApiClient.kt                # НОВЫЙ: VK API client (wall.post, video.save)
│   ├── VkAutoPublishState.kt         # НОВЫЙ: enum состояний
│   ├── VkAutoPublishResult.kt        # НОВЫЙ: результат цикла
│   └── VkTemplateService.kt          # НОВЫЙ: рендеринг шаблонов с плейсхолдерами (FR-023)
├── model/
│   ├── PublicationType.kt            # НОВЫЙ: enum AIR/PREMIUM (FR-027 — расширяемо)
│   ├── Song.kt                       # ИЗМЕНЁННЫЙ: getter'ы для VK-состояния, запись idVk
│   └── SongField.kt                  # БЕЗ ИЗМЕНЕНИЙ (ID_VK уже существует)
├── controllers/
│   └── ApiController.kt              # ИЗМЕНЁННЫЙ: endpoint'ы /api/song/publishToVkNow?type=, /api/vk/templates (GET/POST)
└── KaraokeProperties.kt               # ИЗМЕНЁННЫЙ: новые ключи listKaraokeProperties (vkAutoPublish*, vkAccessToken, vkGroupId, vkTemplateAir, vkTemplatePremium)

docs/features/
└── vk-news-auto-publish.md           # НОВЫЙ: per-feature документ (Constitution Principle VI FR-009)

webvue3/src/
├── components/Songs/edit/
│   └── SongEdit.vue                  # ИЗМЕНЁННЫЙ: кнопка «Опубликовать во ВК (air)» и «Опубликовать во ВК (premium)» (FR-016, FR-026)
└── components/VkTemplates/           # НОВЫЙ каталог (или Views/VkTemplates/ — по конвенции webvue3)
    └── VkTemplatesEditor.vue         # НОВЫЙ: редактор шаблонов ВК (FR-025)

deploy/karaoke-db/                    # БЕЗ ИЗМЕНЕНИЙ (tbl_songs.id_vk уже существует, recordhash-триггер на месте)
```

**Structure Decision**: Backend-first, по образцу Telegram-Фазы 2.
Расширение: `PublicationType` enum (AIR/PREMIUM, FR-027 расширяемо),
`VkTemplateService` для рендеринга шаблонов с плейсхолдерами, новый
endpoint `/api/vk/templates` (GET/POST) для редактора шаблонов, новый
UI-компонент `VkTemplatesEditor.vue` в `webvue3`. Все новые классы в
`karaoke-app/services/` рядом с `TelegramAutoPublish*`. Никаких новых
таблиц/миграций БД — переиспользуется `tbl_songs.id_vk` и существующий
`NewsSyncTarget`. UI-правки: две кнопки в существующей карточке песни +
отдельная страница/компонент редактора шаблонов.