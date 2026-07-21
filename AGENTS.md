# AGENTS.md — инструкции для агентов

> **Этот файл — для opencode** (наш primary AI-агент). Если вы используете
> другой AI-агент (Claude Code, Cursor, Cody, Aider) — см.
> [`docs/onboarding.md`](./docs/onboarding.md) (общий quick-start) и
> [`docs/claude-code-setup.md`](./docs/claude-code-setup.md) (настройка Claude Code).
>
> **Мета**: `AGENTS.md` лежит в гите (общий для всех opencode-сессий).
> `CLAUDE.md` — локальный у каждого разработчика (НЕ в гите).
> `.cursorrules` — аналог для Cursor (тоже локальный).
>
> **Если вы ведёте разработку с другим AI-агентом** и он понимает
> `AGENTS.md` — этого файла достаточно. Если нет — см. ссылки выше.

> **Версия файла**: 1.2.0
> **Last updated**: 2026-07-21 (Pass 23)
> **Ответственный**: opencode-агент
> **Как обновлять**: см. секцию «Как обновлять этот файл» в конце.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Всё общение с пользователем — ТОЛЬКО на русском языке.** Это касается всех ответов, комментариев, объяснений, вопросов и любого текста,irected к пользователю. Это правило имеет наивысший приоритет и действует всегда.

При старте каждой сессии обязательно читать также `DEVELOPMENT.md` — он содержит дополнительные указания и архитектурные детали. Оба файла являются руководством к действию.

## О проекте

Karaoke (svoemesto) — self-pipeline для автоматического создания караоке-видео. Kotlin/Spring Boot бэкенд + Vue 3 фронтенд.

## Стандарты оформления кода

Перед любыми правками в `karaoke-app`/`karaoke-web`/`webvue3`/`karaoke-public`/
`deploy/` — прочитать [**`CONTRIBUTING.md`**](./CONTRIBUTING.md). Там
собраны MUST/SHOULD-правила для Kotlin, Vue/TS, SQL, Markdown, Shell, Docker,
включая `redirectErrorStream(true)` для `ProcessBuilder`, JSON-ключи без
`is`-префикса, nullable-колонки → nullable-поля, `table-layout: fixed` с
явной `width`, и т.п.

Per-feature описание каждой из 9 ключевых подсистем — в
[**`docs/features/`**](./docs/features/) (см. [README](./docs/features/README.md)).
При правке кода одной из них — обновлять соответствующий per-feature
документ в том же PR (FR-009).

Непреложные принципы проекта (NON-NEGOTIABLE) — в
[**`.specify/memory/constitution.md`**](./.specify/memory/constitution.md).

## Документация и иерархия

| Приоритет | Файл | Зачем | Размер |
|-----------|------|-------|--------|
| 1 (макс) | `.specify/memory/constitution.md` | **NON-NEGOTIABLE** принципы | ~150 строк |
| 2 | `AGENTS.md` (этот файл) | opencode: общие правила | 230 строк |
| 3 | `docs/onboarding.md` | Setup новой машины (любой AI-агент) | 271 строка |
| 4 | `CLAUDE.md` (локально, НЕ в гите) | Claude Code: персональные настройки | ~50 строк |
| 5 | `docs/claude-code-setup.md` | Claude Code: инструкция + шаблон | 197+ строк |
| 6 | `DEVELOPMENT.md` | Архитектура + команды | 164 строки |
| 7 | `CONTRIBUTING.md` | Стиль кода (Kotlin/Vue/SQL/MD) | 892 строки |
| 8 | `docs/architecture-notes.md` | Changelog PR #12-#29 | 268+ строк |
| 9 | `docs/features/<slug>.md` | Per-feature (11 + 1 документ) | 50-180 строк каждый |

**При расхождении** — приоритет у файла с меньшим номером.
**AI-агент**: читай файлы в этом порядке при старте сессии (1 → 9).

## Где правила для разных AI-агентов

