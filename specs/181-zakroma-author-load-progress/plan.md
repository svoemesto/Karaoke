# Implementation Plan: 181-zakroma-author-load-progress — real-time прогресс через NDJSON-стрим

**Branch**: `181-zakroma-author-load-progress` | **Date**: 2026-08-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/181-zakroma-author-load-progress/spec.md`

## Summary

Заменить синхронную загрузку списка песен автора (`GET /api/public/zakroma`)
на chunked NDJSON-стрим (`GET /api/public/zakroma/stream`), чтобы посетитель
видел **реальный** прогресс загрузки (получено X из N песен автора), а не
синтетический спиннер или фейковые проценты. Бэкенд (`karaoke-web`) стримит
через `StreamingResponseBody` + flush после каждого сообщения; фронт
(`karaoke-public`) читает `ReadableStream` + парсит NDJSON. Чистый side effect:
очистка списка предыдущего автора при клике становится естественной (фронт
сразу стирает `state.zakroma` до fetch, посетитель не видит «хвостов»).

Существующий `GET /api/public/zakroma` остаётся без изменений (FR-BE-007) —
обратная совместимость для статистики, telegram-ботов и других потребителей
API.

## Technical Context

**Language/Version**:
- Backend: Kotlin (JDK 17, Spring Boot 3.x, gradle multi-module).
- Frontend: JavaScript ES2020+ (Vue 3, Vite, Bootstrap 5).

**Primary Dependencies**:
- Backend: Spring `StreamingResponseBody`, `ResponseEntity<StreamingResponseBody>`,
  Jackson для NDJSON-сериализации. Без новых сторонних зависимостей —
  всё в Spring `spring-web` уже.
- Frontend: `fetch().body.getReader()` (native Web Streams API),
  `TextDecoder('utf-8')`. Без новых зависимостей (`ReadableStream`
  поддерживается всеми evergreen-браузерами + mobile Safari 10+).
- Nginx: директивы `proxy_buffering off; gzip off; proxy_cache off;
  proxy_read_timeout 300s;` — никакого нового ПО.

**Storage**: N/A (только чтение из существующей PostgreSQL через
`Song.loadAuthorSongCounts` и `Zakroma.getZakroma`, никаких изменений
схемы БД).

**Testing**:
- Backend: ручное `curl -N https://localhost/api/public/zakroma/stream?author=Test`
  с визуальной проверкой NDJSON-чанков в DevTools Network/Console.
- Frontend: ручной сценарий в браузере (Chrome DevTools Network →
  `Transfer-Encoding: chunked`, счётчик `receivedCount` растёт в UI).
- Тесты в CI отсутствуют (см. `constitution.md` § «Тесты» + `AGENTS.md`).
- Регрессия: убедиться что `GET /api/public/zakroma` (старый endpoint)
  продолжает работать (FR-BE-007 + SC-005).

**Target Platform**:
- Сервер: Linux (Debian/Ubuntu), JDK 17, nginx 1.x, Docker.
- Клиент: любой современный браузер с поддержкой `ReadableStream`.

**Project Type**: Web service (Spring Boot backend + Vue 3 SPA).

**Performance Goals**:
- TTFB первого NDJSON-сообщения (`meta`) ≤ **500 мс** на стандартном
  интернете ≥ 5 Мбит/с (SC-002).
- Полный стрим автора с 500 песен ≤ **10 с** (эвристика).
- Задержка рендера в UI после каждого `song`-сообщения ≤ **100 мс**
  (SC-002 acceptance).
- Lighthouse LCP для `/zakroma` НЕ деградирует более чем на 50 мс
  (на уровне предыдущей версии — стрим не увеличивает payload,
  только меняет «форму» доставки).

**Constraints**:
- Nginx на проде: `proxy_buffering off` + `gzip off` для пути — **критично**
  для chunked-доставки, без правки `80to8897` фича не работает в продакшене.
- `proxy_read_timeout 300s` — для очень больших авторов (медленный диск).
- Существующий endpoint `GET /api/public/zakroma` MUST остаться без изменений
  (FR-BE-007, SC-005) — другие потребители не должны сломаться.
- На localhost dev (vite proxy) nginx-конфиг не нужен — vite отдаёт
  chunked as-is.

**Scale/Scope**:
- 11 097 песен на проде (см. AGENTS.md, Q&A «Что с 11 097 уже эфирных песен»).
- Use case: load одного автора (до ~1000 песен, до ~50 альбомов).
- Single author + chunked stream. **Не** для batch-загрузки списка всех
  авторов (для этого есть `GET /api/public/authors-tiles`).

## Constitution Check

