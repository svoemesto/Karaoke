---
description: "Task list for SEO-HTML endpoint for bots"
---

# Tasks: SEO-HTML endpoint для ботов (specs/180-og-seo-html)

**Input**: Design documents from `/specs/180-og-seo-html/`
- `plan.md` (technical context, Constitution Check)
- `spec.md` (4 user stories P1–P3, 15 FR, 7 SC)
- `research.md` (8 design decisions)
- `data-model.md` (existing entities, no new entities)
- `contracts/og-html-endpoint.md` (HTTP contract)
- `quickstart.md` (10 manual validation scenarios)

**Branch**: `180-og-seo-html`
**Target**: заменить генерацию PNG на SEO-HTML в `PublicOgSongController.kt`
**Scope**: одна точка изменения (`PublicOgSongController.ogSongHtml()`), без миграций БД, без изменений nginx

**Tests**: тесты НЕ включены (см. AGENTS.md «Тесты»: существующие тесты — интеграционные, большинство `@Disabled`, валидация проводится пользователем вручную через `quickstart.md`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: можно делать параллельно (разные файлы, нет зависимостей)
- **[Story]**: привязка к user story (US1, US3 — US2 и US4 не входят в tasks)
- Все пути абсолютные, от корня репозитория `/home/nsa/Karaoke/`

## Path Conventions

**Web service**: `karaoke-web/src/main/kotlin/...` (Kotlin, Spring Boot).
**Spec**: `specs/180-og-seo-html/`.
**Docs**: `docs/features/` и `docs/architecture-notes.md`.

## Phase 1: Setup (Shared Infrastructure)

**Цель**: подготовить инфраструктуру для фичи — проверить baseline, создать per-feature документ (FR-015 спеки), без которого изменения не пройдут CI.

- [x] T001 Запустить `./gradlew ktlintCheck` в `/home/nsa/Karaoke/` для проверки, что baseline не загрязнён
- [x] T002 Создать `/home/nsa/Karaoke/docs/features/seo-html-for-bots.md` со структурой: «Что делает», «Зачем», «Как работает», «Инварианты / правила», «Известные ловушки», «Ссылки» (FR-015 спеки; см. `tools/check-feature-doc.sh`)

**Checkpoint**: baseline чист, per-feature документ существует.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Цель**: подготовить helper-функции, которые используются во всех user stories (escape, JSON-LD-escape, picture URL construction).

- [x] T003 [P] Добавить helper-функцию `escapeJsonLd(s: String): String` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` — расширяет существующий `escape()` экранированием `\` и непечатных символов для безопасной вставки в JSON-LD блок (FR-005 спеки)
- [x] T004 [P] Добавить helper-функцию `buildAlbumImageUrl(song: Song): String` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` — возвращает абсолютный URL обложки альбома из MinIO (`https://sm-karaoke.ru/minio/karaoke/{storageFileName}`) или fallback на `https://sm-karaoke.ru/KARAOKE_LOGO.png` если обложки нет (R4 research.md)
- [x] T005 [P] Добавить helper-функцию `formatDurationMs(ms: Long): String` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` — форматирует длительность `mm:ss` (например, `03:45`); возвращает пустую строку для `ms <= 0`

**Checkpoint**: helper-функции готовы, можно реализовывать user stories.

---

## Phase 3: User Story 1 — Поисковый бот индексирует SEO-HTML за миллисекунды (Priority: P1) 🎯 MVP

**Goal**: заменить тело `ogSongHtml()` на полноценный SEO-HTML ответ (canonical, OG, Twitter Card, JSON-LD, semantic body) — без обращения к MinIO, с TTFB < 100 мс.

**Independent Test**: `curl -H "User-Agent: Mozilla/5.0 (compatible; YandexBot/3.0)" "https://localhost/api/public/og/song?id=11661"` возвращает HTTP 200, TTFB < 100 мс, Content-Type `text/html; charset=UTF-8`, body содержит `<title>`, canonical, OG-теги, Twitter Card, JSON-LD `MusicRecording`, видимые `<h1>`, `<h2>`, `<section id="meta">`, `<section id="lyrics">`, `<section id="listen">` (см. quickstart.md сценарий 1, 4).

### Implementation for User Story 1

- [x] T006 [US1] В файле `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` заменить тело `ogSongHtml()`: вместо `buildBareHtmlForVK(title, imageUrl)` вызвать новый `buildSeoHtmlForBots(song)` который формирует полный SEO-HTML (FR-001, FR-002 спеки)
- [x] T007 [US1] В том же файле реализовать `buildSeoHtmlForBots(song: Song): String` — формирует `<head>` с `<title>`, `<meta name="description">`, `<link rel="canonical">`, `<meta name="robots">`, Open Graph (`og:title/description/url/type=music.song/site_name/locale/image/image:width/image:height/image:alt`), Twitter Card (`twitter:card=summary_large_image/title/description/image`), JSON-LD блок `<script type="application/ld+json">` (FR-002, FR-003 спеки)
- [x] T008 [US1] В том же файле реализовать формирование видимого `<body>` в `buildSeoHtmlForBots`: `<header>` с `<h1>{songName}</h1>`, `<h2>{author}</h2>`, `<p>{shortDescription}</p>` (если не пусто), `<p class="warning">{warning}</p>` (если не пусто); `<section id="meta">` с `<dl>` для года, альбома, трека, тональности, BPM, жанров, длительности (только непустые поля); `<section id="description">` (если `description` не пусто); `<section id="lyrics">` с `<pre>formattedTextSong</pre>` (если `idStatus >= 3`); `<section id="chords">` с `<pre>formattedTextChords</pre>` или `formattedTextTabs` (если не пусто); `<section id="listen">` с `<ul>` ссылок на непустые `linkSponsrPlay/linkVkKaraoke/linkTgKaraoke/linkDzenKaraoke/linkMaxKaraoke/linkPlKaraoke/...`; `<footer>` с копирайтом и canonical (FR-004 спеки)
- [x] T009 [US1] В том же файле реализовать формирование JSON-LD блока в `buildSeoHtmlForBots`: `@context: "https://schema.org"`, `@type: "MusicRecording"`, `@id: canonical URL`, `name: songName`, `byArtist: { @type: "MusicGroup", name: author }`, `inAlbum: { @type: "MusicAlbum", name: album, datePublished: year }` (если album не пуст), `datePublished: year` (если не 0), `genre: [tags без SKIP]`, `inLanguage: "ru"`, `description: description` (или fallback), `url: canonical`, `image: albumImageUrl`, `lyrics: { @type: "CreativeWork", text: formattedTextSong }` (если не пуст и idStatus >= 3 и не SKIP), `isAccessibleForFree: song.isFreelyAvailableNow` (FR-003 спеки)
- [x] T010 [US1] Удалить старую функцию `buildBareHtmlForVK(title, imageUrl)` из `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` (больше не используется, см. R6 research.md)
- [x] T011 [US1] Обновить KDoc контроллера `PublicOgSongController` в `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt`: отразить новую концепцию (SEO-HTML вместо «голого» HTML для VK), указать, что endpoint больше НЕ генерирует PNG «на лету» и НЕ вызывает `/api/public/song-vk-image/{id}`, добавить `@see docs/features/seo-html-for-bots.md` и `@see specs/180-og-seo-html/spec.md` (FR-014 спеки; constitution.md Principle VI FR-006)
- [ ] T012 [US1] Manual validation: запустить `cd /home/nsa/Karaoke && bash deploy/do.sh build_start_web` для пересборки и рестарта karaoke-web; дождаться готовности контейнера (`docker logs -f karaoke-web | grep "Started"`)
- [ ] T013 [US1] Manual validation (SC-001, FR-001): выполнить `time curl -sS -H "User-Agent: Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)" "https://localhost/api/public/og/song?id=11661" -o /tmp/og-test.html -w "HTTP %{http_code}, TTFB: %{time_starttransfer}s, Size: %{size_download} bytes\n"` и убедиться: HTTP 200, TTFB < 0.1s, Size 5–50 КБ
- [ ] T014 [US1] Manual validation (SC-004, FR-002): выполнить скрипт из quickstart.md «Сценарий 4» (grep по `/tmp/og-test.html` для всех обязательных элементов: title, canonical, og:*, twitter:*, JSON-LD, h1, h2, sections); должно быть 0 FAIL
- [ ] T015 [US1] Manual validation (FR-003): извлечь JSON-LD блок из `/tmp/og-test.html` через `python3 -c "..."` (см. quickstart.md «Сценарий 1») и убедиться, что JSON валиден, `@type == "MusicRecording"`, присутствуют `name` и `byArtist`
- [ ] T016 [US1] Manual validation (SC-002): выполнить скрипт из quickstart.md «Сценарий 2» (10 параллельных запросов от ботов с мониторингом `access.log` nginx); убедиться, что в логе нет строк с `/api/public/song-vk-image/`
- [ ] T017 [US1] Manual validation (SC-006, FR-008): выполнить `grep "OG render for song id=11661, User-Agent=YandexBot" /var/log/karaoke-web.log` (или через `docker logs karaoke-web`); убедиться, что формат строки логирования неизменный

**Checkpoint**: User Story 1 полностью функциональна — TTFB < 100 мс, все обязательные мета-теги присутствуют, JSON-LD валиден, формат лога не изменился. Можно деплоить как MVP.

---

## Phase 4: User Story 3 — Endpoint корректно обрабатывает крайние случаи (Priority: P2)

**Goal**: добавить обработку невалидного id (400), песня не найдена (404), SKIP-тег (`noindex, nofollow`), idStatus < 3 (без текста), отсутствие обложки (fallback на KARAOKE_LOGO.png).

**Independent Test**: выполнить 5 curl-тестов из quickstart.md «Сценарий 5»: id=0 → 400, id=999999 → 404, id с SKIP → 200 с `noindex`, id с idStatus<3 → 200 без текста, id без обложки → og:image с логотипом.

### Implementation for User Story 3

- [x] T018 [US3] В `/home/nsa/Karaoke/karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicOgSongController.kt` добавить ветку в `ogSongHtml()` после проверки `id == null || id <= 0`: расширить существующую обработку невалидного id (400) сообщением «id должен быть положительным числом» (FR-006 спеки, текущее поведение сохраняется, улучшается текст ошибки)
- [x] T019 [US3] В том же файле реализовать обработку SKIP-тега в `buildSeoHtmlForBots(song)`: если `song.tags` содержит `SKIP` (проверка через `tags.split(" ").map { it.uppercase() }.contains("SKIP")` — паттерн из `SongPublicDto.kt:152`), вернуть HTML с `<meta name="robots" content="noindex, nofollow">`, видимым `<p class="warning">Контент удалён по требованию правообладателя</p>`, БЕЗ секций `#lyrics`, `#chords`, `#listen`, `#description`, БЕЗ `lyrics` в JSON-LD, БЕЗ обложки в `og:image` (FR-006 спеки; см. AGENTS.md «Тег SKIP»)
- [x] T020 [US3] В том же файле добавить условие `if (song.idStatus >= 3)` для отображения секций `#lyrics` и `#chords` (FR-006 спеки): если статус < 3, эти секции не рендерятся (текст ещё не верифицирован)
- [x] T021 [US3] В том же файле убедиться, что `buildAlbumImageUrl(song)` корректно обрабатывает случай `pictureAlbum == null` — возвращает `https://sm-karaoke.ru/KARAOKE_LOGO.png` (FR-006 спеки; R4 research.md)
- [ ] T022 [US3] Manual validation: выполнить 5 curl-тестов из quickstart.md «Сценарий 5» (5.1 невалидный id → 400, 5.2 не найдена → 404, 5.3 SKIP → 200+noindex, 5.4 idStatus<3 → 200 без текста, 5.5 без обложки → og:image с логотипом); убедиться, что все 5 ожидаемых результатов достигнуты
- [ ] T023 [US3] Manual validation: выполнить `python3 -c "..."` (см. quickstart.md «Сценарий 3») — извлечь JSON-LD из SKIP-песни и убедиться, что в нём нет поля `lyrics` и `@type` либо `MusicRecording`, либо `Song`

**Checkpoint**: User Story 3 полностью функциональна — все крайние случаи обрабатываются согласно FR-006.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Цель**: финальные проверки качества, обновление документации, подготовка PR.

- [x] T024 Запустить `./gradlew ktlintCheck` в `/home/nsa/Karaoke/` и убедиться, что нет новых нарушений (constitution.md Principle VI FR-007)
- [x] T025 Запустить `bash /home/nsa/Karaoke/tools/check-kdoc-coverage.sh` и убедиться, что KDoc coverage 100% для `PublicOgSongController.kt` (constitution.md Principle VI FR-006)
- [x] T026 Обновить `/home/nsa/Karaoke/docs/architecture-notes.md` — добавить запись о PR `180-og-seo-html` в секцию «Phase 003 (PR #180+)» (по образцу Pass 35, см. строки 731–791), описать: что меняется, почему (FR-009 spec), что НЕ меняется (nginx, миграции), метрики (TTFB < 100 мс, 0 обращений к `/song-vk-image/`), lessons learned
- [ ] T027 Manual validation (SC-006, FR-008): проверить, что формат строки логирования остался неизменным (`OG render for song id={id}, User-Agent={userAgent}`) — `grep "OG render for song" /var/log/karaoke-web.log | head -1`  ⚠️ Требует запущенный karaoke-web (см. AGENTS.md «Пересобирать/перезапускать контейнер karaoke-web локально» — на хостах НЕ `dev-pc` делает только пользователь)
- [ ] T028 Manual validation: выполнить quickstart.md «Сценарий 7» (10 параллельных запросов от YandexBot по 10 случайным песням); убедиться, что TTFB < 100 мс для всех запросов  ⚠️ Требует запущенный nginx + karaoke-web
- [x] T029 Manual validation: выполнить quickstart.md «Сценарий 8» (`git diff HEAD -- deploy/web-server-deploy/deploy/80to8897`); убедиться, что nginx-конфиг не изменился  ✅ Выполнено: `git diff HEAD -- deploy/web-server-deploy/deploy/80to8897` — пустой вывод
- [x] T030 Manual validation: выполнить quickstart.md «Сценарий 9» (`git status --porcelain deploy/karaoke-db/` и `git diff HEAD -- deploy/karaoke-db/`); убедиться, что нет новых миграций  ✅ Выполнено: нет новых SQL-файлов
- [ ] T031 Manual validation: выполнить quickstart.md «Сценарий 10» (curl с обычным User-Agent Chrome); убедиться, что запрос идёт в SPA karaoke-public (Vue), а не в OG-endpoint (FR-012 спеки)  ⚠️ Требует запущенный nginx
- [ ] T032 Создать PR: `cd /home/nsa/Karaoke && git add . && git commit -m "feat(og-seo): SEO-HTML endpoint for bots instead of PNG generation"` затем `git push -u origin 180-og-seo-html` и `gh pr create --base master --title "feat: SEO-HTML endpoint for bots (specs/180)" --body "..."` (см. AGENTS.md «CI-gate для master»)  ⚠️ Только по явному запросу пользователя
- [ ] T033 Дождаться CI 7/7 SUCCESS через `gh pr checks --watch` или `gh run watch`; если есть failures — починить и перезапустить  ⚠️ Только по явному запросу пользователя
- [ ] T034 Merge PR: `gh pr merge --merge` БЕЗ `--delete-branch` (см. AGENTS.md «Жизненный цикл feature-ветки (NON-NEGOTIABLE)»)  ⚠️ Только по явному запросу пользователя

**Checkpoint**: все проверки качества пройдены, PR вмержен в master, ветка `180-og-seo-html` остаётся живой.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: нет зависимостей — стартует немедленно.
- **Phase 2 (Foundational)**: зависит от Phase 1 — BLOCKS все user stories.
- **Phase 3 (US1, P1)**: зависит от Phase 2. Это MVP.
- **Phase 4 (US3, P2)**: зависит от Phase 3 (использует `buildSeoHtmlForBots()` и `buildAlbumImageUrl()` из US1).
- **Phase 5 (Polish)**: зависит от Phase 4.

### User Story Dependencies

- **US1 (P1)**: стартует после Foundational (Phase 2). Нет зависимостей от других stories.
- **US2 (P2)**: НЕ выделена в отдельную фазу — реализуется внутри US1 (og:image URL construction — часть US1). Валидация (FR-007 спеки) — это часть Phase 5 «Сценарий 8–10».
- **US3 (P2)**: стартует после US1 — расширяет существующий `buildSeoHtmlForBots()`.
- **US4 (P3)**: НЕ в скоупе этого PR (см. spec assumptions — backlog).

### Within Each User Story

- Helper-функции (Phase 2) → реализация контроллера (Phase 3) → manual validation (Phase 3).
- Реализация `buildSeoHtmlForBots` (T006–T009) — последовательно, в одном файле.
- Manual validation тесты (T013–T017, T022, T028–T031) — параллельно после реализации.

### Parallel Opportunities

- T003, T004, T005 (Phase 2) — параллельно (разные helper-функции в одном файле, но без взаимных зависимостей).
- T013–T017 (Phase 3 validation) — последовательно после T012, но их можно выполнять в одном bash-скрипте.
- T022, T023 (Phase 4 validation) — последовательно после T018–T021.
- T028–T031 (Phase 5 validation) — последовательно друг за другом, но автоматизируются в один bash-скрипт.

---

## Parallel Example: User Story 1

```bash
# Phase 2 — все три helper-функции независимы, можно делать параллельно
# (но они в одном файле — на практике последовательно одной правкой)

# Phase 3 — реализация контроллера последовательно (один файл):
# T006 → T007 → T008 → T009 → T010 → T011 → T012 → T013 → T014 → T015 → T016 → T017

# Phase 3 — validation выполняется последовательно, но автоматизируется:
curl ... > /tmp/og-test.html  # T013
grep ... /tmp/og-test.html    # T014
python3 -c "..."              # T015
tail -f access.log ...        # T016 (отдельно)
grep ... karaoke-web.log      # T017
```

---

## Implementation Strategy

### MVP First (User Story 1 + 3)

1. Complete Phase 1: Setup (T001–T002).
2. Complete Phase 2: Foundational (T003–T005).
3. Complete Phase 3: User Story 1 (T006–T017).
4. **STOP and VALIDATE**: test User Story 1 independently через quickstart.md сценарии 1, 2, 4.
5. Complete Phase 4: User Story 3 (T018–T023).
6. **STOP and VALIDATE**: test User Story 3 через quickstart.md сценарий 5.
7. Complete Phase 5: Polish (T024–T034).
8. Deploy (через пользователя, см. AGENTS.md «Ограничения агента»).

### Incremental Delivery

1. Setup + Foundational → Phase 2 готов.
2. US1 (MVP) → bot индексирует SEO-HTML → можно деплоить как есть (T001–T017).
3. US3 → крайние случаи обрабатываются → деплоить (T018–T023).
4. Polish → CI пройден, PR вмержен (T024–T034).

### Critical Path

**T001 → T002 → T003–T005 → T006 → T007 → T008 → T009 → T012 → T013–T017 → T018–T023 → T024–T034**

T011 (KDoc update) можно делать параллельно с T006–T009.
T010 (удаление старой функции) — после T009.

---

## Notes

- Все задачи привязаны к конкретным файлам с абсолютными путями.
- Manual validation (T012–T017, T022–T023, T028–T031) требует запущенного локального nginx + karaoke-web.
- US2 и US4 намеренно НЕ выделены в отдельные фазы:
  - US2 — это валидация og:image URL, которая происходит в Phase 5 «Сценарий 8–10».
  - US4 — backlog, не входит в этот PR (см. spec assumptions).
- Phase 5 включает шаги, которые **должен** выполнить пользователь (T032 — `git push`, T033 — ожидание CI, T034 — merge). Агент может выполнить их только если пользователь явно запросит (см. AGENTS.md «Не коммитить без явного запроса пользователя»).
- T012 (`bash deploy/do.sh build_start_web`) выполняется ТОЛЬКО на машине `dev-pc` под пользователем `dev` (см. AGENTS.md «Разрешено агенту»). На других машинах пользователь должен запустить вручную.