| Агент | Файл конфига | В гите? | Документация |
|-------|--------------|---------|--------------|
| **opencode** (primary) | `AGENTS.md` | ✅ да | этот файл |
| **Claude Code** | `CLAUDE.md` (локально) | ❌ нет | [`docs/claude-code-setup.md`](./docs/claude-code-setup.md) |
| **Cursor** | `.cursorrules` (локально) | ❌ нет | см. onboarding.md |
| **Cody, Aider, Continue** | `.cody/.cody.yml`, `.aider*`, `.continue/*` | ❌ нет | см. onboarding.md |
| **Любой другой** | системный промпт | — | см. onboarding.md |

**Принцип:** общие правила — в гите (`AGENTS.md`, `CONTRIBUTING.md`).
**Персональные настройки** — локально (`CLAUDE.md`, `.cursorrules`).
**Никогда** не коммитьте персональные AI-конфиги в общий репо (см. PR #29).


## Тип песни (song_type)

> **Cross-ref**: детали реализации (sync-триггеры, flow) — в
> [`docs/features/dual-db-sync.md`](./docs/features/dual-db-sync.md) и
> [`docs/features/mlt-generator.md`](./docs/features/mlt-generator.md).

Песня бывает трёх типов: обычная (вокал + музыка), инструментал (только музыка без вокала), стихи (только вокал без музыки).

**Применение признака:**
- Поле в `tbl_settings.song_type VARCHAR(20) NOT NULL DEFAULT 'song'`.
- Миграция: `deploy/karaoke-db/24_song_type.sql` (добавляет колонку + пересоздаёт recordhash-триггер в `tbl_settings` и `tbl_settings_sync` — иначе LOCAL и PROD будут давать разные md5 для одной песни и sync сломается).
- Backend: enum `SongType` (`song`/`instrumental`/`poetry`, см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SongType.kt`).
- Frontend: select в `webvue3/src/components/Songs/edit/SongEdit.vue` (после «Тэги»), значение по умолчанию `song`.
- API: параметр `songType` в `POST /api/song/update`. Невалидное значение — тихо подменяется на `SONG`.
- Поле передаётся в `SettingsDTO.songType` / `SettingsDTOdigest.songType` (lowercase-строка).
- Применяется в `Settings.loadListFromDb` (читается как nullable string, дефолт `SONG` если пусто) и в `Settings.songType` getter/setter.
- Поддерживается в `Settings.getDiff` и `Settings.getSqlToInsert` — изменение songType сохраняется через обычный flow автосохранения.

## Рендер MP4 из онлайн-плеера

> **Cross-ref**: детали — в [`docs/features/mp4-render.md`](./docs/features/mp4-render.md).

Очередь `RENDER_MP4_LYRICS` / `RENDER_MP4_KARAOKE` / `RENDER_MP4_DEMO` (threadId=0, HEAVY_RENDER lane) — рендер караоке-видео через Playwright + ffmpeg без MLT.

**Поток:**
1. `PlayerMp4RenderService.renderFrames()` — headless Chromium рисует кадры, `canvas.toDataURL('image/jpeg', 0.95)` → JPEG-секвенция
2. `PlayerMp4MuxService.mixAndMux()` — ffmpeg собирает JPEG + FLAC-стемы в MP4, прогресс через `-progress pipe:1` (парсинг `out_time_ms`)
3. Результат копируется в `done_files/$fileName [render].mp4`, temp-папка удаляется

**Три версии рендера (`RenderVersion` enum):**
- **LYRICS** — acc(1.0)+voc(1.0), 1920×1080@60fps
- **KARAOKE** — acc(1.0)+voc(0.0), 1920×1080@60fps
- **DEMO** — acc(1.0)+voc(0.0), **1280×720@30fps** (дефолт), фрагмент с fade-in/out, watermark "ДЕМО", end screen 10 сек

**Ключевые файлы:**
- `PlayerMp4RenderService.kt` — рендер кадров (Playwright, headless Chromium), `RenderVersion`, `RenderMp4Params`
- `PlayerMp4MuxService.kt` — ffmpeg mux с прогрессом
- `Utils.kt:executeRenderMp4()` — диспетчеризация: рендер → mux → done_files → cleanup
- `ApiController.kt` — `/song/renderMp4Preview` (очередь), дефолты DEMO (1280/720/30)
- `KaraokeProcess.kt` — создание задачи, дефолты DEMO (1280/720/30)
- `Settings.kt` — `demoFragmentStartSeconds`, `demoFragmentEndSeconds`, `demoFragmentFadeInSeconds`

**Оптимизации:**
- JPEG (quality 95) вместо PNG — x3 скорость рендера (18 fps вместо 6 fps)
- ffmpeg `-progress pipe:1` + `out_time_ms` — реальный прогресс mux-фазы (80-100%)
- `sendCountWaitingMessage` при пустой очереди — бейдж сбрасывается в 0

**Ловушка ffmpeg:** `ProcessBuilder.redirectErrorStream(false)` блокирует процесс — буфер stderr переполняется. Всегда использовать `redirectErrorStream(true)`.

**Ловушка дефолтов DEMO:** дефолты 1280/720/30 задаются И в `ApiController.kt` (context map), И в `KaraokeProcess.kt` (fallback). Контроллер подставляет дефолты раньше, чем они попадают в context — если задать только в KaraokeProcess, не сработает.

## Счётчики главной страницы (StatBySong)

> **Cross-ref**: SQL-формулы, Spring `@Cacheable`-нюанс, GDPR retention — в
> [`docs/features/stats.md`](./docs/features/stats.md).

Карточки статистики в `HomeView.vue` (karaoke-public) и `main.html` (legacy Thymeleaf) показывают 4 числа + одно внутреннее:

| Лейбл | Формула (SQL в `Stat.kt`, без учёта SKIP) |
|---|---|
| **Песен в коллекции** | `count(*) WHERE id_status>=3 AND btrim(source_markers)!=''` |
| **В открытом доступе** | подмножество «коллекции» с истёкшим `publish_date`/`publish_time` |
| **По подписке** | «коллекция» − «в открытом доступе» (вычитание на бэкенде) |
| **В работе** | «всего в БД» − «в коллекции» |
| **Всего в БД** (внутр., не показывается) | `count(*)` без SKIP |

**SKIP-фильтр** (одинаков во всех формулах):
`(tags IS NULL OR NOT ('SKIP' = ANY(string_to_array(upper(coalesce(tags,'')), ' '))))` —
исключает реальное слово-маркер `SKIP` (через массив-тег), не подстроку.

**Формула «Песен в коллекции» — SQL-аппроксимация готовности премиум-плеера.** Точная проверка
живёт в `PublicPlayerController.stemsReady()` и делает 2 HEAD-запроса в MinIO на песню;
счётчик главной на 18k+ записей не может позволить такую нагрузку — используется **последний
из трёх шагов** готовности (наличие `source_markers` после `id_status>=3`).

**Кеш в `AtomicInteger`** (`cachedTotal/Collection/OnAir/Exclusive/InWork`); live-сайт получает
мгновенный JSON без обращения к БД. Обновление — `StatsCacheScheduler`
(`@PostConstruct warmUp()` для cold-start + `@Scheduled cron "0 0 * * * *"` каждый час).
Spring `@Cacheable` намеренно НЕ подключён (нет `@EnableCaching`) — проще держать инвариант
«endpoint отвечает без обращения к БД» явно через `AtomicInteger + Scheduled`, чем добавлять
стартер ради одного счётчика.

**Потребители:**
- `PublicApiController.kt` (`@GetMapping("/stats")`) → JSON для Vuex-модуля `stats`
  (`karaoke-public/src/store/modules/stats.js`).
- `MainController.kt:main()` → атрибуты `onSponsr/onAir/exclusive/inWork/total` для Thymeleaf
  `main.html` (legacy-шаблон, дублирует карточки для старого сайта).

## Модули

- `karaoke-app` — ядро: Spring Boot (Kotlin), все доменные модели, MLT-генератор, очередь задач, LLM-поиск текстов
- `karaoke-web` — публичный API и веб-страницы (depends on karaoke-app)
- `karaoke-db` — legacy, не используется в продакшене
- `karaoke-vue` — legacy, заброшен (только `src/assets`, нет реального кода). Не участвует в сборке.
- `karaoke-public` — публичный SPA (Vue 3 + Vite + Bootstrap 5)
- `webvue3` — admin SPA (Vue 3 + Vite + Bootstrap-vue-next + Vuex)
- `deploy/` — docker-compose, `do.sh`, серверные конфиги

## Сборка и запуск

```bash
# Backend (Kotlin, JDK 17)
./gradlew karaoke-app:bootJar
./gradlew karaoke-web:bootJar
./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel

# Frontend (webvue3)
cd webvue3 && npm install && npm run dev    # dev
cd webvue3 && npm run build                # production

# Frontend (karaoke-public)
cd karaoke-public && npm run dev
cd deploy && bash do.sh build_start_public  # Docker build + restart

# Deploy (всегда запускать из deploy/)
cd deploy
bash do.sh build              # gradle + docker images
bash do.sh build_app          # только karaoke-app
bash do.sh build_start_web    # rebuild karaoke-web + restart
bash do.sh start / stop       # контейнеры
bash do.sh push / pull        # Docker Hub
```

Root `pom.xml` — leftover от Maven, не использовать. Проект собирается через Gradle.

## Тесты

Тестов в CI нет. Существующие тесты (`karaoke-app/src/test`) — интеграционные, требуют сеть/браузер/credentials, большинство `@Disabled`. Не полагайся на них как на проверку.

## Архитектура (ключевые особенности)

- **БД без JPA/Hibernate.** Сырой JDBC + рефлексия для diff. `KaraokeDbTable` — интерфейс всех сущностей. Изменения публикуются через SSE.
- **Две БД (LOCAL ↔ SERVER).** Синхронизация через хэши записей. О(n) через `associateBy`, не O(n²).
- **MLT-генерация.** `mlt/mko/*` — объекты для каждого визуального слоя караоке. ~150 настраиваемых параметров в `KaraokeProperties.kt`.
- **Async-очередь.** `KaraokeProcess*` — задачи (ffmpeg, Demucs, Sheetsage) запускаются как subprocess, прогресс парсится из stdout.
- **Storage.** MinIO (MinIO-compatible). Картинки: альбом 400×400, автор 1000×400. Превью генерируются on-demand.

**Синхронизация LOCAL↔SERVER — критичные паттерны производительности:**
- Сравнение хэшей: `associateBy { it.id }` + Map lookup — O(n), **не** вложенные `.any`/`.none` — O(n²) (при 18858 записях = 3+ минуты).
- Загрузка записей для diff: пакетно через `WHERE id IN (...)`, **не** по одной в цикле (N+1 запросов).

## Важные паттерны

- **`karaoke-public` dual design:** два дизайна (classic/modern), выбор в `localStorage`. Компоненты: `views/classic/` и `views/modern/`. CSS-переменные `--km-*`.
- **Таблицы `karaoke-public`:** `table-layout: fixed` требует явной `width: Npx`. Колонки платформ: 16×22px = 352px. `display: flex` на `<td>` ломает высоту строки — использовать `text-align: center; vertical-align: middle`.
- **Bootstrap 5:** `<select>` → класс `form-select` (не `form-control`).
- **Картинки в БД:** поле `picture_full` всегда `""`. Картинки только в MinIO. `PicturesDTO` содержит `previewUrl`/`fullUrl`. При загрузке всегда использовать `ignoreUseInList = false`.
- **Тег SKIP:** если в `tags` есть `SKIP`, показывается заглушка "удалено по требованию правообладателя".
- **Табулатура (ASCII-only):** `-` вместо `⎼` (U+23BC), `||` вместо `‖` (U+2016). Unicode ломает выравнивание через font fallback.
- **HealthReport:** видеофайлы проверяются **только при `idStatus >= 6`**. Не трогать логику для видео при статусе < 6.

**Персистентность страницы пагинации в `webvue3` (паттерн «вернуться на ту же страницу»):**
- `currentPage` живёт в `data()` компонента; при уходе с роута Vue выгружает компонент и значение сбрасывается. Чтобы при уходе «Песни → Публикации → Песни» открывалась та же страница — храним номер в Vuex.
- В каждом сторе (модуле с таблицей) добавляются три вещи по образцу `components/Songs/store.js`:
  - `state.<module>TableCurrentPage: 1`
  - `getters.get<Module>TableCurrentPage(state) { return state.<module>TableCurrentPage; }`
  - `mutations.set<Module>TableCurrentPage(state, page) { state.<module>TableCurrentPage = page; }`
- В компоненте таблицы:
  - `data()` инициализирует поле из getter: `currentPage: this.$store.getters.get<X>TableCurrentPage || 1`
  - watcher `currentPage` коммитит изменения в store. В `SongsTable.vue` также ослаблен watcher `countRows` — был безусловный сброс `currentPage = 1`, теперь сбрасывает только если страница вышла за пределы `Math.ceil(newCount / perPage)`. Делать так же в новых таблицах только если там есть watcher `countRows` с аналогичным безусловным сбросом.
- Применено в `SongsTable` (с расширенной логикой countRows), `ProcessesTable`, `AuthorsTable`, `PicturesTable`, `SiteUsersTable`, `SitePlaylistsTable`, `PropertiesTable`, `DictionariesTable`. В `StatsView` — две отдельные пагинации `statsBySongPage`/`webEventsPage` в сторе `stats`, восстановление через watcher на data-поля.
- Применять тот же шаблон к любой новой таблице в `webvue3` сразу, иначе пользователь потеряет позицию при переключении пунктов меню.

## Dockerfile-ловушки

- **`nginx:alpine` нельзя** — docker-compose использует `/bin/bash -c`, в alpine нет bash → контейнер падёт. Использовать `nginx:stable` (Debian).
- **`node:latest` нельзя** — недетерминированный. Использовать `node:22-alpine` (LTS).
- **karaoke-app: `eclipse-temurin:22-jre-jammy`** (JRE, не JDK — Spring Boot fat jar не требует компилятора).
- **Docker CE внутри karaoke-app** намеренно — приложение запускает `docker run`/`docker compose` через `ProcessBuilder`.
- **IP-сервисы:** `ip-api.com`, `ipapi.co`, `ipapi.is` из Docker возвращают 403/502. Использовать `api.country.is` для проверки VPN.

## Ограничения агента

### Запрещено

- Пересобирать/перезапускать контейнер `karaoke-app` локально — делает только пользователь
- Деплоить на сервер (`deploy_web.sh`, `deploy_public.sh`, rsync на 79.174.95.69) — делает только пользователь
- Редактировать файлы на сервере напрямую
- Перезаписывать `do.env` на сервере (содержит секреты)
- Коммитить `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`

### Разрешено

- Редактировать код во всех модулях
- Собирать gradle-джары (`./gradlew karaoke-app:bootJar`, `./gradlew karaoke-web:bootJar`)
- Запускать `npm run dev` / `npm run build` для `webvue3` и `karaoke-public`
- Пересобирать/перезапускать контейнеры `karaoke-web`, `webvue3`, `karaoke-public` локально через `deploy/do.sh`

## Git

Не коммитить: `deploy/ollama_data/`, `dist/`, `node_modules/`, `deploy/.env`, `deploy/do.env`. Всегда проверять `git status` перед `git add`.

## Деплой

- `deploy/deploy_web.sh` — обновление karaoke-web на проде
- `deploy/deploy_public.sh` — обновление karaoke-public на проде
- Сервер: `79.174.95.69`, Docker-сеть `deploy_karaokenet`
- Не редактировать файлы на сервере напрямую — синхронизировать через rsync
- `do.env` на сервере содержит секреты — не перезаписывать через rsync

**Проверка после деплоя `deploy_web.sh`:**
1. В логах **нет** `EOF` / `400 Bad request` при push — иначе пуш не удался.
2. На сервере: `Status: Downloaded newer image` (не `Image is up to date`).
3. Если push через VPN падает — попросить пользователя запустить вручную без VPN.

**Nginx 80to8897:** это отдельный файл (не симлинк). При rsync обновляется в `/root/Karaoke/deploy/`, но nginx читает из `/etc/nginx/sites-enabled/`. Нужно копировать вручную:
```bash
ssh root@79.174.95.69 "cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx"
```

---

## Q&A (Pass 23)

### Q: KDoc с backticks ломает парсер — что делать?

Заменить `` `multitrack` `` на «multitrack», `` `MltProp` `` на «MltProp» и т.п.
Парсер ktlint видит `*` после `/` как начало нового комментария — `Unclosed comment` ошибка.
Допустимые backticks: одиночные слова вне KDoc, или внутри markdown-ссылок.

### Q: `redirectErrorStream(false)` блокирует процесс — почему?

Буфер stderr (~64 KB по умолчанию) переполняется, если ffmpeg/ffprobe долго
пишет в stderr (warnings). Подпроцесс блокируется на write(stderr). Решение:
всегда `redirectErrorStream(true)` для `ProcessBuilder` (см. CONTRIBUTING.md).

### Q: `git blame` показывает чужих авторов после prettier/baseline коммитов?

Настроить:
```bash
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
См. Principle VII.2 в `.specify/memory/constitution.md` (v1.2.0+).

### Q: Дефолты DEMO рендера (1280/720/30) не применяются?

Задаются в **двух местах**: `ApiController.kt` (context map) **И** `KaraokeProcess.kt`
(fallback). Если задать только в одном — контроллер подставляет дефолты
раньше, чем они попадают в context, и fallback не срабатывает.

### Q: `git pull` конфликтует по `CLAUDE.md`?

`CLAUDE.md` — локальный файл, НЕ в гите. Должен быть в `.git/info/exclude`:
```bash
echo "CLAUDE.md" >> .git/info/exclude
```
См. PR #29 (hotfix) и Principle VII.1 в constitution.md.

### Q: Как добавить per-feature документ для новой подсистемы?

1. Создать `docs/features/<slug>.md` со структурой (см. `check-feature-doc.sh`):
   - `## Что делает`, `## Зачем`, `## Как работает`,
   - `## Инварианты / правила`, `## Известные ловушки`, `## Ссылки`.
2. Добавить запись в таблицу `docs/features/README.md` (11 → 12 фич).
3. В KDoc корневого класса добавить `@see docs/features/<slug>.md`.
4. Проверить: `bash tools/check-feature-doc.sh docs/features/*.md`.

### Q: Как добавить новый AI-агент в таблицу `## Где правила для разных AI-агентов`?

Добавить строку с 4 колонками: агент / файл конфига / в гите? / документация.
Пример: см. существующие 5 строк (opencode / Claude Code / Cursor / Cody-Aider-Continue / любой).
См. также [`docs/onboarding.md`](./docs/onboarding.md) секция «Настроить AI-агента».

### Q: Где найти датированный changelog последних PR?

[`docs/architecture-notes.md`](./docs/architecture-notes.md) — Pass 1-14 (21 PR).
Phase 001 (PR #12-#26) + Phase 002 (PR #27-#32). Содержит метрики, принципы,
уроки, ловушки. Обновляется в Pass 14, 20, 24, ...

### Q: Какой приоритет у документов при расхождении?

1. `.specify/memory/constitution.md` (макс) — NON-NEGOTIABLE.
2. `AGENTS.md` (этот файл) — opencode правила.
3. `docs/onboarding.md` — setup новой машины.
4. `CLAUDE.md` (локально) — Claude Code персональные.
5. `docs/claude-code-setup.md` — Claude Code шаблон.
6. `DEVELOPMENT.md` — архитектура.
7. `CONTRIBUTING.md` — стиль кода.
8. `docs/architecture-notes.md` — changelog.
9. `docs/features/<slug>.md` — per-feature.

См. секцию «Документация и иерархия» выше.

### Q: Почему `nginx:alpine` падает?

`docker-compose` использует `/bin/bash -c ...`, а в `nginx:alpine` нет bash
(только `ash` через BusyBox). Контейнер запускается, но скрипт не работает.
Решение: `nginx:stable` (Debian). Аналогично `node:latest` → `node:22-alpine`
(LTS, детерминирован).

### Q: `WORKING_DATABASE` и `KSS_APP` — это глобалы. Почему?

Исторически проект развивался с глобалами для удобства (Kotlin `lateinit var`
+ `object`-singleton). Минус: усложняет тестирование (нужен отдельный setup
для каждого теста). TODO: рефакторинг в DI (Pass 15+).

### Q: Чем отличается `karaoke-app` от `karaoke-web`?

- `karaoke-app` — ядро (Spring Boot, модели, MLT, очередь, LLM). Разворачивается
  ТОЛЬКО на admin-машине (где Demucs/ffmpeg и доступ к /sm-karaoke/system/).
- `karaoke-web` — публичный API + Thymeleaf (тонкий слой над karaoke-app).
  Разворачивается на проде (Docker).
- На проде **нет** `karaoke-app` — только его бэкенд-API через `karaoke-web`.

### Q: Как проверить, что CI пройдёт (локально)?

```bash
./gradlew ktlintCheck                    # Kotlin lint
cd webvue3 && npm run lint:check && cd ..
cd karaoke-public && npm run lint:check && cd ..
bash tools/check-kdoc-coverage.sh        # должно быть 100%
bash tools/check-jsdoc-coverage.sh webvue3
bash tools/check-jsdoc-coverage.sh karaoke-public
pre-commit run --all-files               # 7 проверок
./gradlew :karaoke-app:compileKotlin
```
Все должны быть зелёными (или baseline = 0).

---

## Как обновлять этот файл

**Когда обновлять:**
- При добавлении/удалении AI-агента в проекте (новая колонка в таблице).
- При изменении иерархии документации (новые первоисточники).
- При добавлении нового типа ловушки/паттерна (Dockerfile, KDoc, git).
- При изменении конституции (Principle X → Y).

**Как обновлять:**
1. Прочитать текущий `AGENTS.md` и связанные документы (constitution, onboarding).
2. Внести правки в feature-ветке `0XX-agents-md-update`.
3. **Обновить версию файла** в шапке (semver):
   - **MAJOR** — удаление секции, изменение governance.
   - **MINOR** — добавление новой секции (Q&A, кросс-референс).
   - **PATCH** — typo-фикс, обновление ссылки, мелкое уточнение.
4. **Обновить `Last updated`** дату.
5. **Обновить `docs/architecture-notes.md`** (Pass 14+) — запись о PR.
6. Проверить CI: ktlint, ESLint, KDoc/JSDoc coverage, docs structure.
7. Создать PR, дождаться CI 7/7 PASS, merge.

**Чего НЕ делать:**
- Не дублировать содержимое `constitution.md` / `CONTRIBUTING.md` —
  давать только ссылки + краткую выжимку.
- Не добавлять код (только инструкции для агента).
- Не коммитить личные данные (имя, email, пути).

**Связанные документы:**
- [`.specify/memory/constitution.md`](./.specify/memory/constitution.md) — NON-NEGOTIABLE
- [`docs/onboarding.md`](./docs/onboarding.md) — setup новой машины
- [`docs/claude-code-setup.md`](./docs/claude-code-setup.md) — настройка Claude Code
- [`docs/architecture-notes.md`](./docs/architecture-notes.md) — changelog
- [`DEVELOPMENT.md`](./DEVELOPMENT.md) — архитектура
- [`CONTRIBUTING.md`](./CONTRIBUTING.md) — стиль кода