*Gate: must pass before implementation starts. Re-checked after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ N/A | Не затрагиваем медиа-pipeline. Только UI-сторона выбора автора. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Используем существующие `Song.loadAuthorSongCounts` и `Zakroma.getZakroma` — там уже всё по принципу. Ничего нового по БД. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ N/A | Не добавляем новые sync-сущности. |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Не используется. Streaming происходит на уровне HTTP, не в `KaraokeProcess`. |
| **V. Двух-фронтенд: админка и публичный сайт — разные приложения** | ✅ Pass | Затрагиваем только `karaoke-public` (публичный SPA). `webvue3` (админку) не трогаем. `SearchView`, `loadSpecialBucket` — out of scope. |
| **VI. Code Standards (NON-NEGOTIABLE)** | ⚠️ Plan | См. ниже **VI.1 / VI.2** — обязательно создать per-feature документ + пройти ktlint/ESLint/pre-commit. |
| **VII. Cross-Machine Setup** | ✅ N/A | |
| **VIII. Секреты и git-гигиена** | ✅ N/A | Нет секретов. `nginx` конфиг в `deploy/80to8897` уже в `.gitignore`-подобном списке (см. AGENTS.md). |
| **CI-gate для master** (AGENTS.md) | ✅ Plan | Все коммиты пойдут через feature-ветку → PR → CI 7/7 → merge. Ветка `181-zakroma-author-load-progress` уже создана автоматически через `tools/specify-bootstrap.sh` (commit #1). |
| **`git push -u origin` + `gh pr create`** | ✅ Plan | PR будет создан после всех коммитов (#2-#5). |

### VI.1 — Per-feature документ (FR-009, NON-NEGOTIABLE)

**Требование:** при правке кода одной из 9 ключевых подсистем в этом же PR
обновляется per-feature документ (`docs/features/<slug>.md`). Наша фича —
новая подсистема (real-time прогресс через NDJSON-стрим), в существующем
`docs/features/README.md` её пока нет.

**Действие:** создать `docs/features/zakroma-stream-progress.md` со
структурой (см. `tools/check-feature-doc.sh`):
- `## Что делает` — бэкенд стримит NDJSON, фронт рендерит real-time прогресс.
- `## Зачем` — посетитель видит «получено 87 из 234», а не спиннер.
- `## Как работает` — wire protocol `meta/album/song/done`, AbortController,
  nginx buffering/gzip off.
- `## Инварианты / правила` — обратная совместимость старого endpoint,
  nginx-конфиг обязателен на проде.
- `## Известные ловушки` — gzip разрывает NDJSON, nginx буферизует по
  умолчанию, AbortController не закрыт = утечка.
- `## Ссылки` — `spec.md`, `plan.md` (этот), nginx-конфиг.

Также обновить `docs/features/README.md` — добавить строку в таблицу
(11 → 12 фич). KDoc корневого метода
`PublicApiController.zakromaStream(...)` MUST содержать `@see
docs/features/zakroma-stream-progress.md` (FR-006).

### VI.2 — Линтеры и pre-commit (FR-007, NON-NEGOTIABLE)

**Перед PR MUST быть зелёными:**
- `./gradlew ktlintCheck` (Kotlin, karaoke-web + karaoke-app)
- `cd webvue3 && npm run lint:check` (если меняли — не меняем для 181)
- `cd karaoke-public && npm run lint:check` (karaoke-public)
- `bash tools/check-kdoc-coverage.sh` (должно быть 100%)
- `bash tools/check-jsdoc-coverage.sh karaoke-public` (для затронутых файлов)
- `pre-commit run --all-files` (7 проверок)

Если в karaoke-public появятся новые функции/composables — нужны JSDoc
комментарии. Если в karaoke-web — KDoc.

## Project Structure

### Documentation (this feature)

```text
specs/181-zakroma-author-load-progress/
├── plan.md              # ← этот файл (/speckit.plan output)
├── spec.md              # уже создан (/speckit.specify output)
├── quickstart.md        # ✅ создан (Phase 1 — manual test scenarios)
├── checklists/
│   └── requirements.md  # уже создан
├── research.md          # не нужен (нет исследовательских вопросов, всё в плане)
├── data-model.md        # не нужен (нет новых сущностей в БД)
├── contracts/           # не нужен (NDJSON-формат уже в spec.md + plan.md)
└── tasks.md             # Phase 2 — задачи (/speckit.tasks output)
```

### Source Code (repository root)

**Backend (karaoke-web):**
```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── PublicApiController.kt    # MODIFY: добавить endpoint zakromaStream
├── dto/
│   ├── ZakromaPublicDto.kt        # UNCHANGED (используется существующим endpoint)
│   ├── ZakromaAlbumMetaPublicDto.kt   # NEW (без albumSettings, для stream)
│   └── ZakromaStreamMessageDto.kt # NEW (NDJSON wrapper: meta/album/song/done/error)
```

**Frontend (karaoke-public):**
```text
karaoke-public/src/
├── composables/
│   └── useZakromaStreamProgress.js  # NEW (ReadStream парсер + AbortController)
├── store/modules/
│   └── zakroma.js                   # MODIFY: заменить loadZakroma на loadZakromaStream
└── views/
    └── ZakromaView.vue              # MODIFY: использовать composable, заменить "Загрузка..." на progress UI
```

**Infrastructure:**
```text
deploy/
└── 80to8897                          # MODIFY: location /api/public/zakroma/stream с proxy_buffering off
tools/
└── deploy-nginx-stream.sh            # NEW: ручное копирование 80to8897 в /etc/nginx/sites-enabled/ + reload
```

**Documentation:**
```text
docs/
├── features/
│   ├── README.md                      # MODIFY: добавить строку в таблицу (12 фич)
│   └── zakroma-stream-progress.md     # NEW
└── architecture-notes.md              # MODIFY: Pass 51 запись
```

**Structure Decision**: выбран стандартный layout проекта (Option 2 —
web application). Никаких новых проектов/модулей. Существующие `karaoke-app`,
`karaoke-web`, `karaoke-public` остаются как есть; добавляются только
**файлы** (1 DTO meta, 1 DTO wrapper, 1 composable, 1 deploy script,
1 feature doc) и точечные правки в 3 существующих файлах
(`PublicApiController.kt`, `zakroma.js`, `ZakromaView.vue`,
`deploy/80to8897`).

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

**Нет нарушений.** Все gates проходят без `Complexity Tracking` entries:

- Не добавляем новых модулей.
- Не добавляем новых сторонних зависимостей (NDJSON через нативный
  `StreamingResponseBody` + Web Streams API).
- Не добавляем новых сущностей в БД.
- Не ломаем обратной совместимости API (старый endpoint остаётся).
- Не ломаем обратной совместимости nginx (добавляем новый `location`,
  не меняем существующие).

Если в процессе реализации обнаружатся нарушения (например, окажется,
что `Zakroma.getZakroma` нужно менять — что **не планируется**), они
должны быть явно обоснованы здесь с предложением более простой
альтернативы.

## Implementation Strategy

**5 коммитов в ветке `181-zakroma-author-load-progress`** (см. конец
spec.md для деталей), плюс обязательный per-feature документ:

0. **#1** (✅ сделан): инфраструктура — `.specify/extensions.yml` +
   `tools/specify-bootstrap.sh` + AGENTS.md секция «Создание спецификации».

1. **#2** (✅ сделан): спека `specs/181-zakroma-author-load-progress/`
   (`spec.md` + `checklists/requirements.md`).

2. **#3 — backend NDJSON endpoint:**
   - `dto/ZakromaAlbumMetaPublicDto.kt` (NEW) — без `albumSettings`.
   - `dto/ZakromaStreamMessageDto.kt` (NEW) — NDJSON-wrapper с
     `@JsonInclude(NON_NULL)`.
   - `controllers/PublicApiController.kt` (MODIFY) — добавить
     `zakromaStream(...)` с `StreamingResponseBody`, flush после каждого
     NDJSON-сообщения, обработка ошибок SQL с `{"type":"error",...}`.

3. **#4 — nginx config + deploy script:**
   - `deploy/80to8897` (MODIFY) — location для `/api/public/zakroma/stream`
     с `proxy_buffering off; gzip off; proxy_cache off; proxy_read_timeout 300s`.
   - `tools/deploy-nginx-stream.sh` (NEW) — ручное копирование на прод +
     `nginx -t` + `systemctl reload nginx`.

4. **#5 — per-feature документ + frontend:**
   - `docs/features/zakroma-stream-progress.md` (NEW) — 6 обязательных разделов.
   - `docs/features/README.md` (MODIFY) — добавить строку в таблицу.
   - `composables/useZakromaStreamProgress.js` (NEW) — ReadStream парсер +
     AbortController.
   - `store/modules/zakroma.js` (MODIFY) — заменить `loadZakroma` →
     `loadZakromaStream`.
   - `views/ZakromaView.vue` (MODIFY) — использовать composable,
     заменить `<div class="km-loading">` на progress UI.

5. **#6 — cleanup + проверки:**
   - Удалить старый код синхронной загрузки (`useZakromaLoadProgress` если
     создавался, текст «Загрузка...», debounce).
   - `./gradlew ktlintCheck` + ESLint + JSDoc/KDoc coverage.
   - Ручная проверка quickstart сценариев.
   - `git push` + `gh pr create --base master`.

## Risks & Open Questions

| Риск | Митигация |
|---|---|
| Nginx на проде уже имеет другие `location`-блоки для `/api/public/*` — наша правка может сломать наследование `proxy_*` директив. | При правке `80to8897` — явно скопировать `proxy_set_header Host ...; proxy_set_header X-Real-IP ...; proxy_set_header X-Forwarded-For ...; proxy_set_header X-Forwarded-Proto ...;` из существующих upstream-блоков в новый location (или вынести их на уровень `server { }`). На dev-машине (если есть) сначала проверить, что `/api/public/songs` и `/api/public/stats` продолжают работать. |
| Gzip ломает NDJSON: chunked response состоит из chunk'ов, и gzip может объединить несколько сообщений в один chunk без разделителя. | Уже зафиксировано: `gzip off;` для location. Проверить ещё, что upstream не шлёт `Content-Encoding: gzip` сам (Spring этого не делает по умолчанию — но `Content-Length: chunked` (если есть) тоже проверять). |
| `proxy_read_timeout 300s` достаточно? | Проверить на prod логе самого большого автора (Аквариум?) сколько занимает текущая загрузка. Если > 5 мин — увеличить. Реалистично: обычный автор — секунды, очень большой — десятки секунд. 300s с запасом. |
| `AbortController` cleanup в `onBeforeUnmount` работает в Vue 3 setup()? | Да, `onBeforeUnmount` (alias `onUnmount`) — стандартный Vue 3 Composition API lifecycle hook. Документировано. |
| `ReadableStream` + `TextDecoder` на мобильных Safari < 10? | Не поддерживается. См. caniuse ~99% покрытие. Fallback не реализуем в этой фиче (TODO если будут жалобы). |
| KDoc coverage упадёт при добавлении `ZakromaStreamMessageDto` и `ZakromaAlbumMetaPublicDto` (это публичные DTO). | Добавить KDoc + `@see docs/features/zakroma-stream-progress.md` на оба класса. |
| JSDoc coverage упадёт на `useZakromaStreamProgress` (новый composable). | Добавить JSDoc с `@see`. |
| `ktlint` может ругаться на длинные строки в endpoint (`zakromaStream` много параметров и boilerplate). | Принять baseline-исключение или разбить на helper-методы (`writeMessage(out, type, ...)`, `streamAlbums(out, zakroma)`). |
| `git push` через VPN падает (см. AGENTS.md «Push-ловушка»). | Если пуш упадёт — попросить пользователя запустить `deploy_*.sh` без VPN. Сам пуш делаю я, deploy на прод — пользователь. |

## Acceptance for /speckit.plan → /speckit.tasks

Этот plan готов. Следующий шаг — `/speckit.tasks`, который разобьёт
6 коммитов на конкретные задачи (Phase 2). Задачи будут:

- **BE-T1**: создать `ZakromaAlbumMetaPublicDto.kt` + KDoc + @see.
- **BE-T2**: создать `ZakromaStreamMessageDto.kt` + KDoc + @see.
- **BE-T3**: добавить `zakromaStream(...)` endpoint в `PublicApiController.kt`
  + KDoc + @see + helper `writeNdjsonMessage()`.
- **BE-T4**: скомпилировать (`./gradlew :karaoke-web:compileKotlin`),
  убедиться что нет ошибок.
- **NX-T1**: добавить `location /api/public/zakroma/stream` в `deploy/80to8897`.
- **NX-T2**: создать `tools/deploy-nginx-stream.sh` (rsync + nginx -t + reload).
- **DOC-T1**: создать `docs/features/zakroma-stream-progress.md` (6 разделов).
- **DOC-T2**: обновить `docs/features/README.md` (добавить строку).
- **FE-T1**: создать `useZakromaStreamProgress.js` + JSDoc + @see.
- **FE-T2**: обновить `zakroma.js` (заменить `loadZakroma` → `loadZakromaStream`).
- **FE-T3**: обновить `ZakromaView.vue` (использовать composable, progress UI).
- **CLEANUP-T1**: удалить старый код (debounce, текст «Загрузка...»,
  `useZakromaLoadProgress` если был).
- **QA-T1**: `./gradlew ktlintCheck` + ESLint + JSDoc/KDoc coverage.
- **QA-T2**: ручная проверка quickstart сценариев (TTFB, прогресс,
  отмена, ошибки).
- **GIT-T1**: `git push -u origin 181-zakroma-author-load-progress`.
- **GIT-T2**: `gh pr create --base master` + CI 7/7 PASS → merge.
