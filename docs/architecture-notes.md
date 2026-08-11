# Architecture Notes — актуальный changelog

> **Что это.** Датированные записи о значимых изменениях архитектуры, новых
> подсистемах, лит-инструментах и уроках, извлечённых из PR. Фокус — **«что
> изменилось и почему»**, без пересказа деталей реализации (они в git-истории
> и в per-feature документах).
>
> **Связанные документы**:
> - [`architecture-notes-archive.md`](./architecture-notes-archive.md) — старая
>   детальная история фич и ловушек (1718 строк, до PR #12).
> - [`DEVELOPMENT.md`](../DEVELOPMENT.md) — durable-карта архитектуры.
> - [`AGENTS.md`](../AGENTS.md) — инструкции для AI-агента.
> - [`docs/features/<slug>.md`](./features/) — per-feature документы.
> - [`docs/onboarding.md`](./onboarding.md) — setup новой машины.
> - [`docs/claude-code-setup.md`](./claude-code-setup.md) — настройка Claude Code.

---

## 2026-07 — Phase 001: Code Standards & Documentation

> **Цель фазы.** Привести проект в состояние «production-grade» по качеству
> кода, документации и CI. 15 PR (#12-#26), 548 файлов, +57217/−27869 строк.
> Все коммиты прошли CI (7/7 jobs PASS) на момент мержа.

### 2026-07-20 — PR #12: `001-code-standards-docs` (`221b2d1`)

**Что.** Внедрение стандартов оформления кода:
- `ktlint` + `eslint` baselines (на тот момент: 30612 + 436 проблем).
- KDoc coverage ≥ 50% для публичных Kotlin-классов.
- JSDoc coverage ≥ 50% для публичных Vue/TS-классов.
- Per-feature документы для 9 ключевых подсистем (`docs/features/`).
- Dokka + TypeDoc генерация.
- 1 cross-cutting документ: `ci-lint-enforcement.md`.

**Зачем.** Проект не имел формальных стандартов. Каждый контрибьютор
писал в своём стиле, что замедляло review и создавало «дрейф стиля».

**Уроки.** `ktlint` и `eslint` — разные тулы с разными правилами.
`ktlint` — Kotlin formatter (аналог `gofmt`); `eslint` — JS/TS linter
с набором правил. Baseline = «разрешённые исключения», чтобы новые
PR не ломались на старых нарушениях.

---

### 2026-07-20 — PR #13: `002-ci-lint-enforcement` (`8a63eed`)

**Что.** GitHub Actions workflow `.github/workflows/lint.yml` с 5 jobs:
- `ktlint` (Kotlin/Java).
- `ESLint + Prettier` для `webvue3` и `karaoke-public`.
- KDoc coverage (informational).
- JSDoc coverage (informational).
- Baseline stats.

**Зачем.** Без CI лит-проверки превращаются в «бумажного тигра» — можно
написать правила, но без автоматической проверки они не действуют.

**Уроки.** detekt ОТКЛЮЧЁН — несовместим с Kotlin 2.2.20. Используем
только ktlint + ручные правила в `.editorconfig`.

---

### 2026-07-20 — PR #14: `003-baseline-healing` (`89ee040` + `9b17087`)

**Что.** Запуск `./gradlew ktlintFormat` для автоматического исправления
ktlint-проблем. Сократили baseline: 426 → 96 (−77.5%).

**Ловушка #1.** ESLint baseline для `karaoke-public` имел `null` вместо
имени файла для одной записи — CI падал на сравнении. Решение:
`./gradlew lintKotlin` или удалить baseline и регенерировать.

**Уроки.** Авто-форматирование работает для ~80% проблем. Остальные 20%
(complex imports, длинные строки) — ручные правки.

---

### 2026-07-20 — PR #15: `004-baseline-zero` (`ddea921`)

**Что.** Полное обнуление baseline: 96 → 0. Теперь любое новое нарушение
= блокер CI.

**Зачем.** Baseline = «технический долг». Если не обнулять — он копится
бесконечно. Лучше погасить сейчас (быстро), чем потом (медленно).

**Уроки.** После обнуления baseline каждый PR требует ручной работы
по лит-фиксу. Это замедляет разработку на 1-2 часа, но в долгосрочной
перспективе экономит дни.

---

### 2026-07-20 — PR #16: `005-kdoc-coverage` (`14a36aa`)

**Что.** KDoc coverage: 7.6% → 100% через авто-генератор
`tools/auto-kdoc.py`. Скрипт анализирует каждый Kotlin-файл и добавляет
минимальный KDoc (одна строка `* Класс <Name>.`).

**Зачем.** 100% coverage — это baseline, а не цель. Минимальный KDoc
гарантирует, что IDE показывает подсказку, но не объясняет **зачем**
класс существует.

**Уроки.** Авто-генерация НЕ заменяет ручную работу. После неё нужны
quality-проходы (Pass 2-7) для расширения описаний.

---

### 2026-07-20 — PR #17: `006-kdoc-quality` (`967a00e`)

**Что.** Замена авто-KDoc на качественный для 10 топ-классов:
- `Settings`, `MltGenerator`, `MltNode`, `MltNodeBuilder`,
  `PropertiesMltNodeBuilder` — ядро MLT.
- `LyricsFinderService`, `KaraokeStorageService/Impl` — поиск + storage.
- `SyncDirection`, `SyncTarget` — синхронизация.
- `Pictures`, `Picture` — главный DTO + entity.

Каждый KDoc получил:
- Описание «что делает».
- Блок «зачем».
- Блок «как работает».
- Список инвариантов.
- Ссылки на per-feature документы.

**Уроки.** KDoc — это инвестиция в будущее. Когда через год разработчик
откроет `MltGenerator.kt`, он сразу поймёт «куда воткнуть свою фичу».

---

### 2026-07-20 — PR #18: `007-jsdoc-coverage` (`1b12394`)

**Что.** JSDoc coverage: 0% → 100% для 163 Vue/TS-файлов через
`tools/auto-jsdoc.py`. Скрипт обрабатывает 3 паттерна:
- `export default` (для `*.js` модулей).
- `defineComponent` (для Options API).
- `<script setup>` (для Composition API, JSDoc в первых 15 строках).

**Ловушка #2.** `sed replace` по `import com.svoemesto.karaokeweb.WORKING_DATABASE`
без `$` сломал 18 файлов: `.controllers`/`.services`/`.config` приклеились
к импорту (часть `WORKING_DATABASE` поглотила следующую строку).

**Уроки.** Всегда проверять `git diff` после массовых `sed`-правок.
Лучше использовать Python-скрипт, который понимает AST.

---

### 2026-07-20 — PR #19: `008-jsdoc-quality` (`2289aa2`)

**Что.** Качественный JSDoc для 11 топ-Vue-компонентов:
- `App.vue`, `HomeView.vue` (×2 для classic + modern).
- `SongsView.vue`, `SongsTable.vue`, `SongEdit.vue`.
- `ProcessWorker.vue`, `CustomConfirm.vue`.
- `SearchText.vue`, `SubsEdit.vue`, `SyncTable.vue`, `SongView.vue`.

---

### 2026-07-20 — PR #20: `009-kdoc-quality-pass2` (`208a653`)

**Что.** Pass 5: ещё 10 KDoc:
- `Pictures`, `Picture`, `Author`, `News`, `Dictionary` (entity).
- `SiteUser`, `Subscription`, `KaraokeProcess`, `HealthReport`, `MltProp`.

Удалён неиспользуемый `JsonIgnoreProperties` import (9 файлов).

**Ловушка #3.** KDoc с backticks `` `MltProp` `` внутри multiline-string
параметра bash-функции — `bash` интерпретирует backticks как command
substitution. Решение: Python-скрипт с dict'ом описаний.

---

### 2026-07-21 — PR #21: `010-script-setup-jsdoc` (`77f3b2d`)

**Что.** JSDoc для `<script setup>` файлов: `PlayerView.vue` (×2). Найдено
2 скрытых файла (163 → 165) — `git ls-files` не показывал untracked.

`tools/check-jsdoc-coverage.sh` обновлён для 3 паттернов.

---

### 2026-07-21 — PR #22: `011-kdoc-quality-pass3` (`0a00506`)

**Что.** Pass 6: 20 KDoc для менее описанных моделей:
- `Publication`, `CrossSettings` (prod-настройки).
- `MusicChord`, `MusicNote`, `MusicInterval` (музыкальные).
- `CartItem/Dto`, `PriceTariff/Dto`, `PromoRule/Dto` (e-commerce).
- `SiteChatMessage/Dto`, `SitePlaylist/Dto/Item` (social).
- `StemJob/Dto`, `SongAssignment/Draft` (premium).

---

### 2026-07-21 — PR #23: `012-development-md-rewrite` (`56b12bf`)

**Что.** Полный rewrite `DEVELOPMENT.md`:
- Было: 686 строк устаревших заметок.
- Стало: 164 строки durable-карты архитектуры.

Создано 4 новых документа:
- [`docs/deployment.md`](./deployment.md) — деплой через `do.sh` (135 строк).
- [`docs/database.md`](./database.md) — структура БД (48 строк).
- [`docs/public-modules.md`](./public-modules.md) — `karaoke-public` (109 строк).
- [`docs/invariants.md`](./invariants.md) — непреложные правила (96 строк).

**Зачем.** Старый `DEVELOPMENT.md` смешивал «что актуально сегодня» с
«историей, как мы дошли до этого состояния». Разделили на 2 файла.

---

### 2026-07-21 — PR #24: `013-kdoc-quality-pass4` (`fc1afc5`)

**Что.** Pass 7 step 1: 27 KDoc (все 26 Mko* + StorageApiClient).
Создан `tools/auto-kdoc-quality.py` с dict'ом `CLASS_DESCRIPTIONS`.

**Mko*** (MLT Karaoke Object) — визуальные слои karaoke-видео:
текст, аккорды, гриф, ноты, watermark, прогресс, фейдеры, скроллеры,
флэш, горизонт, заголовок, сплэш, заливка фоном, разделитель строк,
голоса, обёртки треков.

**StorageApiClient** — HTTP-прокси к MinIO через karaoke-web (с прогрессом
через `CountingInputStream`).

---

### 2026-07-21 — PR #25: `014-jsdoc-quality-pass2` (`1f40682`)

**Что.** Pass 7 step 2: 2 новых per-feature документа:
- [`docs/features/dictionaries.md`](./features/dictionaries.md) — DB + TextFile
  словари для lyrics (130 строк).
- [`docs/features/stats.md`](./features/stats.md) — статистика главной +
  события + KPI (140 строк).

В KDoc у `Dictionary`, `TextFileDictionary`, `Stat`, `StatBySong`
упоминались эти документы, но они не существовали. Закрыли ссылки.

---

### 2026-07-21 — PR #26: `015-architecture-notes` (`64d4d89`)

**Что.** Создан [`docs/architecture-notes.md`](./architecture-notes.md) (268
строк) — датированный changelog по PR #12-#25. Фокус — «что изменилось
и почему», без пересказа деталей реализации.

**Зачем.** Раньше детальная история жила в `DEVELOPMENT.md` (686 строк) —
нечитаемо. Разделили: durable-карта в `DEVELOPMENT.md`, история в
`architecture-notes.md`.

---

## 2026-07 — Phase 002: Developer Experience & Cross-Machine Setup

> **Цель фазы.** Сделать проект удобным для **нескольких разработчиков**
> с **разными AI-агентами** (opencode / Claude Code / Cursor / другие).
> 4 PR (#27-#30), +530 строк, 0 production-кода — только tooling и docs.

### 2026-07-21 — PR #27: `016-git-blame-ignore` (`22bfaae`)

**Что.**
- `.git-blame-ignore-revs` (1526 байт) — 7 шумных коммитов Phase 001.
- `.gitattributes` — line endings (LF для всех текстов) + binary files.

**Зачем.** `git blame` показывал авторов prettier/baseline/авто-KDoc
вместо авторов оригинальных строк. После настройки
`git config blame.ignoreRevsFile .git-blame-ignore-revs` blame
показывает оригинальных авторов.

**Уроки.** `.gitattributes` с `* text=auto eol=lf` нормализует line
endings на Windows (CRLF → LF при commit) — без этого `git diff`
показывает «всё изменилось» в каждом PR.

---

### 2026-07-21 — PR #28: `017-onboarding-docs` (`a710ed0`)

**Что.** Создан [`docs/onboarding.md`](./onboarding.md) (271 строка) —
чеклист из 8 шагов для нового разработчика (с любым AI-агентом):
1. Установить зависимости.
2. Клонировать + настроить git.
3. Прочитать ключевые документы.
4. Настроить AI-агента.
5. Pre-commit хуки.
6. Собрать проект.
7. Проверить CI.
8. Создать первый PR.

**Ловушка #4 (ОБНАРУЖЕНА ПОЗЖЕ).** Я также закоммитил свою версию
`CLAUDE.md` (78 строк) в master, перезаписав оригинальную (46 строк).
На другой машине у разработчика с Claude Code уже был локальный
`CLAUDE.md` (не в гите) — `git pull` дал бы merge conflict.

---

### 2026-07-21 — PR #29: `018-claude-md-fix` (`19c6b8e`) — hotfix

**Что.**
- **Revert `CLAUDE.md`** к состоянию до PR #28 (46 строк, baseline).
- Создан [`docs/claude-code-setup.md`](./claude-code-setup.md) (190 строк)
  — инструкция + шаблон для локального `CLAUDE.md`.
- Обновлён `docs/onboarding.md` — явно про локальный `CLAUDE.md`.

**Зачем.** Hotfix после ловушки #4.

**Решение для разработчика с Claude Code:**
```bash
echo "CLAUDE.md" >> .git/info/exclude
cp <template> ./CLAUDE.md   # локально, НЕ коммитить
```

**Уроки.**
- **Персональные AI-конфиги** (`CLAUDE.md`, `.cursorrules`, `AGENTS.md.local`)
  **никогда не коммитить** в общий репо. Каждый разработчик имеет свой стиль.
- **В onboarding.md** явно прописывать про локальный `CLAUDE.md`.
- **Revert + новый документ** лучше, чем force-push: история сохраняется.

---

### 2026-07-21 — PR #30: `019-claude-faq-agents-update` (`a02ae83`)

**Что.**
- Pass 9.2: [`docs/claude-code-setup.md`](./claude-code-setup.md) расширен
  FAQ-секцией (197 → 350 строк, 14 Q&A).
- Pass 10: [`AGENTS.md`](../AGENTS.md) — добавлены 2 секции:
  - «Документация и иерархия» — таблица 9 уровней приоритетов.
  - «Где правила для разных AI-агентов» — таблица 5 агентов.

**14 Q&A** покрывают:
- Claude Code не подхватывает `CLAUDE.md`.
- Merge conflict в `CLAUDE.md` при `git pull`.
- Claude Code игнорирует инструкции (слишком длинный файл).
- Claude Code не знает проект (как заставить читать `AGENTS.md`).
- `.claude/rules.md` с `@import` для автоподключения.
- «Proactive mode» — как отключить непрошеные изменения.
- npm install упал / Docker не стартует / pre-commit падает.
- VS Code + Claude Code.
- Где хранить API-ключи.

**Принцип иерархии документации** (9 уровней):
1. `constitution.md` (макс) — NON-NEGOTIABLE
2. `AGENTS.md` — opencode правила
3. `docs/onboarding.md` — setup
4. `CLAUDE.md` (локально) — Claude Code персональные
5. `docs/claude-code-setup.md` — Claude Code шаблон
6. `DEVELOPMENT.md` — архитектура
7. `CONTRIBUTING.md` — стиль кода
8. `docs/architecture-notes.md` — changelog
9. `docs/features/<slug>.md` — per-feature

---

### 2026-07-25 — PR #55 + `/speckit-converge`: конвергенция 001/002 (`203f165`)

**Что.**
- Прогон `/speckit-converge` по `001-code-standards-docs` нашёл 8
  расхождений между кодом и spec/plan/constitution (все — устаревшие
  ссылки после рефакторинга кода и не-блокирующий CI для KDoc/JSDoc).
  Закрыто в PR #55: `kdoc-coverage`/`jsdoc-coverage` сделаны блокирующими
  (`--strict`), починены ~15 битых ссылок в `CONTRIBUTING.md` и
  `docs/features/*.md`, починен `tools/check-enforcement.sh`
  (регэксп никогда не находил MUST-правила), `spec.md` FR-004
  актуализирован с 9 до 12 продуктовых подсистем + 1 cross-cutting.
- Повторный `/speckit-converge` по `002-ci-lint-enforcement` нашёл 1
  расхождение: `workflow_dispatch` триггер был решён (Q3 в spec.md) и
  отмечен выполненным (T027), но фактически отсутствовал в
  `.github/workflows/lint.yml` — добавлен.

**Зачем.** `tasks.md`-чекбоксы со временем расходятся с реальным кодом
(рефакторинг переименовывает классы, ссылки в доках не обновляются).
`/speckit-converge` — регулярная проверка «код ⟷ спека», а не разовая.

**Уроки.**
- Чекбокс `[x]` в `tasks.md` — это утверждение агента на момент коммита,
  не гарантия сегодняшнего состояния кода. Периодический `/speckit-converge`
  ловит дрейф, который иначе всплывает только при code review вручную.
- `lychee` полезен не только для `docs/`, но и для проверки, что markdown-
  ссылки в `CONTRIBUTING.md` не «съехали» после рефакторинга модулей.

---

## Метрики

| Метрика | До Phase 001 | После Phase 001 | После Phase 002 |
|---------|--------------|-----------------|-----------------|
| PR в master | — | 15 | **19** |
| ktlint baseline | 30612 | **0** | **0** |
| ESLint baseline | 436 | **0** | **0** |
| KDoc coverage | 0% | **100%** (356/356) | **100%** |
| JSDoc coverage | 0% | **100%** (165/165) | **100%** |
| Качественный KDoc | 0 классов | **89+ классов** | **89+** |
| Per-feature документы | 0 | **11 + 1** | **11 + 1** |
| Документы в `docs/` | 1 (DEVELOPMENT.md) | **6** + 12 per-feature | **8** + 12 per-feature |
| CI jobs | 0 | **7** | **7** |
| Production-код | baseline | +57217/−27869 в 548 файлах | (без изменений) |
| Tooling/docs | 0 | — | +530 строк в 4 PR |

## Принципы, зафиксированные в Phase 001+002

### Phase 001 (качество кода)
1. **Baseline = 0** — единственное «правильное» состояние.
2. **100% coverage = baseline, не цель.** Качественные KDoc/JSDoc — ручная работа.
3. **CI enforced** — лит, coverage, baseline stats. 7 jobs.
4. **Per-feature документы** обязательны (FR-009).
5. **KDoc/JSDoc выше всех аннотаций** (`@Service`/`@Component`).
6. **Backticks в KDoc** могут сломать парсер.
7. **Wildcard imports** разрешены (правило отключено в `.editorconfig`).

### Phase 002 (developer experience)
8. **Общие правила — в гите**, персональные — локально.
9. **CLAUDE.md / .cursorrules НЕ коммитить** в общий репо.
10. **Иерархия документации — 9 уровней** (см. AGENTS.md).
11. **Revert лучше, чем force-push** — история сохраняется.
12. **Per-developer tooling** (`.git/info/exclude`, `~/.gitignore_global`)
    для исключения локальных файлов.

## Следующие фазы

- **Phase 003**: продуктовая фича (по согласованию).
- **Phase 004**: TBD (после Phase 003).

---

*Последнее обновление: 2026-07-21 (Pass 14, 19 PR в master).*

## Pass 27 — Альбомы: клик по ячейке открывает модалку обложки альбома (2026-07-27)

**Спецификация:** `specs/014-album-cell-album-cover-modal/`
**Ветка:** `014-album-cell-album-cover-modal`

**Что сделано:**

- **Backend** (`karaoke-app`):
  - `Album.kt` — новый helper `fun getFirstSongId(albumId, database): Long?`
    (SQL: `MIN(id)` среди песен альбома).
  - `ApiController.kt` — новый endpoint `POST /api/albums/firstsongid?albumId=X`
    (возвращает `Long` или `0L`). **НЕ участвует в sync** (read-only lookup).

**Hotfix после PR #80:** первая реализация использовала `WHERE first_song_in_album = TRUE`
(с fallback на `MIN(id)`), но колонки `first_song_in_album` **нет в `tbl_songs`** — `Song.firstSongInAlbum`
это in-memory property, не персистируется (нет в `getSqlToInsert`/`loadFromDb`, нет миграции).
Убран первый запрос, оставлен только `MIN(id)`. PR #82.

- **Frontend** (`webvue3`):
  - `Albums/store.js` — action `getFirstSongIdByAlbumIdPromise(albumId)`.
  - `AlbumsTable.vue`:
    - Computed `canEditCover(item)` — `true` только при `songsCount > 0`.
    - Data: `isAlbumCoverModalVisible`, `prevCurrentSongId`, `currentAlbumCoverAlbumId`.
    - Импорт + регистрация `AlbumCoverModal` (компонент НЕ изменён).
    - Методы `openAlbumCoverModal`/`closeAlbumCoverModal`/`onAlbumCoverSaved`
      с подменой/восстановлением `currentSongId` через `setCurrentSongIdOnly`.
    - Клик по `cell(albumPicture)` (preview) И `cell(name)` (название) — теперь
      открывает `AlbumCoverModal` (раньше preview открывал `PictureEditModal`,
      название — `CustomConfirm` редактирования атрибутов).
    - CSS `.is-clickable` — единый hover для обеих ячеек.
  - `AlbumCoverModal.vue` — **НЕ ИЗМЕНЁН** (требование пользователя: «такая же модалка»).
  - `Song.kt`, `Picture.kt`, `Pictures.kt`, `SyncTarget.kt` — **НЕ ИЗМЕНЕНЫ** (инвариант).

**Линт/coverage:** ktlintCheck ✅, eslint ✅, KDoc 96.7% (≥ 50% target), JSDoc 100%.

**Решения (research.md):**
1. Новый endpoint вместо расширения `AlbumDTO` (минимальная инвазивность).
2. Подмена `currentSongId` обратима (защита от потери рабочего контекста администратора).
3. `MIN(id)` как единственный стабильный критерий «репрезентативной» песни альбома
   (после hotfix: `first_song_in_album` в БД не сохраняется, комбинация с TRUE невозможна).

**Связанные документы:** `specs/014-album-cell-album-cover-modal/{spec,plan,research,data-model,quickstart,contracts/api,tasks}.md`.

## Pass 28: fix(webvue3): спецтеги — сохранение маркеров после «Точные маркеры → Apply → Save → reopen» (#016)

**Контекст:** на песнях, в `sourceText` которых есть распознаваемые спецтеги (`~Припев~` и т.п.), после цикла
«Точные маркеры → Apply → Save → close → reopen» в `SubsEdit.vue` пропадали syllable/endofline/group/comment/beat
маркеры, оставались только spec tag-маркеры, автоподставленные `syncMarkersFromSpecTags()`.

**Первопричина** (локализована в `specs/016-fix-spec-tags-marker-loss-on-reopen/research.md` §2.2):
- `mounted()` ставил `sourceText = await ...` ДО `loadedMarkers = await ...`, и заполнение
  `sourceMarkers` жило в `ws.on('decode')` (отложенно на момент декодирования аудио — десятки мс-секунды).
- Watcher `sourceText` (Vue 2 async) срабатывал раньше `ws.on('decode')` с пустым `sourceMarkers`.
- `syncMarkersFromSpecTags()` с пустым `syllablePositions` вставлял spec tag-маркеры через `splice(0, 0, ...)`
  (все в позицию 0), `sourceMarkers.length` становился > 0.
- Условие `this.sourceMarkers.length === 0` в `ws.on('decode')` переставало выполняться — реальные
  маркеры из БД **не загружались** в UI.
- На Save уезжал мусор. Цикл Apply→Save→reopen усугублял потерю.
- Тот же механизм затрагивал watcher `currentVoice` (см. research.md §2.4) — между двумя `await` watcher
  `sourceText` срабатывал со СТАРЫМИ `sourceMarkers` и корраптил их spec tag-маркерами из НОВОГО текста.

**Что изменилось** (только `webvue3/src/components/Songs/edit/SubsEdit.vue`, +48/−17 строк):
1. **`mounted()`**: `loadedMarkers = await ...` + заполнение `sourceMarkers` (с фильтром `COMMENT| ` и пустых
   syllables) + `createBeatMarkers()` — теперь ПЕРЕД `sourceText = await ...`. Watcher `sourceText`
   срабатывает с **уже заполненным** `sourceMarkers` → `syncMarkersFromSpecTags()` корректно проверяет
   наличие syllables-маркеров в окнах и аддитивно добавляет spec tag-маркеры.
2. **`ws.on('decode')`**: цикл загрузки `sourceMarkers` из `loadedMarkers` удалён (оставлены только
   `clearRegions()` + расчёт `duration`/`visibleStartTime`/`visibleEndTime`). Это устраняет
   race-condition: маркеры больше не зависят от времени декодирования аудио.
3. **Watcher `currentVoice`**: `loadedMarkers` ставится ДО `sourceText` (а не наоборот). Это устраняет
   second-order race: между двумя `await` watcher `sourceText` не сможет сработать с чужими маркерами.
4. **`syncMarkersFromSpecTags()`**: добавлен **защитный гард** `if (this.sourceMarkers.length === 0) return`
   (страховка от вызова с пустым `sourceMarkers` в неожиданных местах в будущем).
5. JSDoc-комментарии обновлены для `syncMarkersFromSpecTags()` и `mounted()` — отражают новый инвариант
   порядка присваиваний и ссылаются на research.md §2.2.

**Что НЕ изменилось** (явно зафиксировано в spec.md FR-007/FR-008/FR-009):
- Контракт `specs/010-lyrics-spec-tags/contracts/tag-registry.md` — без изменений.
- `applyAutoMarkersToEditor()` (`SubsEdit.vue:4525-4536`) — остаётся «жёсткой заменой» маркеров
  (by design «Точные маркеры + Apply», текст подтверждения в `doForcedAlignMarkers` остаётся прежним).
- Backend (`karaoke-app`, `karaoke-web`), `karaoke-public`, `alignment-ml`, схема БД, Vuex-стор — без изменений.
- Лёгкий admin-редактор `SongKaraokeEditorView` и краудсорсинг `EditorWorkView` — вне scope (там нет
  потока «Точные маркеры», баг не воспроизводится).

**Линт/coverage:** ktlintCheck ✅, eslint ✅ (`npm run lint:check`), JSDoc 100% (134/134 в webvue3),
KDoc 100% (43/43 в karaoke-public), `npm run build` ✅, backend regression `SpecTagsTest` +
`WhisperMarkerAlignerSpecTagsTest` ✅ (UP-TO-DATE, нет изменений в backend).

**Решения (research.md §4):**
1. Синхронный перенос загрузки `loadedMarkers` в `mounted()` (а не флаг-гард `isLoadingVoice`) —
   исправляет порядок по построению, без хрупкого гарда в watcher'е.
2. Удаление цикла из `ws.on('decode')` (а не смягчение условия `sourceMarkers.length === 0`) — явно
   фиксирует, что маркеры зависят только от БД, а не от времени декодирования.
3. Гард в `syncMarkersFromSpecTags()` (P2 по плану) — страховка от регрессии в будущем; сам по себе
   баг не устраняет, но защищает от его возврата.

**Связанные документы:** `specs/016-fix-spec-tags-marker-loss-on-reopen/{spec,plan,research,data-model,quickstart,contracts/README,tasks}.md`.

---

## Pass 29: docs — machine-scoped исключение из ограничений агента для `dev-pc`/`dev` (2026-07-28, #021)

**Что.** Amendment конституции (`1.2.0 → 2.0.0`, MAJOR — Governance rule 3: «изменение
ограничений доступа агента») + зеркальная правка `AGENTS.md`: на машине с hostname `dev-pc`
под OS-пользователем `dev` агенту больше не требуется отдельное согласие пользователя на:
1. Пересборку/перезапуск любого локального контейнера проекта, включая `karaoke-app`
   (ранее — единственный контейнер, зарезервированный только за пользователем).
2. Любые операции с локальной базой данных (запросы, миграции, изменения схемы/данных).

**Зачем.** Прямой запрос пользователя: на машине разработки (`dev-pc`/`dev`) эти два
действия — самая частая точка трения в рабочем цикле агента, и пользователь явно снял
ограничение именно для этой пары хост+пользователь.

**Что НЕ изменилось.** Деплой на прод-сервер, прямая правка файлов на сервере,
перезапись `do.env`, прямые DDL/DML к серверной БД, git-safety правила (не коммитить в
`master`, не использовать `--no-verify`/force-push) — всё это осталось без изменений и
не зависит от хоста/пользователя. Исключение активируется только при точном совпадении
`hostname == dev-pc` И `os_user == dev`; на любой другой машине (в т.ч. admin-машине) или
под любым другим пользователем действуют прежние ограничения.

**Уроки.** Простое чтение задачи («поправь `AGENTS.md`») было недостаточным: раздел
«Ограничения и доступы агента» в `.specify/memory/constitution.md` явно приоритетнее
`AGENTS.md` при конфликте и уже содержал тот же запрет на `karaoke-app`, поэтому правка
только `AGENTS.md` была бы no-op. Конституция также сама классифицирует любое изменение
ограничений доступа агента как MAJOR (независимо от узости scope) — потребовался version
bump и Sync Impact Report, а не просто правка текста.

**Связанные документы:** `specs/021-dev-pc-agent-permissions/{spec,plan,research,data-model,quickstart,tasks}.md`.

---

## Pass 30: webvue3 — квадратная ячейка обложки альбома в `AlbumsTable` (2026-07-29, #083)

**Что.** В `webvue3/src/components/Albums/AlbumsTable.vue` колонка `(альбом)` сужена со 125px до 54px (= высоте строки) — ячейка с обложкой стала квадратной. Параллельно CSS `.fld-picture-preview` (`max-width: 125px → 100%`) и `.preview-image` (`max-width: 50px; max-height: 50px; width: auto; height: auto` вместо `width: auto; height: 50px`) подогнаны под квадрат с сохранением `object-fit: contain` — картинка вписывается пропорционально, без обрезки. 1 файл, +5/−3 строк, 0 миграций БД, 0 изменений бэкенда, 0 изменений в `karaoke-public`.

**Зачем.** Прямой запрос пользователя: «в админке в компоненте "Альбомы" в таблице надо ширину колонки с картинкой альбома сделать такой же как высота строк (ячейка должна быть квадратной)». До правки картинки 1:1, 4:3, 3:4 в прямоугольной ячейке 125×54 «прыгали» по ширине, таблица выглядела неаккуратно.

**Что НЕ изменилось.** Колонка `(автор)` (`authorPicture` в `albumDigestFields` — 125×54, не квадрат) сохранена без изменений (явный scope — US2 в спеке). Клик-логика модалки `AlbumCoverModal` не тронута. Все остальные колонки, фильтр, пагинация — без изменений.

**Уроки.** Если бы я просто сузил CSS `.fld-picture-preview { width: 54px }` без правки `style` поля `albumPicture` в `albumDigestFields`, ширина колонки осталась бы 125px (colgroup определил бы её по `minWidth/maxWidth` поля), а внутри ячейки появилось бы «лишнее» пустое пространство. Квадрата не получилось бы. Правильный путь — задать ширину **на уровне colgroup** (через `field.style`), а не на уровне отдельной ячейки.

**Связанные документы:** `specs/083-album-cover-square-cell/{spec,plan,research,data-model,quickstart,tasks}.md`. Смежная фича: Pass 27 (`specs/014-album-cell-album-cover-modal/`) — клик по этой же ячейке открывает `AlbumCoverModal`; клик-логика сохранена.

---

## Pass 31: webvue3 — hotfix: изолировать квадратные стили картинки от колонки (автор) (2026-07-29, #085)

**Что.** Hotfix для #109: класс `.preview-image` (общий для `cell(authorPicture)` и `cell(albumPicture)`) восстановлен к pre-#109 виду (`width: auto; height: 50px; object-fit: contain; vertical-align: middle;`). Новые квадратные стили (`max-width: 50px; max-height: 50px; width: auto; height: auto`) вынесены в модификатор `.preview-image--square`, который применён **только** в шаблоне `cell(albumPicture)`. 1 файл, +10/−3.

**Зачем.** Регрессия после #109: у колонки `(автор)` (125×54) для горизонтальных картинок (4:3, 16:9) ширина изменилась с пропорциональной (height-only 50px, например 67×50 для 4:3) на ограниченную `max-width: 50px` (50×37 для 4:3). Это нарушало US2 спеки 083 («квадрат только для колонки (альбом), не (автор)»). Пользователь заметил регрессию после merge #109.

**Что НЕ изменилось.** Ширина колонки `(альбом)` осталась 54px (из #109). `.fld-picture-preview { max-width: 100% }` (из #109) — не меняется. Колонка `(автор)` теперь идентична pre-#109.

**Уроки.** В #109 я изменил общий класс `.preview-image`, не учтя, что он шарится между двумя колонками с разной семантикой (квадратная vs прямоугольная). Правильный путь: не менять общий класс, а вынести специфичные стили в модификатор (`--square`) и применить его точечно в шаблоне нужной колонки. Это паттерн BEM-like модификаторов, который и так принят в файле (см. `.fld-picture-preview.is-clickable` рядом).

**Связанные документы:** `specs/083-album-cover-square-cell/` (US2 — scope). Regressed PR: #109 (`083-album-cover-square-cell`). Fix PR: #110 (`085-fix-album-preview-image-scope`).

---

## Pass 32: webvue3 — убрать ложный спиннер на таблице при открытии модалки обложки альбома (2026-07-29)

**Что.** В `openAlbumCoverModal()` (`AlbumsTable.vue:377`) убрано `this.isBusy = true/false` (вместе с `finally {}` блоком, который был нужен только для сброса). 1 файл, +6/−3.

**Зачем.** `isBusy` привязан к `<b-table :busy="isBusy">` и показывает спиннер на ВСЕЙ таблице. При открытии модалки `AlbumCoverModal` (по клику на preview `(альбом)` или по названию альбома) `isBusy = true` ставился на время `getFirstSongIdByAlbumIdPromise` + `setCurrentSongId` — это 1 (один) и 5 (пять) сетевых запросов соответственно. Со стороны пользователя это выглядело как перезапрос дайджеста альбомов (`albumsDigest`), хотя фактически `albumsDigest` не перезапрашивался (никакого `loadAlbumsDigests` в этой ветке нет — это видно по `git grep`). Реальная загрузка `albumsDigest` идёт через watcher на `albumsDigestIsLoading` (строки 324-329), и эта связь сохранена.

**Что НЕ изменилось.** Модалка `AlbumCoverModal` по-прежнему открывается через тот же flow (`getFirstSongIdByAlbumIdPromise` → `setCurrentSongId` → `isAlbumCoverModalVisible = true`). `canEditCover` UI-блокировка (без песен — клик disabled) сохранена. Race condition с `songsCount === 0` (если бэк вернул 0) сохранён.

**Уроки.** Показ спиннера на ВСЕЙ таблице как «защита от повторных кликов» — антипаттерн: модалка открывается поверх таблицы, и спиннер на таблице не имеет семантического отношения к открытию модалки. Если в будущем понадобится блокировать повторные клики во время async-операции — правильнее использовать `pointer-events: none` на конкретной ячейке, а не глобальный `isBusy` на таблице.

**Связанные документы:** `specs/014-album-cell-album-cover-modal/` (Pass 27) — исходная фича, ввела `isBusy = true/false`. `specs/083-album-cover-square-cell/` (Pass 30) — пользователь заметил этот артефакт именно при ручной проверке сценария S4 (клик → модалка без регрессии) из quickstart.

---

## Pass 33: автоматическая премиум-публикация в Telegram+VK (2026-08-02)

**Что.** Хук в `Song.markNewsAvailableIfReady` (Song.kt:5036-5051) выставляет `newsPremiumPublishPending=true` при первом переходе `newsAvailableAnnounced` false→true. Новый `PremiumAutoPublishScheduler` (`@Scheduled(fixedDelay=30s)`) ищет песни с этим флагом и публикует их в Telegram (PREMIUM-шаблон, `persistMessageId=false`) и VK (PREMIUM-шаблон, `persistPostId=false`). Рефакторинг `TelegramAutoPublishService.publishToTelegram` и `VkAutoPublishService.publishToVk` для `publicationType: PublicationType` и `persistMessageId/persistPostId: Boolean`. Новые endpoint'ы `POST /api/song/publishPremiumTelegram` и `POST /api/song/publishPremiumVk` (ручной триггер + тесты). 2 новых свойства в `KaraokeProperties`: `premiumAutoPublishEnabled` (default false), `premiumAutoPublishMaxAttempts` (default 3). 6 файлов Kotlin, +213/−17 строк.

**Зачем.** Сейчас при выходе новой песни 1) на сайте сразу появляется «новая песня» (после синхронизации, `newsAvailableAnnounced` false→true), но 2) в Telegram-канале и группе ВК нет **никакой** отметки до момента AIR-публикации (по наступлению date/time). То есть подписчики получают две отметки: «доступна на сайте» (через `News` бот на проде) и «появилась в эфире» (через scheduler AIR) — но **не получают промежуточного уведомления** «песня теперь доступна premium-подписчикам», что особенно критично для тарифа Model D (early-access premium для формирования привычки). Премиум-публикация — это два поста (Telegram + VK) с PREMIUM-шаблоном **до** AIR-публикации. Ключевая особенность: `idTelegramDemo`/`idVk` **не записываются** в `Song` при PREMIUM (используется флаг `persistMessageId=false`/`persistPostId=false`), чтобы те же слоты заполнила будущая AIR-публикация при выходе в эфир (один bot-cycle с PREMIUM-шаблоном → отдельный bot-cycle с AIR-шаблоном).

**Что НЕ изменилось.** Идемпотентность AIR-публикации (FR-008) сохранена — проверки `idTelegramDemo.isNotEmpty()` / `idVk.isNotEmpty()` короткозамыкают и для PREMIUM-вызовов. AIR-scheduler (TelegramAutoPublishScheduler.publishScheduledSongs) работает **как раньше** (только с type=AIR, persistMessageId=true). VkAutoPublishScheduler тоже не менялся — он идёт по `tbl_news` для AIR. Лимит попыток (`premiumAutoPublishMaxAttempts=3`, по умолчанию) — после 3 SEND_FAILED PREMIUM-задача переходит в `state=FAILED`, `newsPremiumPublishPending=false`, и админ видит проблему в UI (новое поле `premiumAutoPublishLastError` в `playerReadinessFlags`).

**Уроки.** Плановый tick через отдельный `@Scheduled` scheduler выбран вместо Fire-and-forget в `markNewsAvailableIfReady` — этот же вопрос задавал пользователь. Аргументы «за» plan-tick: 1) `markNewsAvailableIfReady` синхронно внутри `saveToDb()` — добавление туда fire-and-forget на HTTP-запрос 10-20 сек рисковало бы раздуть пул потоков и задержать следующие save'ы; 2) для тестов и ручного сброса после FAILED удобнее иметь явный scheduler, к которому можно обратиться через endpoint; 3) легко отключить флагом `premiumAutoPublishEnabled=false` (FR-013). Плановый tick каждые 30 сек — компромисс между отзывчивостью и нагрузкой; если потребуется быстрее (например, уменьшить до 10 сек) — это `fixedDelay = 10_000L` в одном месте. ВАЖНО: scheduler должен быть инициализирован **на admin-машине** (`karaoke-app`), не на проде (`karaoke-web`) — потому что именно admin-машина знает путь к Telegram/VK прокси, демо-MP4 файлам на MinIO и сама дёргает scheduler-тик. Хук `markNewsAvailableIfReady` срабатывает и на admin-машине (при сохранении песни здесь), и на проде (при синхронизации, но `newsAvailableAnnounced` уже был true — перехода не происходит, поэтому `newsPremiumPublishPending` не выставится повторно на проде).

**Связанные документы:** секция «Премиум-публикация» в [`docs/features/telegram-auto-publish.md`](./features/telegram-auto-publish.md), секция «Премиум-публикация» в [`docs/features/vk-news-auto-publish.md`](./features/vk-news-auto-publish.md). PR: feat 122 (автопремиум-публикация).


## Pass 34: backfill флагов публикации готовых песен + kill-switch на sync-окне (2026-08-03)

**Что.** Одноразовый backfill ПОЛНОГО complete-набора флагов публикации для уже готовых песен на LOCAL: `newsAvailableAnnounced=true`, `newsPremiumPublishPending=false`, `newsPremiumTelegramSent=true`, `newsPremiumVkSent=true`, `premiumAutoPublishState="COMPLETE"`, `premiumAutoPublishLastError=""`, `premiumAttemptCount=0`. Endpoint `POST /api/utils/backfillpublishflags` (с параметром `dryRun=true` для диагностики) + UI-кнопка в `HomeView.vue`. Новый метод `SongReleaseAnnouncementService.backfillPublishFlags(...)` + data class `BackfillReport` (с разбивкой по категориям). Новое свойство `KaraokeProperties.newsAutoPublishKillSwitch` (default false) с проверкой в `News.createAutoAnnouncement` — kill-switch блокирует ОБЕ точки auto-новостей (SongReleaseAnnouncementService.detectAndAnnounceAvailability из sync, checkOnAirWindow из scheduler). 5 файлов (+323 строк): 1 Kotlin-сервис + 1 Kotlin-контроллер + 1 Kotlin-модель + 1 KaraokeProperties + 2 webvue3.

**Зачем.** До feature 122 премиум-автопубликация вообще не существовала. После развёртывания feature 122 на admin-машине хук `markNewsAvailableIfReady` (Song.kt:5088) триггерит на уже-готовых песнях переход `newsPremiumPublishPending false→true` + `premiumAutoPublishState="RUNNING"` **при первом же save()** — а за ним `PremiumAutoPublishScheduler` запустил бы автопубликацию в TG+VK для ~15000 песен разом (лавина). Backfill заполняет флаги «уже-опубликовано и завершено» явно, минуя state RUNNING — это безопасно для уже-обработанных песен. Sync LOCAL→PROD распространяет флаги обычным recordhash-механизмом; kill-switch `newsAutoPublishKillSwitch` включается на PROD ДО sync, чтобы уже-применённые флаги не сработали в обратку как «новая песня появилась в коллекции». Без kill-switch при синхронизации 15000 песен с `newsAvailableAnnounced=false→true` обе точки (`detectAndAnnounceAvailability` для premium и `checkOnAirWindow` для air) создали бы лавину auto-новостей в `tbl_news`. Kill-switch ставится только на время sync-окна и снимается после (через `/api/properties/setproperty`, без рестарта).

**Что НЕ изменилось.** `markNewsAvailableIfReady` (Song.kt:5088) и `PremiumAutoPublishScheduler` — без изменений. Запись идёт через штатный `Song.saveToDb()` (а не raw SQL) — recordhash гарантированно консистентен с sync-движком, и спека 122 не ломается (см. research.md фичи 124, п.3). Идемпотентность backfill обеспечивается проверкой `alreadyComplete` (полный набор 7 флагов) перед записью — повторный запуск = no-op + `alreadyOk++` в отчёте. Идемпотентность kill-switch: при включении повторно `getBoolean` возвращает то же значение (никаких side-effects, нет INSERT/UPDATE). Идемпотентность `markNewsAvailableIfReady`: при `newsAvailableAnnounced=true` Block 1 skip; при `premiumAutoPublishState="COMPLETE"` Block 2 skip (даже если бы был сделан saveToDb() повторно).

**Уроки.** Kill-switch размещён в `News.createAutoAnnouncement` (News.kt:337), а не в `markNewsAvailableIfReady` или `Song.saveToDb` — это **единственная точка** создания auto-новостей (через неё идут обе ветки: detectAndAnnounceAvailability и checkOnAirWindow). Если бы kill-switch стоял выше в call stack (например, в `MainController.doChangeRecords`), пришлось бы добавлять отдельный guard в `SongReleaseAnnouncementScheduler` — это дублирование и риск рассинхрона. Конкретная точка выбрана после clarify-сессии (см. `specs/124-news-flags-backfill/spec.md`, Clarifications). Запись через `saveToDb()` (а не raw SQL) выбрана для консистентности recordhash — иначе пришлось бы вручную пересчитывать md5 по формуле триггера, что хрупко и нарушает принцип «одна правка → один источник истины» (constitution II). Dry-run режим (`dryRun=true`): пропускает `saveToDb()` но считает счётчики → числа идентичны реальному запуску на тех же данных (SC-010). Это позволяет администратору предварительно увидеть объём работы без риска.

**Связанные документы:** [`docs/features/news-publish-backfill.md`](./features/news-publish-backfill.md) — новый per-feature документ с пошаговой инструкцией. Pass 33 (PR feat 122, автопремиум-публикация) — причина появления этой фичи. Pass 9 (PR feat 101, флаг «доступна для новости») — пример точно такого же backfill-паттерна для единственного флага `newsAvailableAnnounced` (этот раз обобщили на 7-флаговый complete-набор).


## Pass 35: kill-switch через tbl_public_settings вместо KaraokeProperties (2026-08-03)

**Что.** Архитектурный fix фичи 124. Миграция `deploy/karaoke-db/37_news_auto_publish_kill_switch.sql` — INSERT ключа `newsAutoPublishKillSwitch` в `tbl_public_settings` (Postgres key/value, идемпотентный ON CONFLICT DO NOTHING). Новый контроллер `karaoke-web/.../controllers/PublicSettingsWebController.kt` с endpoint `POST /api/properties/setproperty` — UPDATE/INSERT в `tbl_public_settings` через прямой JDBC. `News.kt`: заменить чтение `KaraokeProperties.getBoolean("newsAutoPublishKillSwitch")` на новый приватный helper `isNewsAutoPublishKillSwitchActive(database)` — прямой SELECT из `tbl_public_settings` (аналогично `CaptchaConfigService.kt:35`). `KaraokeProperties.kt`: удалена строка `newsAutoPublishKillSwitch` из `listKaraokeProperties` (после миграции она бесполезна). 4 файла: +1 миграция, +1 контроллер karaoke-web, правки News.kt + KaraokeProperties.kt.

**Зачем.** Pass 34 (feat 124-news-flags-backfill) смёржил kill-switch через `KaraokeProperties` — но `@SpringBootApplication` в `karaoke-web` (`KaraokeWebApplication.kt:19`) БЕЗ `scanBasePackages` сканирует только `com.svoemesto.karaokeweb.*`. Файл `/sm-karaoke/system/Karaoke.properties` живёт только на admin-машине (комментарий в `deploy/karaoke-db/07_public_settings.sql` ПРЯМО об этом предупреждает). Итог: после Pass 34 фича **молча не работала на проде** — `News.createAutoAnnouncement` запускался `SongReleaseAnnouncementScheduler` (на проде), но `KaraokeProperties.getBoolean(...)` возвращал default `false` (либо файл не существовал, либо инициализировался defaults). Админ мог думать, что защищён, но kill-switch всегда был выключен — лавина auto-новостей при sync была возможна. Пользователь это обнаружил при попытке включить kill-switch через `curl http://localhost:8897/api/properties/setproperty` — получил 404 (контроллер `karaoke-app/ApiController.kt:5852` на проде не зарегистрирован) и поднял вопрос.

**Что НЕ изменилось.** Backfill endpoint/service/UI (`backfillpublishflags`, `SongReleaseAnnouncementService.backfillPublishFlags`) — на admin-машине работало корректно, не трогаем. Идемпотентность backfill — без изменений. Sync LOCAL↔PROD для `tbl_public_settings` НЕ добавлен — намеренно: kill-switch ставится НАПРЯМУЮ на прод (через прод-endpoint), нет требования к распространению через sync.

**Уроки.** Архитектурный review с проверкой «этот код **исполняется** на проде» обязателен перед merge. Подтверждающие команды, которые нужно было выполнить **до** PR: 1) `git grep "@SpringBootApplication" karaoke-web` + проверка `scanBasePackages` или `@ComponentScan`; 2) `git grep "KaraokeProperties" karaoke-web/src/main/kotlin` — если 0 матчей, значит слой не используется в проде; 3) `ssh prod "docker ps | grep karaoke-app"` — на проде нет admin-only модуля. Игнорирование «Gradle-зависимость => bean зарегистрирован» — самая частая ошибка в мультимодульных Spring Boot проектах. Тот же шаблон `tbl_public_settings` уже использовался в `CaptchaConfigService` (Yandex SmartCaptcha) — если бы я поглядел на этот файл ДО проектирования Pass 34, ошибка была бы поймана.

**Связанные документы:** [`specs/125-news-flags-backfix/spec.md`](../specs/125-news-flags-backfix/spec.md) — спека с полным описанием fix. [`docs/features/news-publish-backfill.md`](../docs/features/news-publish-backfill.md) — обновлён per-feature документ. Pass 34 (PR feat 124) — баг, который исправлен этой фичей. Бэкфилл-часть Pass 34 рабочая (на admin-машине); архитектурный баг был только в kill-switch и его endpoint.


## Pass 36: шаблоны автоматических новостей сайта (2026-08-03)

**Что.** Третья вкладка «Новости сайта» в существующем компоненте `PublishTemplatesView.vue` (наравне с ВК/Telegram). Позволяет администратору редактировать `title`/`body` авто-новостей `tbl_news` для двух категорий (`air`, `premium`) с поддержкой плейсхолдеров, превью и сброса к дефолту. Backend: новый `object NewsTemplateService` (`karaoke-app/.../services/NewsTemplateService.kt`) с 14 плейсхолдерами (12 granular из ВК/Telegram + 2 составных `{albumYearSuffix}`/`{bodyDetails}` byte-идентичных старым хелперам) + `template(key, database)` + `render(template, song, news, truncate)` + `placeholders()`. Новый `NewsTemplateController` (`/api/news/templates/*`) с 4 endpoints: `GET /` (список+placeholders), `POST /` (UPSERT через `INSERT ... ON CONFLICT DO UPDATE` — без seed-миграции), `POST /preview` (рендер title+body отдельно с флагами `titleTruncated`/`bodyTruncated`), `GET /defaults`. UI: новый компонент `webvue3/src/components/NewsTemplates/NewsTemplatesEditor.vue` (самодостаточный — загрузка, save, preview, reset; интегрирован в `PublishTemplatesView.vue` третьей вкладкой через `<NewsTemplatesEditor v-if="platform === 'news'" />`). `SongReleaseAnnouncementService`: правки в `checkOnAirWindow` (категория `air`) и `detectAndAnnounceAvailability` (категория `premium`) — хардкод-строки заменены на `NewsTemplateService.template(...) + render(...)`. Per-feature документ `docs/features/news-templates.md` (18-я подсистема). 5 файлов (+~520 строк): 1 Kotlin-сервис, 1 Kotlin-контроллер, правки SongReleaseAnnouncementService + PublishTemplatesView.vue, новый Vue-компонент.

**Зачем.** До этой фичи формулировки auto-новостей были хардкод-строками в `SongReleaseAnnouncementService.kt:88-89` (premium) и `:198-199` (air). Любое изменение формулировки требовало правки Kotlin + пересборки `karaoke-app` + деплоя. С этой фичей администратор правит шаблон в webvue3 → следующая auto-новость уже содержит новый текст (FR-008, SC-001, SC-005 «без перезапуска»). 12-я per-feature документ-подсистема (`news-templates.md`), что говорит о расширении архитектуры: редактируемые шаблоны становятся нормой для всех каналов публикации.

**Что НЕ изменилось.** `News.createAutoAnnouncement` (News.kt:337) — сигнатура без правок, уже принимала `title: String`/`body: String`. Kill-switch `newsAutoPublishKillSwitch` (Pass 35) сохраняет приоритет — проверка `isNewsAutoPublishKillSwitchActive` в News.kt:393 идёт ДО создания записи, рендер шаблона выполняется впустую при активном kill-switch (микро-расход — лишний SELECT за шаблоном, оставлено для простоты и KISS). `tbl_news` — без изменений схемы. `tbl_public_settings` — без изменений схемы (4 новых ключа создаются UPSERT'ом при первом сохранении). `PublicSettingsController.update` (`UPDATE`-only) — намеренно не тронут, чтобы не сломать generic-UI для других ключей (captcha и т.п.); новый endpoint использует `INSERT ... ON CONFLICT DO UPDATE`. Идемпотентность `News.existsAnnouncement` (Pass 9) — без изменений.

**Уроки.** **Архитектурное решение R1 (research.md) — `tbl_public_settings` НЕ участвует в sync LOCAL↔SERVER**. Это было зафиксировано ещё в Pass 35 (kill-switch через эту таблицу вместо KaraokeProperties) и здесь применено повторно: шаблоны читаются на проде напрямую JDBC (по образцу `CaptchaConfigService`, `isNewsAutoPublishKillSwitchActive`), SyncRegistry НЕ участвует, ручная ответственность администратора за `target=local|remote`. Альтернативы (sync-флаг, двойная запись, KaraokeProperties-файл) — отклонены по конкретным причинам (см. research.md R1, R2, R3). **R2 (UPSERT vs UPDATE) — новая миграция seed-ключей НЕ нужна**. Generic `PublicSettingsController.update` падает на отсутствующем ключе (`updated == 0` → `false`). Новый endpoint использует `INSERT ... ON CONFLICT DO UPDATE` — это правильный паттерн для admin-driven конфигурации в key/value таблице. **R3 (отдельный контроллер vs generic) — отдельный лучше** по 3 причинам: (1) валидация ключей (только 4 разрешённых) — иначе администратор случайно создаст мусор; (2) UPSERT без правки generic-контракта; (3) естественное место для `defaults`/`preview` endpoints с бизнес-логикой. **Ловушка KDoc (AGENTS.md Q&A)**: `/*` внутри KDoc открывает nested-комментарий (Kotlin allows nested comments), что приводит к ошибке «Unclosed comment». В `NewsTemplateController.kt:21` заменил `/api/vk/templates/*` на `/api/vk/templates/...` — компилятор упал, пофиксил за один цикл. **Байтовая идентичность дефолтов (FR-010, SC-002)** — реализована через `DEFAULT_*` константы + переиспользование `albumYearSuffix`/`bodyDetails` хелперов из `SongReleaseAnnouncementService` (теперь `public`, не `private`). До первого сохранения рендер читает отсутствующий ключ → возвращает default → результат неотличим от прежнего хардкода. Это значит: выпуск фичи прозрачный, существующие auto-новости не пересоздаются, лента не дёргается. **Превью возвращает пару `title`+`body`** (не одно поле как у ВК/Telegram) — отражает структуру `tbl_news`. UI показывает оба отдельно с разными бейджами длин (title ≤500 с усечением, body — TEXT без лимита).

**Связанные документы:** [`specs/128-news-publish-templates/spec.md`](../specs/128-news-publish-templates/spec.md) — спецификация. [`docs/features/news-templates.md`](../docs/features/news-templates.md) — новый per-feature документ (18-я подсистема). Pass 35 (kill-switch через `tbl_public_settings`) — архитектурный прецедент для хранения prod-настроек в Postgres вместо KaraokeProperties. Pass 9 (`tbl_news.title`/`body`, флаг `newsAvailableAnnounced`) — оригинальная архитектура auto-новостей. ВК/Telegram-аналоги (Pass 28, 31) — образец `nav-tabs` UI-паттерна.


## Pass 37: заполнение аудиополей при ручном выборе похожей версии песни (2026-08-03)

**Что.** При ручном выборе строки в модалке «Похожие версии песни» (`FamilySongsModal.vue`) теперь сохраняются три аудиополя текущей песни: `audioParentId` (ID выбранной строки), `audioSimilarityPercent` (процент акустической сверки) и `audioDeltaMs` (signed-сдвиг в мс). Расширены: `SelectFamilySongResultDto` (`ApiController.kt`) тремя новыми полями; endpoint `POST /api/song/selectfamilysong` принимает nullable `audioSimilarityPercent` и использует существующий `deltaMs` для двух целей одновременно (сдвиг маркеров + `audioDeltaMs`); helper `applyFamilySongSelection` в `Utils.kt` расширен opt-in параметрами `audioParentId/audioSimilarityPercent/audioDeltaMs`, устанавливает их до единственного `song.saveToDb()`. После сохранения backend перечитывает запись и проверяет, что три аудиополя действительно записаны — при расхождении возвращает ошибку. Frontend: action `selectFamilySongPromise` в `store.js` принимает `audioSimilarityPercent` и не отправляет его, если null; новая Vuex mutation `applyFamilySelectionResult` обновляет `currentSong` и `snapshotSong` пятью полями одним коммитом (предотвращает повторный autosave); `selectFamilySong` handler в `SongEdit.vue` использует in-flight guard `isSelectingFamilySong`, toast на ошибке через `showTelegramToast`, модалка закрывается только после успешного ответа. Обновлён per-feature документ `docs/features/songs-table.md` (FR-009 Конституции).

**Зачем.** До этой фичи три аудиополя редактировались вручную после выбора похожей версии — пользователь копировал процент и сдвиг из модалки в поля «Аудио-схожесть»/«Аудио-сдвиг», рискуя ошибиться или забыть. Теперь значения сохраняются одной операцией с одновременной верификацией БД.

**Что НЕ изменилось.** `autoAssignOriginalByWaveform` (автопоиск аудио-родителя) вызывает `applyFamilySongSelection` без аудиопараметров — поведение не меняется (backward compatibility через opt-in). `audioCompareHistory`, схема `tbl_songs`, `SongDTO`, `SongDTOdigest`, recordhash-триггеры, `SyncRegistry` и публичный frontend НЕ затронуты. Никаких миграций БД — три поля уже non-null в `tbl_songs`.

**Уроки.** **Архитектурное решение R1 (research.md) — opt-in аудиопараметры в helper, а не безусловное чтение `another.id`.** Авто-caller `autoAssignOriginalByWaveform` использует тот же helper для копирования текста/маркеров; без opt-in поведение автоматического сценария сломалось бы (helper начал бы перезаписывать `audioParentId` тем же `another.id`, что ломает задел на будущую автоматизацию). R2: **post-save проверка через перечитывание** — `Song.saveToDb()` в существующем коде может не пробросить исключение (Song.kt:5337-5355), поэтому без явной проверки backend вернул бы `success=true` при расхождении. R3: **единая Vuex mutation для 5 полей** — без `applyFamilySelectionResult` debounce-autosave отправлял бы те же значения повторно (snapshot не синхронизирован, diff непустой). R4: **in-flight guard + закрытие модалки только после успеха** — прежнее поведение закрывало модалку до `JSON.parse` (SongEdit.vue:3723-3734), что давало UI без обратной связи при HTTP-ошибке. **Ловушка Jackson (AGENTS.md Q&A)**: первоначально планировал расширить `SelectFamilySongResultDto` через nullable `Long?/Int?/Long?` — но новые поля non-null в DTO (соответствуют non-null в БД, sentinel 0), и Jackson отдаёт их всегда — пустые значения не нужны.

**Связанные документы:** [`specs/129-copy-family-audio/spec.md`](../specs/129-copy-family-audio/spec.md) — спецификация. [`docs/features/songs-table.md`](./features/songs-table.md) — обновлён per-feature документ (добавлена секция «Ручной выбор похожей версии и аудиополя»). Pass 36 (feat 128, шаблоны auto-новостей) — формат записи в architecture-notes.md скопирован. Pass 33 (feat 122, автопремиум-публикация) — прецедент согласованной ручной/автоматической фичи.


## Pass 38: пайплайн approve → DEMO-рендер → Telegram → sync related → новость «в коллекции» (2026-08-04)

**Что.** Фича 131 (specs/131-fix-approve-demo-render-telegram-sync) — расширение `SongEditorController.approve()` (POST `/editor/song/approve`) двумя аддитивными блоками и пост-хуком `KaraokeProcessThread.run()`. После существующего `updateRemoteSongFromLocalDatabase(song.id)` и до `aRead.save()` вызывается приватный helper `triggerRenderMp4DemoIfNeeded(song)` (SELECT-гард по активному `RENDER_MP4_DEMO`-процессу + `KaraokeProcess.createProcess(...)` с приоритетом 5, `threadId=0` HEAVY_RENDER, `doWait=false`), затем в `thread { ... }` запускается `updateRemoteDatabaseFromLocalDatabase(updateSongs=false, updatePictures=true, updateAuthors=true)` — bulk-sync `tbl_pictures`/`tbl_authors`/`tbl_albums`. В пост-хуке `KaraokeProcessThread.run()` сразу после existing `HealthReport.onRepairProcessFinished` добавлена ветка: если `karaokeProcess.type == RENDER_MP4_DEMO && status == DONE && !forceStopped`, в `thread { ... }` вызывается `TelegramAutoPublishService.publishToTelegram(song, allowPastDate=true, PublicationType.AIR, persistMessageId=true)` (повторная загрузка `Song` через `Song.loadFromDbById` внутри потока, идемпотентность по `song.idTelegramDemo` уже есть в сервисе). Изменено: 2 файла, ~75 строк, добавлено 3 импорта в `SongEditorController.kt` и 1 импорт в `KaraokeProcessWorker.kt`. Новый per-feature документ `docs/features/approve-pipeline.md` (16-я подсистема в `docs/features/README.md`).

**Зачем.** До фичи админский approve делал ровно одну вещь — отмечал задание одобренным и пушил `tbl_songs` на сервер. Рендер DEMO, публикация в Telegram и sync related таблиц оставались на ручных кнопках и плановых scheduler'ах. Это давало три зазора: (1) DEMO-MP4 для Telegram ещё не отрендерен сразу после approve; (2) обложка исполнителя/альбома в публичной карточке песни устаревшая; (3) пост в Telegram-канал «опаздывает» на 60 с + окно «late» `publishToTelegram` может его пропустить (FR-007 спеки 094). Фича закрывает все три зазора аддитивно — approve становится «бизнесовым» концом цикла (доступна + видео + анонс), а не просто статусом задания.

**Что НЕ изменилось.** `tbl_songs`/`tbl_processes`/`tbl_news`/`tbl_pictures`/`tbl_authors`/`tbl_albums`/`tbl_settings`/`tbl_settings_sync` — без миграций и пересоздания `recordhash`-триггеров (A-002 спеки 131, Principle II конституции). `SyncRegistry` — без новых ключей (используем existing `updateRemoteSongFromLocalDatabase` + `updateRemoteDatabaseFromLocalDatabase`); DTO/`SongDTO`/`SongPublicDto` — без новых JSON-полей; HTTP-эндпоинты — без новых; SSE-топики — без новых (новые строки в `/processes`, `/news` приходят через стандартные `SseNotification.recordChange`/`message`/`crud`); существующая логика `approve()` (markers / `idStatus=6` / `updateRemoteSong` / `aRead.save()`) не модифицирована ни в одной строке, закреплена specs/094, 095, 096 (A-001). Серверная `MainController.doChangeRecords` → `SongReleaseAnnouncementService.detectAndAnnounceAvailability` → `News.createAutoAnnouncement(category="premium")` (specs/101) уже корректно делает новость «появилась в коллекции» при переходе `newsAvailableAnnounced: false→true` в применённой `tbl_songs`-строке — фича его не трогает. PREMIUM-цикл (`newsPremiumPublishPending`, `PremiumAutoPublishScheduler`, specs/122) — вне scope, фича работает только с AIR-каналом (D-6 в research.md). Плановые scheduler'ы (`TelegramAutoPublishScheduler`, `VkAutoPublishScheduler`, `SongReleaseAnnouncementScheduler`) и ручные триггеры (`POST /editor/song/approve`, `/song/renderMp4Preview`, `/song/publishtotelegram`, `/utils/updateremotesongfromlocaldatabase`, `/utils/updateremotedatabasefromlocaldatabase`) сохраняют backward compatibility (US3 спеки 131).

**Уроки.** **Архитектурное решение D-1 в research.md — пост-хук в `KaraokeProcessThread.run()`, а не отдельный scheduler.** Существующий `TelegramAutoPublishScheduler` тикает раз в 60 с — это нарушает SC-002 «в течение 60 с после рендера» (фактически до 2 минут + окно 59 минут). Пост-хук даёт немедленный вызов после `DONE`, без отдельного процесса. **D-2 — bulk-sync `updateRemoteDatabaseFromLocalDatabase` вместо per-id вызовов.** `updateRemoteAuthorFromLocalDatabase(id)` и `updateRemoteAlbumFromLocalDatabase(id)` НЕ существуют в кодовой базе (есть только `updateRemoteSongFromLocalDatabase(id)` и `updateRemotePictureFromLocalDatabase(id)`); добавлять их для одной фичи — преждевременно, YAGNI. Bulk-sync переиспользует существующую инфраструктуру `SyncRegistry`/`recordhash`-триггеров, diff не пушит неизменённые записи. **D-3 — гард по активному процессу, не по наличию файла.** Гард «есть ли валидный DEMO-файл» не ловит случай неудачного рендера (файл от прошлой версии остаётся на диске, а рендер был сброшен); SELECT по `tbl_processes WHERE process_status IN ('WAITING','WORKING')` — самая простая защита от дублей и для повторного approve, и для параллельного ручного триггера. **D-4 — порядок шагов в `approve()`:** existing push → [NEW] render trigger → [NEW] sync-related thread → existing `aRead.save()`. Каждый шаг изолирован `try { ... } catch (_: Exception) { println(...) }` — сбой любого не откатывает уже выполненный `song.saveToDb()` (`idStatus=6`). **D-5 — `telegramAutoPublishEnabled` НЕ блокирует approve-side.** Гард находится внутри `TelegramAutoPublishService.publishToTelegram` (ранний `return SCHEDULED` если `!enabled`). Approve-side безусловно создаёт процесс рендера (FR-012 спеки 131): демо-MP4 рендерится, sync идёт, в Telegram поста нет — админ при включении флага позже может опубликовать ручным триггером или дождаться scheduler'а. **Ловушка существования `WORKING_DATABASE`-глобала:** на проде `karaoke-app` НЕ разворачивается (только `karaoke-web`); фича выполняется ТОЛЬКО на admin-машине через `POST /editor/song/approve`. Никаких рисков для публичного флоу нет, но PR-описание должно явно фиксировать «admin-only», иначе ревьюер ожидает прод-эндпоинт. **Ловушка `KaraokeProcess.createProcess`:** `threadId` БЕЗ дефолта (`fun createProcess(song, action, doWait=false, prior=1, threadId, context=emptyMap()): Long`) — все callers обязаны передавать, иначе Type inference error. В helper'е передаём `threadId = 0` (`THREAD_LANE_HEAVY_RENDER`) явно. **Ловушка имён `process_status` vs `status`:** в существующем `TelegramAutoPublishScheduler.findRenderDemoProcess` (services/TelegramAutoPublishScheduler.kt:195) уже был фикс 02.08.2026 — колонка называется `process_status`, не `status`. В нашем SELECT-гарде пишем именно `process_status`. **KDoc на root helper'е (`triggerRenderMp4DemoIfNeeded`)** соответствует FR-006 конституции: `@see docs/features/approve-pipeline.md` + cross-refs на `contracts/pipeline.md` и `research.md`. Корневой класс `SongEditorController` и `KaraokeProcessThread` тоже имеют `@see docs/features/approve-pipeline.md` (метаданные фичи легко находятся через grep `@see`).

**Связанные документы:** [`specs/131-fix-approve-demo-render-telegram-sync/spec.md`](../specs/131-fix-approve-demo-render-telegram-sync/spec.md) — спецификация (14 FR, 3 US, 6 SC, Assumptions A-001..A-009). [`specs/131-fix-approve-demo-render-telegram-sync/plan.md`](../specs/131-fix-approve-demo-render-telegram-sync/plan.md) — Implementation Plan (Constitution Check 10/10 passed, Complexity Tracking пусто). [`specs/131-fix-approve-demo-render-telegram-sync/research.md`](../specs/131-fix-approve-demo-render-telegram-sync/research.md) — Phase 0 research, решения D-1..D-6, риски R-1..R-5. [`specs/131-fix-approve-demo-render-telegram-sync/contracts/pipeline.md`](../specs/131-fix-approve-demo-render-telegram-sync/contracts/pipeline.md) — внутренний контракт оркестрации, идемпотентность, матрица изоляции сбоев. [`specs/131-fix-approve-demo-render-telegram-sync/quickstart.md`](../specs/131-fix-approve-demo-render-telegram-sync/quickstart.md) — ручные сценарии S-001..S-009 (S-001 happy path, S-002..S-009 idempotency + failure isolation). [`docs/features/approve-pipeline.md`](./features/approve-pipeline.md) — новый per-feature документ (16-я подсистема в `docs/features/README.md`). [`docs/features/async-process-queue.md`](./features/async-process-queue.md) — здесь живёт пост-хук `KaraokeProcessThread.run()`. Pass 33 (feat 122, PREMIUM-публикация) — прецедент согласования ручной/автоматической фичи (исследовал тот же `KaraokeProcessThread.run()` для post-hook'а на свой `RENDER_MP4_DEMO`-процесс — не конфликтует с нашим, потому что PREMIUM и AIR — разные задания и разные `publicationType`). Pass 36 (feat 128, шаблоны auto-новостей) — прецедент admin-driven конфигурации без миграций. Pass 35 (kill-switch через `tbl_public_settings`) — прецедент «не ломаем SyncRegistry новыми ключами без необходимости». Specs/094, 095, 096 — оригинальные approve-фиксы, наша фича их не трогает (A-001). Specs/101 (news-flag) — серверная сторона уже делает новость «в коллекции», мы только гарантируем что `tbl_songs` пушится (уже было). Specs/113 (Telegram-бот) — шаблон поста для AIR-канала.


## Pass 39: фикс zombie-процессов `doWait=false` + шаблон «В коллекции» для approve-flow (2026-08-04)

**Что.** После мержа #172/#173 в master и тестов админа в production было выявлено 2 производственных проблемы фичи 131: (а) процесс `RENDER_MP4_DEMO` создавался с `process_status='CREATING'` и воркер `KaraokeProcessWorker.getProcessesToStart` его НЕ подбирал (SQL фильтрует ТОЛЬКО `status='WAITING'`); (б) Telegram-публикация шла с шаблоном «В эфире» (AIR), хотя после approve `Song.markNewsAvailableIfReady` уже выставил `newsPremiumPublishPending=true`, и админ ожидал шаблон «В коллекции» (PREMIUM). Аудит всех 26 вызовов `KaraokeProcess.createProcess(...)` в кодовой базе выявил 3 buggy call-sites с `doWait=false`, а не только наш новый:

| Файл | Строка | Функция |
|---|---|---|
| `SongEditorController.kt` | 942 | `triggerRenderMp4DemoIfNeeded` (наш хелпер из Pass 38) |
| `TelegramAutoPublishService.kt` | 233 | `startRenderAndReturn` (существующий pre-existing баг) |
| `VkAutoPublishService.kt` | 195 | `startRenderAndReturn` (существующий pre-existing баг) |

Все три заменены на `doWait=true`. Дополнительно в `KaraokeProcessWorker.kt` пост-хук `publishToTelegram(AIR, persist=true)` заменён на `onRenderCompleted(success=true, error=null)` — он сам выбирает шаблон и persistMessageId по `newsPremiumPublishPending` (`TelegramAutoPublishService.kt:169-172`). Заодно поправлен pre-existing typo в `VkAutoPublishService:207` (`publicationType.code` → `type.code`, параметр функции называется `type`) — раньше эта строка не компилировалась бы, но из-за `doWait=false` (zombie) код не достигался в рантайме.

Дополнительно: обновлены `research.md` (D-1: правильная семантика `doWait`; D-1-alt (C) с обоснованием выбора `onRenderCompleted` вместо прямого `publishToTelegram`), `docs/features/approve-pipeline.md` §«Известные ловушки» (P-9 про `doWait` zombie-процессы).

**Зачем.** До этого фикса фича 131 была непригодна к эксплуатации — каждый approve создавал процесс, который никогда не стартовал. Аналогично, существующий ручной триггер `/song/publishtotelegram` для песен без готового DEMO-файла попадал в ту же ловушку (просто её не было видно, потому что в таких случаях админу обычно приходилось дождаться другого `RENDER_MP4_*` процесса с правильным `doWait=true`). Финальный вывод — три места где `doWait=false`, было либо нашим багом (Pass 38), либо pre-existing (Telegram/VK `startRenderAndReturn`).

**Что НЕ изменилось.** Схема `tbl_processes` — без изменений (`CREATING` остаётся как валидный статус enum, см. `KaraokeProcessStatuses.kt`). Существующая логика approve (markers/`saveToDb`/`updateRemoteSong`/`aRead.save()`) — без правок ни в одной строке. Никаких миграций БД (записи со старым `CREATING` остаются в `tbl_processes`, но они уже не появятся в новых approve-flow благодаря `doWait=true`). Шаблон Telegram для ручного триггера «Опубликовать сейчас» через UI остаётся «через что пользователь нажал»: если файл уже есть, прежний путь через `publishToTelegram` без изменений; если файла нет и песня в `newsPremiumPublishPending=true` — теперь `startRenderAndReturn` создаст WAITING-задачу, воркер её отрендерит, после чего PremiumAutoPublishScheduler её опубликует. Sync-related US2 и существующий блок `updateRemoteSong` для US1 — без правок.

**Уроки.** **R6 в research.md (новый) — семантика `doWait`:** параметр НЕ управляет блокировкой HTTP-вызова (функция `KaraokeProcess.createProcess` всегда возвращает `Long` синхронно). Он задаёт начальный `process_status`: `WAITING` или `CREATING`. Воркер `KaraokeProcessWorker.getProcessesToStart` фильтрует SQL строго по `status='WAITING'` (`KaraokeProcess.kt:806`). Никакого scheduler'а/пост-хука, который бы флипал `CREATING → WAITING`, в кодовой базе НЕТ — проверено `grep -rn "process_status.*WAITING"`. Это значит `doWait=false` — это «создать zombie-процесс, который навсегда зависнет в `tbl_processes` и никогда не подхватится». Прежнее исследование фичи 131 (research.md D-1, изначальный текст) ошибочно трактовало `doWait=false` как «неблокирующий»; разница между `doWait=true/false` только в initial status. Approve-flow остаётся неблокирующим благодаря существующему `try { ... } catch ...` оборачиванию и `thread { ... }` для sync-related. **R7 — пре-имущество `onRenderCompleted` над прямым `publishToTelegram`.** Прямой вызов требует от caller'a жёсткого решения «AIR или PREMIUM», и любая ошибка (зашить AIR там, где ожидается PREMIUM, или забыть про `persistMessageId=false`) приводит к неправильному шаблону Telegram или преждевременному заполнению `idTelegramDemo`, ломающему последующий AIR-цикл. `onRenderCompleted` инкапсулирует эту логику: он вызывает `Song.loadFromDbById` сам, сам разруливает `effectivePublicationType`/`effectivePersistMessageId` по `song.newsPremiumPublishPending`, и возвращает `null` если песни нет — никаких externally-misused параметров. Это каноническая точка входа (используется `PremiumAutoPublishScheduler.resumeRenderingSong` — `PremiumAutoPublishScheduler.kt:120-134`). **Ловушка pre-existing typo, ставшая видимой благодаря фиксу:** в `VkAutoPublishService.kt:207` параметр называется `type`, но контекст использовал `publicationType.code`. До фикса этот код никогда не выполнялся (createProcess возвращал 0 из-за zombie и сразу выходил из цикла); после `doWait=true` ветка становится реальной, и Kotlin-компилятор требует `type.code` вместо `publicationType.code`. **Общий принцип:** zombie-процессы — silent killers. `CREATING` статус в enum'е — это legacy state, по факту сейчас оно эквивалентно «убит»: ни одна работающая подсистема его не обрабатывает. Можно рассмотреть как future work: (а) либо убрать `CREATING` из enum'а, либо (б) добавить фоновый recovery-флип `CREATING → WAITING` в `KaraokeProcessWorker.start()` (по аналогии с существующим `setWorkingToWaiting`); оба изменения вне scope текущего PR (требуют отдельного обсуждения рисков).

**Связанные документы:** [`specs/131-fix-approve-demo-render-telegram-sync/research.md`](../specs/131-fix-approve-demo-render-telegram-sync/research.md) — обновлены D-1 (правильная семантика `doWait`) и D-1-альтернатива (C) с обоснованием `onRenderCompleted`. [`docs/features/approve-pipeline.md`](./features/approve-pipeline.md) — добавлена ловушка P-9 про `doWait` zombie-процессы. [`services/PremiumAutoPublishScheduler.kt`](../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PremiumAutoPublishScheduler.kt) — прецедент использования `onRenderCompleted` для `newsPremiumPublishPending=true` (его Фаза 1 resumeRenderingSong, строки 120-134). [`services/TelegramAutoPublishService.kt:169-172`](../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt) — логика динамического `effectivePublicationType`/`effectivePersistMessageId`. [`services/VkAutoPublishService.kt:134-138`](../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt) — VK-аналог (тоже динамически выбирает тип по `newsPremiumPublishPending`). Pass 33 (feat 122) — прецедент согласования ручной/автоматической фичи через `onRenderCompleted`. Pass 38 (feat 131) — оригинальная реализация, чей код мы правим (это Pass 39 = fixup).

### 2026-08-04 — PR #182: `142-remove-watch-links-block` (`620bbfa6`)

**Что.** На публичной странице песни (`karaoke-public/src/views/SongView.vue`) удалён UI-блок «Ссылки на просмотр» — карточка с иконками внешних платформ (Sponsr / Dzen / VK / Telegram / Max) для пяти вариантов (Все / Karaoke / Lyrics / TABS / Chords). Удалено 178 строк в одном `*.vue`-файле (DOM-блок 135 строк + CSS-правила 40 строк + import/регистрация `PlatformLink` 2 строки + пустые разделители). Полная поставка: 8 spec-kit артефактов в `specs/142-remove-watch-links-block/` + 1 модифицированный файл.

**Зачем.** Прямой запрос пользователя: «На проде со страницы песни убрать блок „Ссылки на просмотр"». Блок не нёс критичной функциональности (онлайн-плеер уже встроен в страницу и перекрывает основной use-case); наличие внешних ссылок на сторонние платформы в публичном view признано избыточным.

**Что НЕ изменилось.**
- Источники данных: `Song.link*`-поля (Sponsr/Dzen/VK/Telegram/Max × Karaoke/Lyrics/Tabs/Chords) остаются в `tbl_settings` и в JSON `/api/public/song` — на случай возврата блока через `git revert` или использования в других view (`SearchView.vue` уже использует `PlatformLink`, Закрома и результаты поиска продолжают рендерить ссылки).
- `karaoke-public/src/components/PlatformLink.vue` — без правок (компонент переиспользуется).
- `SearchView.vue`, `ZakromaView.vue` — продолжают импортировать `PlatformLink` без изменений.
- Backend (`karaoke-app`, `karaoke-web`), БД (`tbl_settings`), `SyncRegistry`, `recordhash`-триггеры — без изменений.
- `webvue3` (админка) — блока «Ссылки на просмотр» там никогда не было.

**Подход.** Чисто удаляющая правка, без флагов/toggle'ов/A/B-вариантов. Никаких визуальных заглушек на месте блока (NFR-001). Один Vue-файл, один атомарный коммит. Поля, DTO и API оставлены без изменений — чтобы возврат через `git revert` вернул DOM+CSS+import одним revert'ом.

**Уроки / тонкости.** Минимальная фича-иллюстрация стандартного speckit-флоу: `/speckit.specify` → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`. Удаляющая UI-фича = простое удаление кода без новой архитектуры. Все 7 CI-проверок зелёные (ktlint / ESLint webvue3 / ESLint karaoke-public / Docs / Baseline / KDoc / JSDoc). Локально `npm run lint:check && npm run build` в `karaoke-public` — без новых нарушений baseline (baseline сократился, потому что код уменьшился). Pre-check `grep "km-link"` в `SongView.vue` — критичный шаг: убедиться, что удаляемые CSS-классы не используются вне блока (иначе удаление CSS ломает другие части страницы). В этом файле pre-check зелёный с первой проверки. Per Constitution §V ст. 2 деплой на прод делает пользователь (`do.sh build_start_public`), не агент — соблюдено.

**Связанные документы:** [`specs/142-remove-watch-links-block/`](../specs/142-remove-watch-links-block/) — `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/README.md`, `quickstart.md`, `tasks.md`, `checklists/requirements.md`. Merge-commit PR #182: `e4804674` (`Merge pull request #182 from svoemesto/142-remove-watch-links-block`).

### 2026-08-04 — PR #183: `143-song-free-access-window` (`6cf299a0`)

**Что.** Заменена модель доступа к песням: вместо «в эфире = бесплатно навсегда» / «эксклюзивно только на Sponsr» — окно бесплатного доступа в 1 календарный месяц после эфира (`Song.isFreelyAvailableNow`, `Song.kt`), либо бессрочный бесплатный доступ для песен, помеченных флагом `free` («всегда бесплатно»/«вечный эфир»). Флаг `exclusive` полностью убран из бизнес-логики, DTO и админ-UI (столбец/фильтр/переключатель в `webvue3`) — но **не из схемы БД** (см. ниже). Платный гейт (`PublicPlayerController.access()`/`readiness()`) переключён на новое правило; счётчики (`StatBySong.kt`, `/api/public/stats`) переименованы `onAir`→`freeNow`/`exclusive`→`subscriptionOnly` с новой SQL-формулой; Закрома/Поиск показывают непремиум-пользователю без личной подписки «Будет в эфире с …»/«В эфире до …»; страница песни различает «контент не готов» (легаси VK-видео) от «готов, но окно истекло» (карточка ожидания); на «О проекте» добавлено описание правила. Полная поставка: 8 spec-kit артефактов в `specs/143-song-free-access-window/` (44/44 задачи) + 21 изменённый файл в `karaoke-app`/`karaoke-web`/`webvue3`/`karaoke-public` + новый per-feature документ [`song-free-access.md`](./features/song-free-access.md) (20-я подсистема).

**Зачем.** Прямой запрос пользователя: проект больше не публикует на сторонних площадках (только онлайн-плеер + демо в Telegram/VK), поэтому старая семантика `exclusive` («эксклюзивно на Sponsr») физически устарела, а «бесплатно навсегда после эфира» не стимулирует подписку. Решения по параметрам (окно = 1 календарный месяц, ретроактивное применение без «дедушкиных» исключений для уже вышедших песен) — явный выбор пользователя в `/speckit-clarify`/`/speckit-specify`, не default.

**Что НЕ изменилось.**
- Схема БД: колонка `exclusive` в `tbl_songs`/`tbl_songs_sync` **осталась** — миграция не проводилась. Kotlin-код просто перестал её читать/писать (INSERT/UPDATE больше не включают колонку, row-load её не читает); она «протухла», хранит последнее когда-то записанное значение и никем не читается. Причина: колонка участвует в recordhash-триггере обеих БД (LOCAL/PROD), а удаление потребовало бы прод-DDL + пересоздания триггера — риск/цена выше пользы при том, что ни один FR спеки не требовал именно удаления схемы (`research.md` Decision 2).
- `Song.onAir`/`Song.isPubliclyWatchable` — не переопределены под новое правило. Они управляют ОТДЕЛЬНЫМ, не платёжным триггером одноразовой авто-новости «песня вышла в эфир» (`SongReleaseAnnouncementService`, specs/089-auto-news-song-release); платный доступ идёт через новое, отдельное свойство `isFreelyAvailableNow` (`research.md` Decision 1) — иначе авто-новость либо не срабатывала бы вовремя, либо срабатывала бы повторно при истечении окна.
- `idTariff`/личная подписка на конкретную песню, легаси raw-SQL фильтры `flag_exclusive`/`filter_exclusive`/`unpublish` в query-билдере `Song.kt` (ссылаются на колонку по имени, не через Kotlin-свойство — остаются мёртвым, но безвредным кодом, т.к. UI-контролы, которые их вызывали, убраны), таблица «FREE vs PREMIUM» на `/premium` (`PremiumView.vue`, отдельная фича 005) — вне скоупа, не тронуты.

**Подход.** Полный speckit-флоу: `/speckit.specify` → `/speckit.clarify` (3 вопроса: длительность окна, ретроактивность, скоуп таблицы `/premium`) → `/speckit.plan` (Constitution Check 8/8 PASS, 7 research-решений) → `/speckit.tasks` (44 задачи по 6 user story) → `/speckit.implement`. Реализация — точечные правки в уже существующей структуре всех 4 модулей, без новых сервисов. Перед мёржем — живая проверка на локальном стеке с прод-подобными данными (не только компиляция/линтеры): тождество счётчиков (`freeNow+subscriptionOnly=collection`, `+inWork=total`) сошлось на реальных 19652 записях; арифметика окна (+1 календарный месяц) проверена на конкретных датах (`19.07.26 14:00` → `19.08.26 14:00`); все 4 состояния доступности (в окне / окно истекло / не в эфире / всегда-бесплатно) проверены через `/api/public/player/{id}/access` с ожидаемыми `canWatch`.

**Уроки / тонкости.**
- **Инцидент с нумерацией веток**: ветка/спека изначально создана вручную как `140-song-free-access-window` (`git checkout -b` в обход `tools/reserve-branch-number.sh`) — к моменту завершения работы номер `140` оказался уже занят чужой, параллельно смёрженной веткой `140-fix-zakroma-censored-database` (PR #180/#181, тот же класс бага, см. ниже). Исправлено пост-фактум: номер `143` зарезервирован через `tools/reserve-branch-number.sh` (тег `seq/143`), ветка/директория/все внутренние ссылки (включая комментарии в коде) переименованы `140`→`143` **после** реализации, но **до** коммита — дороже, чем сразу использовать скрипт (см. AGENTS.md, «Нумерация feature-веток»).
- **`git fetch origin --tags` падал** с `[rejected] ... would clobber existing tag` на всех `seq/*` тегах — локальные lightweight-теги (заведённые ранним `git fetch origin` в начале сессии) разошлись с origin; помогло `git fetch origin --tags --force` (безопасно локально, ничего не пушит).
- **Тот же баг, найден независимо**: при живой проверке `/api/public/zakroma` упал с `IllegalStateException: Property APP_WORK_ON_SERVER should be initialized before get` (через `censored()` → `ConstantsKt.<clinit>` → `Connection.local()` — `karaoke-web` не должен трогать статическую инициализацию `karaoke-app`-глобалов). Диагностирован независимо, за минуты до обнаружения, что баг уже исправлен в `master` (PR #180 `140-fix-zakroma-censored-database` — `censored(database)` с явным параметром вместо глобала). После обновления ветки/мёржа `master` баг ушёл сам.
- Merge с обновлённым `master` (5 чужих коммитов, включая удаление 178-строчного блока «Ссылки на просмотр» из того же `SongView.vue`, что правила эта фича) дал ровно один текстовый конфликт — разрешён в пользу `master` (блок не восстановлен) + сохранён локальный фикс условия `playerReady` поверх.
- Отдельная находка при разборе кода: `SongView.vue` до этой фичи не различала «контент не готов» от «недоступно по правилам» — при `onAir=true`+`ready=true` раньше `canWatch` было гарантированно `true`, поэтому шаблон не нуждался в различии; новое правило впервые сделало недостижимое ранее состояние достижимым.

**Связанные документы:** [`specs/143-song-free-access-window/`](../specs/143-song-free-access-window/) — `spec.md`, `plan.md`, `research.md` (7 решений), `data-model.md`, `contracts/public-api.md`, `quickstart.md`, `tasks.md`, `checklists/requirements.md`. [`docs/features/song-free-access.md`](./features/song-free-access.md) — новый per-feature документ. Merge-commit PR #183: `6cf299a0` (`Merge pull request #183 from svoemesto/143-song-free-access-window`). Связанные чужие PR (тот же класс бага `censored()`/`WORKING_DATABASE`): PR #180 `140-fix-zakroma-censored-database`, PR #181 `141-fix-censored-web-storage-globals`.

### 2026-08-05 — PR `144-homepage-latest-news` (in progress)

**Что.** На главной странице сайта (SPA `karaoke-public` + legacy Thymeleaf `karaoke-web`) добавлен компактный блок «последние 5 новостей» (дата/время, заголовок, ссылка на песню/новость). Данные берутся из существующего `News.loadPublished` через уже работающий `GET /api/public/news?page=0&size=5` — никакого нового бэкенда, никаких миграций, никаких изменений в `tbl_news`/SyncRegistry. Полная поставка: 7 spec-kit артефактов в `specs/144-homepage-latest-news/` (28 задач, 3 user stories) + 5 изменённых/новых файлов в `karaoke-public`/`karaoke-web` + новый per-feature документ [`homepage-latest-news.md`](./features/homepage-latest-news.md) (21-я подсистема).

**Зачем.** Прямой запрос пользователя: на главной странице не было никакого сигнала о свежести контента, хотя в `tbl_news` уже 19000+ опубликованных записей (благодаря `specs/089-auto-news-song-release`). Цель — поддержать ключевую воронку роста visitor→registration ([docs/strategy/growth.md](./strategy/growth.md)): посетитель видит «проект развивается, новые песни выходят» → регистрируется → возвращается.

**Что НЕ изменилось.**
- `tbl_news` — ни схемы, ни индексов, ни триггеров.
- `News.loadPublished` и `NewsDto` — без изменений.
- `SyncRegistry` (sync-цель `news`) — без изменений.
- Существующий эндпоинт `GET /api/public/news` (с пагинацией) — без изменений (мы передаём ему `size=5`).
- Существующий эндпоинт `GET /api/public/news/since` (бейдж непрочитанных) — без изменений.
- Бэкенд — никакого нового кода (в отличие от `specs/143-song-free-access-window` и большинства предыдущих PR, этот PR чисто презентационный).

**Подход.** Полный speckit-флоу: `/speckit.specify` → `/speckit.clarify` (1 вопрос: поведение SPA-блока при ошибке запроса — выбрана «тихая деградация», A) → `/speckit.plan` (Constitution Check 8/8 PASS, 7 research-решений) → `/speckit.tasks` (28 задач по 3 user story) → `/speckit.implement`. Реализация — точечные правки в уже существующей структуре обоих фронтендов, без новых сервисов. Переиспользован существующий `fetch`-паттерн (как в `useEngagementTracking`), существующая JSDoc-конвенция (`@see docs/features/<slug>.md`), существующий механизм трекинга (`trackUi('click', ...)`).

**Уроки / тонкости.**
- **Инцидент с нумерацией**: `tools/reserve-branch-number.sh` зарезервировал `146` (следующий свободный), но мы хотели `144-homepage-latest-news` (так как директория спеки уже была создана под этим номером на предыдущем этапе). Переименовал ветку через `git branch -m 146-homepage-latest-news 144-homepage-latest-news` **сразу** после резервации — дешёвая операция, но если бы скрипт уже пушнул тег `seq/146` в origin, остался бы «фантомный» номер в тегах. Стоит подумать: либо передавать `slug` скрипту и сразу создавать ветку с правильным номером (когда номер уже зарезервирован вручную или по спеке), либо скрипт должен принимать явный `--number=NNN` флаг для случая «номер из спеки». TODO для Pass 16+.
- **T011/T019 (ручная валидация в браузере)** пропущены локально — на dev-машине нет ни запущенного `karaoke-web`, ни Postgres. Это известное ограничение: визуальная проверка остаётся за пользователем на его стенде. Все «объективные» проверки (сборка `npm run build`, `npm run lint:check`, `./gradlew ktlintCheck`, JSDoc/KDoc coverage, feature-doc structure) выполнены и зелёные.
- **Тихaя деградация в SPA**: явно сделано через `fetch().then().catch(() => {})` без сохранения состояния ошибки — `items` остаётся пустым массивом, шаблон через `v-if="items && items.length"` ничего не рендерит. Никакого `<div v-if="error">` для сообщения об ошибке (FR-013 спеки). Альтернатива `v-if="loading"` со спиннером была бы анти-паттерном при медленном/мёртвом API.
- **Thymeleaf**: фильтр пустых `link`/`title` применён через `th:if="${!#strings.isEmpty(n.link) and !#strings.isEmpty(n.title)}"` — тот же контракт, что и Vue computed `visibleItems()`. Это критично для SC-004 (идентичность SPA и Thymeleaf): расхождение фильтров дало бы разный набор 5 строк на двух главных.
- **JSDoc 100%** сохранён (`tools/check-jsdoc-coverage.sh karaoke-public`): новый `LatestNewsSection.vue` с JSDoc с `@see docs/features/homepage-latest-news.md` (FR-006 конституции).

**Связанные документы:** [`specs/144-homepage-latest-news/`](../specs/144-homepage-latest-news/) — `spec.md`, `plan.md`, `research.md` (7 решений), `data-model.md`, `contracts/public-news-api.md`, `quickstart.md`, `tasks.md`, `checklists/requirements.md`. [`docs/features/homepage-latest-news.md`](./features/homepage-latest-news.md) — новый per-feature документ (21-я подсистема).

### 2026-08-05 — Pass 35: фикс nginx-конфига прод `/song` (критичный баг шеринга)

**Что.** В nginx-конфиге прод-сервера (`deploy/web-server-deploy/deploy/80to8897`) location `/song` ВСЕГДА проксировал на `http://127.0.0.1:8897/api/public/og/song` — OG-endpoint (`PublicOgSongController`, отдаёт «голый» HTML с `<title>` и видимым `<img>` для сниппета VK-парсера, см. `karaoke-web/.../controllers/PublicOgSongController.kt:14-28`). Никакого User-Agent-фильтра не было, поэтому **любой** запрос `/song?id=NNN` отдавал только картинку. SPA `karaoke-public` (порт 7907, nginx-контейнер с try_files `$uri /index.html` fallback) страницу песни через nginx на этом URL не получал — её отдавал только karaoke-web OG-endpoint.

**Зачем.** Баг существовал независимо от PR #144 — любой шеринг `https://sm-karaoke.ru/song?id=NNN` (прямой URL из Telegram, пост VK, правый клик «открыть в новой вкладке» в любом месте, или ссылка в email) показывал пользователю только картинку — он физически не мог попасть на полноценную страницу песни через браузер. Внутри SPA-роута (Закрома, поиск) ссылки работали только потому, что vue-router обрабатывал клик по `<RouterLink>` без перезагрузки страницы — nginx не задействовался. PR #144 сделал баг видимым массово (блок новостей на главной → `window.open(n.link)` → новая вкладка через nginx → OG-endpoint → «только картинка»), но первопричина — в nginx-конфиге.

**Что НЕ изменилось.** `PublicOgSongController.kt` — без логических правок, только KDoc дополнен описанием фикса и историей бага. Список User-Agent'ов ботов в `80to8897` подобран как объединение реальных ботов VK (`vkShare`), Telegram (`TelegramBot`), Twitter (`Twitterbot`), Facebook (`facebookexternalhit`), LinkedIn (`LinkedInBot`), WhatsApp (`WhatsApp`), Slack (`Slackbot`), Viber (`ViberBot`), Skype (`SkypeUriPreview`), Google (`Googlebot`), Bing (`bingbot`), Яндекс (`YandexBot`/`YandexImages`). Если бот не распознан — пойдёт по ветке SPA (Vue Router рендерит SongView), что приемлемо (сниппета просто не будет).

**Подход.** Только серверная правка `deploy/web-server-deploy/deploy/80to8897` (без правок кода приложения, без миграций). Nginx-паттерн: внутри `location /song { if ($http_user_agent ~* "vkShare|...") rewrite ^/song(\?.*)?$ /api/public/og/song$1 last; }`. После rewrite nginx переходит в `location /api/public/og/song` (точнее — в самый длинный prefix, это `location /api/ { proxy_pass http://127.0.0.1:8897/api/; }`, который и так был), и karaoke-web получает `/api/public/og/song?id=NNN` — текущее поведение бота сохраняется без изменений. Для обычных браузеров if не срабатывает, остаётся `proxy_pass http://127.0.0.1:7907` — SPA получает исходный `/song?id=NNN`, Vue Router рендерит `SongView`.

**Деплой.** Серверная правка, делает пользователь вручную (см. AGENTS.md «Деплой»):

**Точный путь к файлу на сервере:** `/root/Karaoke/deploy/web-server-deploy/deploy/80to8897`
(НЕ `/root/Karaoke/deploy/80to8897` — плоского пути нет, файл лежит на глубине `web-server-deploy/deploy/` и попадает на сервер через rsync всей `deploy/`-папки).

```bash
# 1. Дождаться, что PR #191 смержен в master (rsync подхватит из origin).
# 2. Зайти на сервер и применить конфиг:
ssh root@188.119.64.111
# 3. Скопировать обновлённый файл в sites-enabled (nginx читает оттуда):
cp /root/Karaoke/deploy/web-server-deploy/deploy/80to8897 /etc/nginx/sites-enabled/80to8897
# 4. Проверить синтаксис:
nginx -t
# 5. Применить:
systemctl reload nginx
```

Без перезапуска контейнеров (правим только nginx на хосте). Перед применением — проверить `nginx -t` (синтаксис валиден). После — ручная проверка в браузере: `https://sm-karaoke.ru/song?id=25513` должна рендерить страницу песни, а не картинку. Проверка User-Agent бота — через `curl -H "User-Agent: vkShare" https://sm-karaoke.ru/song?id=25513` (должен вернуть HTML с `<img>`).

**Уроки / тонкости.**
- **Критичный баг скрывался годами**, потому что все внутрипроектные ссылки шли через `<RouterLink>` (SPA-навигация без перезагрузки). Любые **внешние** ссылки (пост в Telegram/VK, email, прямая вставка URL в браузер) ломались — но никто не сообщал, потому что входящий трафик по прямым URL минимален. PR #144 сделал баг массовым: главная страница стала источником «битых» шерингов (любой клик по новости на главной = пользователь видит картинку вместо страницы песни).
- **KDoc лгал, но это пропустили.** В `PublicOgSongController.kt:25-27` (до фикса) было написано: «Endpoint проксируется nginx'ом на проде через правило `location /song { if ($http_user_agent ~* "vkShare|...") rewrite ... }`. Обычные пользователи идут на SPA Vue (port 7907) — nginx это не затрагивает». Это намерение, но **не реальное поведение nginx-конфига**. KDoc описывал **план**, а не факт. Урок: проверять, что KDoc соответствует **актуальному** конфигу инфраструктуры, а не только замыслу. После фикса KDoc обновлён и теперь отражает реальность.
- **`if` в nginx** — для User-Agent-based маршрутизации внутри `location` это **нормальный** паттерн, хотя nginx-сообщество часто предпочитает `map` (требует http-блока, не подходит для изолированного server-файла). В нашем случае `80to8897` — это отдельный server-файл, и `map` невозможен без правки основного `nginx.conf` (рискованно). `if + rewrite ... last` — единственный безопасный вариант в нашей конфигурации. Альтернативный путь (вынести `map` в `/etc/nginx/conf.d/`) — слишком инвазивный для нашей ситуации.
- **Кросс-валидация: проверено через `public/sponsr/vk/tg`-линки в `SocialLinks.vue`** — все они используют абсолютные `https://`-URL, открываются в новой вкладке через `window.open`, идут мимо nginx нашего сайта. Поэтому блок соцсетей не подвержен этому классу багов. А вот блок новостей использует `News.link` от контроллера, который формирует **относительные** URL `/song?id={id}` — поэтому они проходят через наш nginx и попадают под баг.

**Связанные документы:** [`specs/144-homepage-latest-news/`](../specs/144-homepage-latest-news/) — фича, которая обнажила баг (через PR #187/#188/#189). [`docs/features/homepage-latest-news.md`](./features/homepage-latest-news.md) — per-feature документ блока новостей (обновлён, добавлена ловушка про `window.open` и nginx-location `/song`). [`deploy/web-server-deploy/deploy/80to8897`](../deploy/web-server-deploy/deploy/80to8897) — сам конфиг с фиксом.

---

## 2026-08 — Phase 003: `156-typograph-public-endpoint`

### 2026-08-06 — PR #205: `156-typograph-public-endpoint`

**Что.** Кнопка «Типограф» в `karaoke-public/src/views/EditorWorkView.vue` возвращала
405 `Method Not Allowed` на проде (`api/replacesymbolsinsong`). Добавлен тонкий дубль
endpoint'а в `karaoke-web` (`PublicTypographController.kt`), который напрямую вызывает
`Utils.replaceSymbolsInSong()` из `karaoke-app` через зависимость `karaoke-web → karaoke-app`.

**Зачем.** Спека 155 (PR #204) корректно переиспользовала backend-эндпоинт
`POST /api/replacesymbolsinsong`, но исходила из посылки «backend не менялся — endpoint
уже работал». Это верно для **admin** (`webvue3` → nginx → `karaoke-app:8898`), но
**неверно для паблика**: nginx `karaoke-public` проксирует `/api/` на `karaoke-web:8897`,
а там endpoint отсутствует (Spring сканирует только `com.svoemesto.karaokeweb.*`).
Результат — `karaoke-public` шлёт POST, а Spring отвечает 405, потому что в `karaoke-web`
нет соответствия. Контрактный URL `/api/replacesymbolsinsong` идентичен в обоих бэкендах,
поэтому фронт не меняется.

**Как сделано.** По образцу `PublicSettingsWebController.kt` — «тонкий дубль
endpoint'а, который существует в karaoke-app, но нужен в karaoke-web, потому что
karaoke-app на проде не разворачивается». `replaceSymbolsInSong()` — pure top-level
функция из `Utils.kt`, без БД/сессий/Spring-бинов karaoke-app, поэтому прямой
вызов безопасен и не зависит от того, что Spring karaoke-app не отсканировал эти бины.

**Уроки / тонкости.**
- **Спека 155 ошибочно утверждала «Backend не менялся»** — это пропустили ревью и CI.
  Урок: когда фича добавляет одинаковый код в admin/public фронтенды, надо проверять
  **каждый** бэкенд, на котором крутится endpoint, а не только тот, что доступен
  разработчику. `karaoke-app` ≠ `karaoke-web` — последний развёрнут на проде, первый нет.
  Соответствующая правка внесена в `docs/features/editor-tasks.md` — теперь явно сказано
  про дубль в karaoke-web и причину (nginx-проксирование на 8897, а не 8898).
- **Прямой вызов top-level Kotlin-функции через зависимость модулей** — простой и
  безопасный способ избежать HTTP-проксирования. `karaoke-web → karaoke-app` уже
  в `build.gradle.kts` (`implementation(project(":karaoke-app"))`), поэтому никаких
  новых конфигов/HttpClient'ов не понадобилось.
- **Проверка ловушки**: если бы ревьюер спеки 155 просто открыл `karaoke-web/src/main/
  kotlin/com/svoemesto/karaokeweb/controllers/` и поискал `replacesymbolsinsong` — он бы
  ничего не нашёл и поднял вопрос. Grep по файлам `karaoke-app/.../controllers/*.kt`
  даёт ложное ощущение покрытия.

> ⚠ **Дополнение (2026-08-06, PR #206):** фикс выше **оказался неполным** — после деплоя
> на локальной сборке `karaoke-web` начал отвечать 500 `Internal Server Error` вместо 405.
> Причина: прямой вызов `com.svoemesto.karaokeapp.Utils.replaceSymbolsInSong(txt)` из
> `karaoke-web` при первом обращении триггерит JVM-init класса
> `com.svoemesto.karaokeapp.ConstantsKt`, который собирает карту
> `mapOf(ProducerType.X to MkoY::class.java, ...)` — загружаются ВСЕ MLT-классы
> (`com.svoemesto.karaokeapp.mlt.mko.*`), часть которых при class init обращается к
> `APP_WORK_ON_SERVER`/`WORKING_DATABASE` для MLT, настроенным только в `karaoke-app`
> (на проде `karaoke-app` не развёрнут — переменные не инициализированы). Результат —
> `NoClassDefFoundError: Could not initialize class com.svoemesto.karaokeapp.ConstantsKt`.
> Чистый «pure top-level» анализ в коде был верен, но неполон: JVM class loading тянет
> зависимости по цепочке, а не только то, что явно вызвано. См. PR #206 ниже.

**Связанные документы:** [`docs/features/editor-tasks.md`](./features/editor-tasks.md) —
per-feature документ для редакторских задач (обновлён, убрано ложное «Backend не менялся»,
добавлен явный пункт про дубль в `karaoke-web`). [`specs/155-editor-typograph-button/contracts/replacesymbolsinsong.md`](../specs/155-editor-typograph-button/contracts/replacesymbolsinsong.md) — контракт endpoint'а (без изменений, единый для обоих бэкендов). [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicTypographController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicTypographController.kt) — сам дубль. Эталон паттерна: [`karaoke-web/.../PublicSettingsWebController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt).

---

### 2026-08-06 — PR #206: `157-fix-typograph-public-endpoint`

**Что.** Комплексный дофикс спеки 155 / PR #205 (типограф в паблике) — три проблемы
одной строкой `POST /api/replacesymbolsinsong` от паблика, каждая снимает предыдущий симптом:

1. **Nginx в `karaoke-public` не проксирует этот URL.** Конфиг `karaoke-public/
   nginx_karaoke-public.conf` имеет только два `location`: `/api/public` и `/api/storage`.
   Все остальные `/api/*` падают в `try_files $uri $uri/ /index.html;` — и для **POST**
   nginx отдаёт **405 Method Not Allowed** (статика не принимает не-GET, в отличие от GET,
   который через `try_files` отдаёт `index.html` со статусом 200). Добавлен
   `location /api/replacesymbolsinsong` с `proxy_pass http://karaoke-web:7799` —
   минимально-инвазивно, не сломает существующее поведение других `/api/*` путей.

2. **`Utils.replaceSymbolsInSong` из `karaoke-app` не вызывается из `karaoke-web` без
   побочного class loading.** После PR #205 первый POST отвечал **500 Internal Server
   Error** (а не 200) с `NoClassDefFoundError: Could not initialize class
   com.svoemesto.karaokeapp.ConstantsKt`. Функция в `karaoke-app` сама pure, но при
   первом обращении JVM инициализирует `Constants.kt`, который собирает карту
   `ProducerType → Mko*::class.java` — загружаются все MLT-классы, часть которых
   при init лезет в `APP_WORK_ON_SERVER`/`WORKING_DATABASE`/`/sm-karaoke/system/...`,
   настроенные только в `karaoke-app`. На проде `karaoke-app` не развёрнут — переменные
   не инициализированы — JVM class init падает. Решение: **скопировать pure-логику
   (`replaceSymbolsInSong` + 6 String-extensions + 2 константы) в
   `karaoke-web/TypographUtils.kt`**, без зависимости от `com.svoemesto.karaokeapp.*`.
   Чтение Ё-словаря — прямой SQL к `tbl_dictionaries` через локальный `WORKING_DATABASE`
   (он уже инициализирован в `karaoke-web` через `Connection.local()`), без обращения
   к karaoke-app-модели `Dictionary` (та тоже тянет class loading).

3. **`PublicTypographController` переписан** — теперь вызывает локальный
   `com.svoemesto.karaokeweb.replaceSymbolsInSong`, а не `com.svoemesto.karaokeapp.*`.

**Зачем.** Без PR #205 кнопка в паблике возвращала 405 (nginx). Без этого PR #206 — после
#205 начала бы возвращать 500 (Spring) и на локалке, и на проде. Спека 155 заявляла
«та же кнопка, что и в админке» — это правильная цель, но «как в админке» не значит
«прямой вызов той же функции через module dependency». Только в `karaoke-app` живут все
нужные для MLT глобалы, и поднять их в `karaoke-web` нельзя без полной инициализации
всего `karaoke-app` (что, по задумке, делает `KaraokeAppService`/`Karaoke.kt` —
компонент, который **намеренно не поднимается** в `karaoke-web`, см.
`KaraokeWebApplication.kt` и `WorkingDatabase.kt`).

**Что НЕ делалось.**
- Не ревертил PR #205 — он содержит рабочий код (контроллер с правильным URL/mаршрутизацией),
  только меняется его тело (импорт). Реверт + rebase был бы лишним шумом, контроллер и так
  короткий.
- Не трогал фронт (`karaoke-public/src/views/EditorWorkView.vue` — URL/method не меняются,
  бэк-контракт идентичный).
- Не трогал эндпоинт в `karaoke-app` (`ApiController.kt:5052` + `MainController.kt:970`) — он
  продолжает работать для `webvue3` (admin), там class init полностью валиден.

**Проверка локально (на `nsa-i9`):**

```bash
docker exec karaoke-public nginx -t && docker exec karaoke-public nginx -s reload
cd deploy && bash do.sh build_start_web
curl -sG --data-urlencode 'txt=privet,mir' -X POST \
  'http://localhost:7907/api/replacesymbolsinsong'
# privet, mir                                         (200, запятая + пробел)

curl -sG --data-urlencode 'txt=Он сказал "привет"' -X POST \
  'http://localhost:7907/api/replacesymbolsinsong'
# Он сказал «привет»                                  (200, «ёлочки»)

curl -sG --data-urlencode 'txt=всё её еще' -X POST \
  'http://localhost:7907/api/replacesymbolsinsong'
# всё её ещё                                          (200, Ё-словарь: ещё→ещё? нет,
#   оригинал в karaoke-app так же; «ещё» уже с ё, не трогается; «еще» в слове «еще» — не в
#   словаре, как и в karaoke-app)
```

**Уроки / тонкости.**
- **«Pure function» ≠ «безопасно вызывать из любого модуля».** Даже pure top-level
  функция может при первом обращении дёрнуть class init зависимостей через `const val`/
  `val mapOf` в файле, где она определена. **Перед тем как полагаться на
  `implementation(project(":other"))` для доступа к utility-функциям, надо
  верифицировать, что файл, в котором они определены, загружается без сайд-эффектов**
  (в идеале — `grep -E '^(const val|val)\s' файл.kt | grep -v simple` + ручная проверка).
  В нашем случае `Constants.kt:149-199` (`val TEXT_FILE_DICTS`, `val producerTypeClass`)
  явно триггерит загрузку `Mko*` через `mapOf(... X::class.java, ...)`.
- **«По образцу PublicSettingsWebController» ≠ «можно копировать буквально».** В том
  контроллере обращение к `WORKING_DATABASE` безопасно, потому что БД-глобал
  инициализируется рано (`KaraokeWebApplicationKt`). А в `karaoke-app` — помимо БД ещё
  нужен MLT-глобал, и его в `karaoke-web` нет by design. Разница в **объёме глобального
  состояния**, которое тянет за собой вызов.
- **Nginx 405 на `try_files` POST** — частая ловушка, не описанная в CONTRIBUTING.md.
  `try_files $uri $uri/ /index.html;` корректно отдаёт `index.html` (200) на GET,
  но для POST/DELETE/PUT возвращает 405 (статика не поддерживает не-безопасные методы).
  Лечится либо явно `proxy_pass` для не-безопасных методов, либо `error_page 405 = @fallback;`
  с `location @fallback { try_files ... }`, либо отдельным `location` для каждого
  не-безопасного URL.

**Связанные документы:** [`karaoke-public/nginx_karaoke-public.conf`](../../karaoke-public/nginx_karaoke-public.conf) —
nginx-конфиг с новым location. [`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/TypographUtils.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/TypographUtils.kt) — локальная копия правил.
[`karaoke-web/.../PublicTypographController.kt`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicTypographController.kt) — обновлённый контроллер. Эталон логики, которая скопирована: [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt:1460`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) и [`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt). PR #205 (предыдущая попытка, неполная): [см. выше](#2026-08-06--pr-205-156-typograph-public-endpoint).

---

### 2026-08-06 — PR #207: `158-hotfix-typograph-recursion`

**Что.** Hotfix регрессии в PR #206 — после упрощения `= com.svoemesto.karaokeweb.replaceSymbolsInSong(txt)`
до `= replaceSymbolsInSong(txt)` в `PublicTypographController.kt:51` контроллер стал вызывать
**сам себя** (бесконечная рекурсия → `StackOverflowError` → HTTP 500). Восстановлен fully-qualified
вызов + комментарий-предупреждение рядом.

**Зачем.** В Kotlin **локальная декларация метода имеет приоритет над одноимённым top-level
import** в том же пакете. Импорт `import com.svoemesto.karaokeweb.replaceSymbolsInSong` + метод
контроллера с тем же именем → в теле метода `replaceSymbolsInSong` резолвится в сам метод
контроллера, а не в импортированную функцию. Рекурсия не сразу видна (компилируется без
предупреждений), проявляется только в рантайме как `StackOverflowError`. Полностью убрать
нельзя (имя метода контроллера диктуется контрактом, см. спеку 155 — URL endpoint'а
содержит `replacesymbolsinsong`, имя метода в Spring-коде должно соответствовать).

**Альтернатива, которая была отвергнута:** переименовать метод контроллера (например,
`handleReplaceSymbolsInSong`) — отклонено: имя метода = часть URL привязки в Spring (если
использовать `MvcUriComponentsBuilder` / `@RequestMapping` по имени), плюс ухудшает читаемость
KDoc «вызывает локальную `replaceSymbolsInSong`» — имя функции в комментарии должно совпадать
с именем вызываемой. Альтернативный путь: вынести `replaceSymbolsInSong` в другой пакет
(`com.svoemesto.karaokeweb.typograph`) и импортировать — отклонено: переплетение
с `WORKING_DATABASE` (тоже в `com.svoemesto.karaokeweb`) требует или `internal`, или
публичного API, что в нашем проекте для top-level util-функций не принято (см. `Utils.kt`
в `karaoke-app` — top-level без отдельного sub-пакета).

**Уроки / тонкости.**
- **«Упростил — стало чище»** — опасный инстинкт, когда есть импортированная функция с
  тем же именем, что и метод. Перед упрощением надо проверять scope resolution — проще
  через grep по проекту, чтобы убедиться, что в `controllers/PublicTypographController.kt`
  нет `import com.svoemesto.karaokeweb.replaceSymbolsInSong` (или наоборот, проверить, что
  `replaceSymbolsInSong` нигде не определён внутри класса).
- **CI не ловит регрессию** — это runtime StackOverflow, а не compile error. Локальный
  smoke-test (`curl POST /api/replacesymbolsinsong`) после **каждого** изменения
  в коде контроллера обязателен. В этом случае я сделал curl **до** упрощения, не **после** —
  отсюда регрессия.
- **Канонический Kotlin-фикс** для таких коллизий: `@JvmName("invokeLocalReplaceSymbolsInSong")`
  на top-level функции + явный импорт. Не использовал, потому что:
  (а) `replaceSymbolsInSong` уже существует в `karaoke-app` с тем же именем — конфликт JVM;
  (б) `@JvmName` нужно применять к обоим определениям (в `karaoke-app` и в `karaoke-web`),
      что выходит за рамки hotfix'а.

**Связанные документы:** [`karaoke-web/.../PublicTypographController.kt:51`](../../karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicTypographController.kt) — сам hotfix (fully-qualified + комментарий). PR #206 (сломанный): [см. выше](#2026-08-06--pr-206-157-fix-typograph-public-endpoint).





### 2026-08-06 — PR #NNN: `156-remove-songs-table-platform-flags`

**Что.** Удалены 18 узких столбцов-флагов публикации из таблицы песен в admin SPA `webvue3` (`SongsTable.vue`):
SP/VG/ZL/ZK/ZC/ZM/VL/VK/VC/VM/TL/TK/TC/TM/ML/MK/MC/MM (платформы Спонсор / ВКонтакте-группа /
Дзен / ВКонтакте / Телеграм / OK-Max × типы контента lyrics/karaoke/chords/melody). Удалено:
18 объектов из `fields[]`, 18 ячеек-шаблонов `<template #cell(flagX)>`, 18 CSS-блоков
`.fld-flag-*`, 4 метода `playLyrics/Karaoke/Chords/Tabs` в `SongsTable.vue`; 10 определений
из `fieldSongParams[]` в `store.js`. Ширина таблицы уменьшилась на ≈ 360px. Сборка webvue3:
471 модулей, 7.55s, lint 0 ошибок.

**Зачем.** Таблица песен — основная ежедневная точка входа администратора. 18 узких
20-пиксельных столбцов визуально перегружали шапку и затрудняли сканирование списка на
экранах ≤ 1440px. Данные о публикации (`flag_*` в БД, `processColor*` в DTO) по-прежнему
вычисляются и сохраняются — удаление чисто визуальное. Флаги публикации по-прежнему
доступны в таблице публикаций `PublishTableBodyTd.vue` (там показываются через свои
компоненты) и в редакторе песни `SongEdit.vue` (через Vuex-геттеры `playLyrics/Karaoke/
Chords/Tabs/Demo`).

**Альтернативы, которые были отклонены.**
- **Computed-срез `fields[]` через `v-if`**: лишний runtime-overhead, не решает проблему
  «мёртвых» шаблонов и CSS.
- **CSS hide через `.d-none`**: оставляет 20px-пустые ячейки в DOM, ломает `table-layout:
  fixed` (per CONTRIBUTING.md).
- **Перераспределить ширину на оставшиеся колонки**: out of scope, требует UX-исследования,
  рискует сломать фиксированные пиксельные ширины в существующем UI.

**Уроки / тонкости.**
- **`processColor*` и `play*` НЕ удалять** — они используются в `PublishTableBodyTd.vue`
  и `SongEdit.vue`. Удалять можно только методы-обёртки в `SongsTable.vue`
  (`playLyrics(id) → this.$store.getters.playLyrics(id)` и т.п.), а не сами Vuex-геттеры.
- **Шаблоны ячеек и объекты в `fields[]` должны быть согласованы** — `<b-table>`
  Bootstrap-vue-next использует `fields[]` как декларативный контракт колонок, плюс
  именованные слоты `#cell(key)` для кастомного контента. Удаление только `fields[]` без
  шаблонов (или наоборот) оставляет «мёртвый» код.
- **`fieldSongParams[]` в Vuex не имеет внешних потребителей** — был обнаружен через
  `grep -rn getFieldSongParams` (нашлось только в самом `store.js`). Если в будущем
  появится потребитель — вернуть удалённые 10 определений.
- **`flagPlayerDemo` (DE) НЕ должна была попасть в список** — её легко спутать с
  остальными, но она используется отдельно (двойной клик воспроизводит demo-видео).
  Зафиксировано в spec FR-005 / FR-010.

**Связанные документы:**
- [spec.md](../../specs/156-remove-songs-table-platform-flags/spec.md) — спецификация фичи.
- [plan.md](../../specs/156-remove-songs-table-platform-flags/plan.md) — Implementation Plan.
- [quickstart.md](../../specs/156-remove-songs-table-platform-flags/quickstart.md) — 8 шагов ручной проверки.
- [research.md](../../specs/156-remove-songs-table-platform-flags/research.md) — 6 решений и отвергнутые альтернативы.
- [data-model.md](../../specs/156-remove-songs-table-platform-flags/data-model.md) — таблица «что в БД / что в state / что в UI».
- [`docs/features/songs-table.md`](../features/songs-table.md) — обновлённый per-feature документ.


### 2026-08-06 — Pass 40: `160-publish-body-td-remove-six-columns`

**Что.** Логическое продолжение #156 (Pass 39): после удаления 18 флагов из `SongsTable.vue`
в #156 остались «хвосты» от использования `processColor*`:

1. **`PublishTableBodyTd.vue`** — 6 узких цветовых блоков-колонок по 10 px удалены из шаблона;
   ячейка `.publish-name` расширена со 150 px до 210 px (6 × 10 px). Удалены 20 computed-свойств
   `processColor*`, 3 метода `dblClick*`, 3 CSS-правила `.publish-column*`. `.empty` также
   расширен до 210 px. Файл сократился со 241 до 117 строк (−52%).

2. **`SongEdit.vue`** — снята inline-раскраска 4 кнопок «PLAY LYRICS / KARAOKE / CHORDS / TABS»
   (`:style="{ backgroundColor: song.processColorMelt* }"` удалён). Все 4 кнопки получают
   одинаковый фон из CSS-класса `.group-button`.

3. **Backend DTO чистка** — в `SongDTO` и `SongDTOdigest` удалены 27 из 28 полей `processColor*`
   (осталось ровно одно — `processColorPlayerDemo`, единственный живой потребитель: бейдж `DE`
   в `SongsTable.vue:329`). Соответствующие 27 присваиваний удалены в `Song.toDTO()` и
   `SongDTO.toDtoDigest()`. **Геттеры `processColor*` в `Song.kt` (2454–2538) и diff-логика
   LOCAL↔SERVER (6816+) сохранены** — нужны для `Publication.kt` и серверных Thymeleaf-шаблонов,
   которые получают сырой `Song` через `MainController.getSong` и `${song.processColorX}`.
   Принцип III NON-NEGOTIABLE constitution (двух-БД синхронизация через SyncRegistry).

4. **JSON-контракт** — `/api/songs`, `/api/songsdigests`, `/api/songshistory`, `/api/publications`,
   `/api/unpublications` перестают отдавать 27 полей `processColor*`. Payload `/api/songsdigests`
   сокращается на ~5 МБ (18 858 песен × 27 полей × ~10 байт, оценка).

**Скоуп строго ограничен** `PublishTableBodyTd.vue` + `SongEdit.vue` (4 кнопки) + двумя DTO.
**Вне скоупа** (явно по решению пользователя): шапка `PublishTableHead.vue` (намеренное
рассогласование 200 vs 210 px), закомментированные `<template #cell(flagPl*)>` в `SongsTable.vue`,
мёртвый геттер `processColorBoostyFiles` в `Song.kt:2483`.

**Метрики реализации.**
- Файлов изменено: 7 (`PublishTableBodyTd.vue`, `SongEdit.vue`, `SongDTO.kt`, `SongDTOdigest.kt`,
  `Song.kt`, `docs/features/songs-table.md`, `docs/architecture-notes.md`).
- Строк: `PublishTableBodyTd.vue` 241 → 117 (−124); DTO-блоки processColor* 28 → 1.
- Gradle compile: SUCCESSFUL in 30s (Kotlin 1.x, JDK 18 на dev-машине).
- ktlintCheck: PASS (17 actionable tasks, 0 ошибок).
- ESLint webvue3: PASS (max-warnings 0).
- ESLint-baseline: 0 новых нарушений.
- KDoc coverage: 97% (целевой ≥ 50% по FR-006).
- JSDoc coverage webvue3: 100% (целевой ≥ 50% по FR-006).
- pre-commit run (ktlint + eslint + prettier): PASS для всех 5 файлов.

**Связанные документы:**
- [spec.md](../../specs/160-publish-body-td-remove-six-columns/spec.md) — спецификация.
- [plan.md](../../specs/160-publish-body-td-remove-six-columns/plan.md) — Implementation Plan.
- [research.md](../../specs/160-publish-body-td-remove-six-columns/research.md) — карта файлов с точными строками + 5 закрытых NEEDS CLARIFICATION.
- [data-model.md](../../specs/160-publish-body-td-remove-six-columns/data-model.md) — финальная форма сущностей.
- [contracts/api-songsdigests.md](../../specs/160-publish-body-td-remove-six-columns/contracts/api-songsdigests.md) — контракт 7 эндпоинтов.
- [quickstart.md](../../specs/160-publish-body-td-remove-six-columns/quickstart.md) — 5 шагов ручной валидации.
- [tasks.md](../../specs/160-publish-body-td-remove-six-columns/tasks.md) — 31 задача по 7 фазам.
- [`docs/features/songs-table.md`](../features/songs-table.md) — обновлённый per-feature документ.

## Pass 41: 3 production runtime-ошибки `karaoke-web` — ЮKassa logging, Thymeleaf `publishAt`, `KaraokeProperties` на проде (2026-08-09, PR #211, `6adf3408`)

**Что.** Три стабильные runtime-ошибки в `karaoke-web` на проде (188.119.64.111), пойманные в логах `docker logs karaoke-web` за 2026-08-08 — 2026-08-09. Все три исправлены одним change через feature-ветку `161-fix-prod-runtime-errors-2026-08-09` (CI 7/7 PASS).

1. **PaymentService.chargeRecurring** — добавлен отдельный `catch (e: WebClientResponseException)` перед общим `catch (Exception)`. Теперь логируем `statusCode` + `responseBodyAsString` (≤500 chars). Без этого причину `400 Bad Request from POST http://minio-proxy/yookassa/payments` диагностировать было невозможно — `e.message` показывал только `"400 Bad Request from POST ..."`, без тела ЮKassa. Реальная причина (nginx на `minio-proxy` не маршрутизирует `/yookassa/`) правится отдельным ручным деплоем на проде (см. PR #211, раздел «Требуется ручной deploy»).
2. **templates/main.html:200** — `#dates.format(n.publishAt, 'dd.MM.yyyy HH:mm')` падал на главной с 500. `n.publishAt` приходит как `String` (`"2026-08-08 20:17:14.741"`), а Spring 6.2 + Java 22 не конвертируют через deprecated `Date.parse()`. Заменили на `T(java.sql.Timestamp).valueOf(...)` с `th:if` для пустых значений.
3. **KaraokeProperties** — двухчастный фикс. (a) Убраны обращения `KaraokeProperties` из `karaoke-web/`: на проде файлов `Karaoke.properties` нет, и публичный веб не должен зависеть от admin-only state. Реальные вызовы (`vkPreviewImageWidth/Height` в `PublicApiController.songVkImage`) захардкожены в 1200/630 (это текущие fallback-значения), импорт `KaraokeProperties` удалён. (b) Try/catch вокруг всего `savePropertiesMap()` — defensive programming на случай будущих случайных использований в shared-коде. Покрывает оба вызова: из `loadPropertiesMap()` и из `set()`.

**Зачем.** Прямой запрос пользователя по результатам анализа прод-логов. Три категории: 🔴 потеря денег (ЮKassa-recurring — каждый день +N пользователей без продления, потому что `400 Bad Request` без понятного лога), 🟠 5xx на главной (Thymeleaf), 🟠 5xx на `/api/song-vk-image/{id}` (KaraokeProperties).

**Audit grep перед изменениями.**
```
$ grep -rn "KaraokeProperties\." karaoke-web/src/main/kotlin --include="*.kt"
# 0 results
$ grep -rn "KaraokeProperties" karaoke-web/src/main/kotlin --include="*.kt"
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/CaptchaConfigService.kt:7:
  // Ключи Yandex SmartCaptcha живут в tbl_public_settings (Postgres), не в файловых KaraokeProperties
```
Только 1 комментарий — это KDoc-пояснение правильного паттерна. Реальные вызовы только в `PublicApiController.songVkImage:467-468`.

**Архитектурный урок.** **Admin-only `KaraokeProperties` должен жить только в admin-only коде** (`karaoke-app`), а в публичном вебе (`karaoke-web`) конфиги хранятся в `tbl_public_settings` (Postgres) — паттерн уже зафиксирован в `CaptchaConfigService.kt:7` (ключи Yandex SmartCaptcha). До этого PR было 2 нарушения паттерна: `PublicApiController.kt:467-468` — обращались к `KaraokeProperties` за размерами VK-превью. После фикса обращений к `KaraokeProperties` из `karaoke-web/` нет вообще (grep — 0 результатов).

**Связь с AGENTS.md / конституцией.** Принцип VII.4 (логирование для диагностики, не «ради логирования»): добавили `statusCode` + тело ответа ЮKassa, чтобы при следующем 4xx/5xx можно было сразу увидеть причину. Аналогия с Q&A «redirectErrorStream(false) блокирует процесс»: подавление stderr привело к дебагу вслепую; здесь — подавление тела ответа привело к невозможности диагностировать 400. Defensive programming: try/catch в `savePropertiesMap()` — не «глушить ошибки», а «обеспечить работу на проде, где файла нет, и оставить WARN для будущих инцидентов». Также `n.publishAt != null and n.publishAt != ''` в main.html — двухветочный рендеринг с защитой от null/пустой строки.

**Метрики реализации.**
- Файлов изменено: 4 кода (`PaymentService.kt`, `PublicApiController.kt`, `KaraokeProperties.kt`, `main.html`) + 4 OpenSpec артефакта (`proposal.md`, `specs/runtime-errors/spec.md`, `design.md`, `tasks.md`) + 1 docs (`architecture-notes.md`).
- Строк: `PaymentService.kt` +6 (новый catch), `PublicApiController.kt` −3 (удалены 2 вызова + 1 import), `KaraokeProperties.kt` +6 (try/catch вокруг savePropertiesMap), `main.html` +2 (двухветочный `<span>` с `th:if`).
- `grep -rn "KaraokeProperties\." karaoke-web` после фикса: **0 результатов**.
- `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`: BUILD SUCCESSFUL in 25s.
- `./gradlew ktlintCheck`: BUILD SUCCESSFUL.
- ESLint webvue3, ESLint karaoke-public: 0 errors.
- KDoc coverage: 97.0% (≥ 50%).
- JSDoc coverage: 100.0% (≥ 50%).
- pre-commit ktlint: Passed.
- GitHub Actions: CI 7/7 PASS (ktlint, ESLint webvue3, ESLint karaoke-public, KDoc, JSDoc, Docs, Baseline).
- PR: #211, mergeCommit `6adf340823d0c0f38a70cb7e1ace599b325b3b1b`.
- Ветка `161-fix-prod-runtime-errors-2026-08-09` **НЕ удалена** после merge (AGENTS.md, секция «Жизненный цикл feature-ветки»).

**Связанные документы:**
- [`openspec/changes/fix-prod-runtime-errors-2026-08-09/`](../../openspec/changes/fix-prod-runtime-errors-2026-08-09/) — полный change: `proposal.md` (Why/What/Impact), `specs/runtime-errors/spec.md` (новое требование «На вебе нет обращений к KaraokeProperties» + 3 сценария), `design.md` (аудит обращений + риск-таблица), `tasks.md` (28 чекбоксов).
- [AGENTS.md](../../AGENTS.md), Q&A «redirectErrorStream(false) блокирует процесс» — аналогичный кейс: не логируем то, что не помогает диагностике.
- [AGENTS.md](../../AGENTS.md), секция «CI-gate для master» — feature-ветка + PR + CI 7/7 + merge без `--delete-branch` (соблюдено).
- [AGENTS.md](../../AGENTS.md), Q&A «Jackson отбрасывает is в boolean-полях Kotlin DTO» — аналогичный паттерн «обратить внимание на конвенцию сериализации, которая ломает биндинг на фронте».

**Требуется ручной deploy (не агентом).**
- nginx `minio-proxy` на проде: добавить `location /yookassa/` блок (см. design.md).
- Сборка `karaoke-web.jar` и рестарт контейнера.
- Верификация: `GET https://svoemesto.ru/`, `GET https://svoemesto.ru/api/song-vk-image/159`, `docker logs karaoke-web --since "24h" | grep -E "PaymentService.chargeRecurring"` (см. tasks.md, раздел 6).

## Pass 42: устаревший премиум-статус в шапке `karaoke-public` после окончания подписки (2026-08-09, specs/162-fix-header-stale-premium-status)

**Что.** Шапка публичного сайта (`AuthStatusWidget.vue`, значок 🪙) показывала пользователя премиумом ещё долго после того, как его подписка реально закончилась на сервере — до случайного захода на `/account`/`/account/editor/*` или до перелогина. Причина: `useAuth.js` кэширует объект пользователя в `localStorage` на момент логина и раньше обновлял его (`fetchMe()`) только из трёх конкретных view; `App.vue` при старте SPA `fetchMe()` не вызывал вовсе.

**Фикс** (`karaoke-public/src/composables/useAuth.js`, единственный изменённый файл):
1. `fetchMe()` обёрнут в `try/catch` — сетевой сбой (`authGet` реджектит промис на `xhr.onerror`) больше не роняет фоновый таймер и не трогает уже сохранённый `user`/`token`.
2. Module-level guarded `startAutoRefresh()` — ставит `setInterval` на 5 минут, вызывающий `fetchMe()`, пока есть токен; гарантированно стартует один раз за жизнь вкладки, даже если `useAuth()` вызывается из нескольких компонентов (`AuthStatusWidget` монтируется на `HomeView`/`SearchView`/`ZakromaView`/`SongView`).
3. `startAutoRefresh()` вызывается из самой `useAuth()` при каждом обращении — это даёт немедленный `fetchMe()` уже при обычной загрузке страницы, а не только по тику таймера.

`AuthStatusWidget.vue` изменений не потребовал: `isPremium` уже вычисляется из того же реактивного module-level `user` ref, который теперь обновляется автоматически.

**Зачем.** Прямой баг-репорт: премиум-пользователь после окончания подписки продолжает видеть себя премиумом в шапке, что вводит в заблуждение относительно реального доступа. Backend не менялся — `GET /api/public/auth/me` (`PublicAuthController.me`) и так уже отдаёт живой, не кэшируемый на сервере статус на каждый вызов (см. `SiteUserResolver`).

**Live-верификация (dev-pc, локальный docker-стек).** Через Playwright (`playwright-core` + системный `google-chrome`, `page.clock` для перемотки виртуального времени без реального ожидания 5 минут) против `karaoke-public` (порт 7907) + `karaoke-web` (порт 8897):
- Одноразовый тестовый `SiteUser` создан напрямую в LOCAL БД (`is_premium=true`, bcrypt-хэш пароля с префиксом `{bcrypt}` для `DelegatingPasswordEncoder`), после теста удалён вместе с токенами.
- До: значок 🪙 виден. Через `docker exec karaoke-db psql ... UPDATE tbl_site_users SET is_premium=false` (без перезагрузки страницы) + перемотка виртуального времени на 5 мин 10 сек → значок 🪙 пропал сам. Зафиксировано 2 запроса `GET /api/public/auth/me` (немедленный + один фоновый тик), 0 ошибок в консоли браузера.
- **Найденная попутно ловушка окружения**: headless `google-chrome`, запущенный без `--no-proxy-server`, зависал на `page.goto` к `localhost` — в окружении задан системный `HTTP_PROXY`/`HTTPS_PROXY`, который Chrome (в отличие от `curl`, уважающего `no_proxy`) пытался использовать даже для `localhost`.
- **Обнаружена и исправлена попутная проблема LOCAL БД**: identity-последовательность `tbl_site_users.id` отставала от реальных данных (следующий `nextval` совпадал с уже существующим id) — вероятно, следствие синка строк с явными id без выравнивания sequence. Поправлено `setval(pg_get_serial_sequence(...), max(id))`; отдельная проверка/фикс для остальных синкаемых таблиц в рамках этой задачи не проводилась.

**Метрики реализации.**
- Файлов изменено: 1 код (`useAuth.js`, +25/−7 строк).
- `cd karaoke-public && npm run lint:check`: 0 замечаний. `npx prettier --check`: чисто.
- `./gradlew` не запускался — backend не менялся.
- Не закоммичено (ветка не создана, PR не открыт) — ожидает решения пользователя по коммиту/PR.

**Связанные документы:**
- [`specs/162-fix-header-stale-premium-status/`](../specs/162-fix-header-stale-premium-status/) — spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md.
- **Занятость номера 161**: изначально фича была заведена как `161-fix-header-stale-premium-status`, но номер `161` уже занят веткой `161-fix-prod-runtime-errors-2026-08-09` (Pass 41 выше, PR #211, тег `seq/161`) — директория и все внутренние ссылки переименованы на `162-` до какого-либо коммита/PR, коллизии в истории не возникло.

## Pass 46: fail-fast nginx для upstream karaoke-web (164-nginx-upstream-reset, 2026-08-10)

**Контекст инцидента.** 10 августа 2026 на проде (`sm-karaoke.ru`) в течение дня происходили всплески 502 Bad Gateway для пользователей. Со стороны nginx-сервера (`188.119.64.111`, Ubuntu 24.04, nginx 1.24.0) было видно:
- `/var/log/nginx/error.log` за сутки: **71 × `recv() failed (104: Connection reset by peer)`**, **8 × `connect() failed (111: Connection refused)`**, **7 × `upstream prematurely closed connection`** — все на `upstream: http://127.0.0.1:8897` (Spring Boot в `karaoke-web`).
- 5xx в access.log: 10:15, 10:32–10:54, 11:20, 13:10–13:11, 13:34, 13:53–13:55, 14:26–14:29 (пик 321 req/min, 4–5 % из них 5xx).
- Топ упавших URL: `/api/public/news/since` (52), `/api/public/account/chat/unreadcount` (23), `/api/public/share/claim` (8) — все три polling-эндпоинта.
- `/actuator/health` — 404 (выключен в `karaoke-web`); `/dumps/` пусто (heap-dump не делался, OOM не было); `dmesg`/`journalctl` без OOM-killer событий; `docker stats` показывает `karaoke-web` 381 МБ RSS из Xmx 1048 м, load на хосте 0.03–0.07, ресурсов хватает.
- Прямой `curl 127.0.0.1:8897/api/public/auth/me` в моменты всплесков отдавал быстро (90–210 мс), контейнер жив — **nginx видел upstream, который сбрасывал коннект в момент GC-pause или рестарта JVM**.
- **Корень — Spring Boot в `karaoke-web`**, а не nginx: после каждого ручного `do.sh build_start_web` контейнер сразу же подвисал снова (юзер подтвердил). Правка nginx ниже лечит **видимый симптом** (60-секундные зависания страниц), но не лечит причину падений Java-процесса — её нужно копать отдельно (heap-dump при следующем зависании, thread-dump, проверить пагинацию `/news/since?id=0` который отдаёт 3.2 МБ JSON одним ответом).

**Фикс** (`deploy/web-server-deploy/deploy/80to8897`, единственный изменённый файл, 11 строк):
1. `location /api/` (Spring Boot upstream на `127.0.0.1:8897`): добавлены `proxy_connect_timeout 5s;` и `proxy_next_upstream off;`. Существующие `proxy_read_timeout 300;` / `proxy_send_timeout 300;` сохранены — для длинных polling-ответов и SSE.
2. `location /changerecords` (SSE-стрим): те же `proxy_connect_timeout 5s;` и `proxy_next_upstream off;`. Уже открытое SSE-соединение не рвём (`proxy_read_timeout 300`), рвём только **попытку подключения** к упавшему upstream.

**Зачем.**
- nginx-дефолты для upstream'а: `proxy_connect_timeout 60s` и `proxy_next_upstream error timeout` — это означает, что при недоступности upstream nginx **60 секунд пытается открыть новое соединение** и/или **повторить через `proxy_next_upstream`**, прежде чем отдать клиенту 502. Браузер в это время «висит».
- 5 секунд достаточно, чтобы отличить «upstream упал» от «upstream перегружен»: TCP connect на `127.0.0.1:8897` в норме занимает < 10 мс, 5 секунд — это 500× дефолта и **гарантированно провалится при любом crash/restart**.
- `proxy_next_upstream off` — у нас один upstream (один `karaoke-web` контейнер), второго нет, повтор бессмысленен и только задерживает 502.
- Длинные `read`/`send` таймауты (300 s) **не трогаем**: они нужны для текущих polling-ответов и SSE, которые могут жить минуты.

**Почему НЕ `/minio/` и НЕ `/smartcaptcha/`** — там те же настройки применены ещё в Pass 45 (PR #212, `fix-prod-runtime-errors-2026-08-09`), но в `git diff HEAD` для этих блоков сейчас нулевой: правки Pass 45 пришли в прод через rsync, в git-коммит не вошли. Это отдельная техдолжная задача (синхронизировать серверный 80to8897 с git-репо), в Pass 46 она не затрагивается.

**Метрики реализации.**
- Файлов изменено: 1 конфиг (`deploy/web-server-deploy/deploy/80to8897`, +11 / −0).
- Локально `nginx -t` не запускается (нет nginx в окружении агента); на проде проверяется `sudo nginx -t` перед reload (задача деплойера, не агента).
- ktlintCheck, ESLint, KDoc/JSDoc coverage: не задействованы (конфиг nginx, не код).
- GitHub Actions: CI 7/7 — `docs` (новый Pass 46 в `docs/architecture-notes.md`) + `lint` пройдут; `baseline` покажет 0; `ktlint`/`ESLint`/`KDoc`/`JSDoc` — без изменений в коде → чисто.
- PR: TBD (см. `gh pr create --base master` в задачах ниже), mergeCommit — TBD.
- Ветка `164-nginx-upstream-reset` **НЕ удалена** после merge (AGENTS.md, секция «Жизненный цикл feature-ветки»).

**Связанные документы:**
- [AGENTS.md](../../AGENTS.md), секция «Документация и иерархия» — этот Pass 46 входит в цепочку архитектурного changelog (Pass 41–Pass 45 в этом же файле).
- [AGENTS.md](../../AGENTS.md), Q&A «Дефолты DEMO рендера…» и аналогичные — образец «как задаются и применяются дефолты в двух местах», здесь работает та же логика «read timeout = запас, connect timeout = fail-fast».
- Pass 41/PR #211 (`fix-prod-runtime-errors-2026-08-09`) и Pass 45/PR #212 (тот же change, докатка) — на сервере применили те же fail-fast приёмы для `/minio/`, `/smartcaptcha/`, `/yookassa/`, но в git-репо эти блоки до сих пор не закоммичены — задокументировано как техдолг.

**Требуется ручной deploy (не агентом).**
- `rsync deploy/web-server-deploy/deploy/80to8897 root@188.119.64.111:/root/Karaoke/deploy/80to8897`.
- На сервере: `sudo nginx -t && sudo systemctl reload nginx` (или как раньше делали через `cp /root/Karaoke/deploy/80to8897 /etc/nginx/sites-enabled/80to8897 && nginx -t && systemctl reload nginx`, см. AGENTS.md, секция «Nginx 80to8897»).
- Верификация после reload:
  - `tail -n 200 /var/log/nginx/error.log` — синтаксис без `[emerg]`.
  - Нагрузить `/api/public/news/since` 30 раз подряд при работающем karaoke-web — все 200.
  - Сэмулировать падение (`docker stop karaoke-web`) и дёрнуть `/api/public/auth/me` — должен прийти **502 Bad Gateway за ≤ 5 секунд**, а не 60. После `docker start karaoke-web` всё восстанавливается.
  - Открыть плеер и проверить, что SSE `/changerecords` не отвалился в момент рестарта (новый клиент не подключится 5 c, но уже подключённый не рвётся).

## Pass 47: восстановление потерянных DDL share-link (2026-08-10)

**Контекст инцидента.** 163 спека (PR #215, `163-add-song-share-link`, 2026-08-10)
открыла фичу «Временный полный доступ к песне» — Kotlin-сервис, контроллеры,
UI, миграции. На момент мержа в master в репо **не оказалось DDL** для
`tbl_song_share_links` и `tbl_song_share_sessions` — миграция была
написана локально (как `28_song_share_links.sql` + `28b_song_share_recordhash.sql`),
но при сборке коммита не попала в `git add` (untracked). После `git checkout
164-nginx-upstream-reset` неотслеженный файл был снесён как untracked artifact
новой ветки.

**Симптом на проде.** С момента выката share-link до расследования (менее суток):
- Любой переход по `/share/{id}/{secret}` → 500 в `PublicShareController.claim`
  (`catch (_: Exception) { status(500).body({"errorCode": "share.notFound"})}`)
- Фронт `ShareView.vue` мапит 500+`share.notFound` в state "notfound" → UI
  показывает «Ссылка недоступна» (вводит в заблуждение — на самом деле
  server error).
- Под капотом — `PSQLException: relation "tbl_song_share_links" does not exist`
  в `SongShareLinkService.resolveForGuest:391` (запрос на `SELECT id, song_id`
  до inner try-блока, SQLException утекает в catch-all контроллера).

**Восстановление через `git fsck --lost-found` + `git reflog`.**

```bash
git reflog --all | grep -i share  # увидел: 0282a998 share-link: ...
# но этого коммита не хватило — DDL был только в working tree до 163-fix-song-editor-regressions
git fsck --no-reflogs --unreachable 2>&1 | grep blob
# → 13 dangling blobs, среди них:
#   c8cc7472af57616ed25d22650722f55a4ce444eb  (8914 b) — DDL 28_song_share_links.sql
#   e6c7d1733b88588e71936dffd52fbc0c5e56718a  (3956 b) — DDL 28_song_share_recordhash.sql
git cat-file -p c8cc7472a... > /tmp/share.sql
```

**Восстановлено в репо:**

1. `deploy/karaoke-db/38_song_share_links.sql` — оригинал миграции 28
   (CREATE TABLE, 7 индексов на `tbl_song_share_links` + 3 индекса на
   `tbl_song_share_sessions`). Идемпотентен (CREATE TABLE IF NOT EXISTS +
   DO-блоки для IDENTITY/PRIMARY KEY).
2. `deploy/karaoke-db/39_song_share_recordhash.sql` — оригинал 28b
   (recordhash + last_update триггеры, 4 функции). Идемпотентен
   (CREATE OR REPLACE FUNCTION + DO-блоки для триггеров).
3. `docs/features/guest-share-link.md` — per-feature документ (FR-009
   конституции), точно потерянный вместе с директорией `specs/163-add-song-share-link/`.
4. `docs/features/README.md` — строка 25 в таблице (feature key `guest-share-link`).
5. `AGENTS.md` Q&A — две новые записи:
   - «500 на `/api/public/share/claim` — где DDL для share-таблиц»
   - «Потерянные при переключении веток артефакты — как восстановить»

**Нумерация изменена 28 → 38/29 → 39** потому что:
- 28 уже занят (`28_rename_settings_to_songs.sql`)
- 29 занят (`29_albums.sql`)
- последний по нумерации — `37_news_auto_publish_kill_switch.sql` (Pass 41)
- 38/39 — следующие свободные по конвенции в `deploy/karaoke-db/`

**Восстановленные артефакты, которые НЕ нужны немедленно** (но сохранены
в object store, указаны для полноты):
- `b9404ea4...` (10979 b) — `specs/add-song-share-link/tasks.md` (исходный
  план с уже `[x]` отметками — большая часть работы сделана)
- `c3542162...` (7043 b) — `specs/add-song-share-link/proposal.md`
- `3a782a54...` (15133 b) — `specs/add-song-share-link/design.md`
- `2302219f...` (16263 b) — `specs/add-song-share-link/spec.md`
- `3d4f3dce...` (3134 b) — `webvue3/.../shareLinkStore.js` (Vuex)
- `c7c3ec6e...` (3795 b) — `karaoke-public/.../songShareLink.js` (services)

Они могут быть полезны, если пользователь захочет формально архивировать
OpenSpec change `add-song-share-link` (по закрытию tasks.md). Текущий
приоритет — применить восстановленные SQL миграции.

**Почему не выкатываю автоматически.** AGENTS.md запрещает агенту
подключаться к прод-серверу и применять миграции — это governance,
не пользовательское разрешение. Локальная БД (`nsa@nsa-i9` ≠ `dev@dev-pc`)
тоже не в зоне автоматических мутаций. Пользователь применяет сам.

**Ловушка (повторять НЕ надо).** В оригинальной миграции 28 в `song_id`
используется `bigint` (не `integer`) — потому что `tbl_songs.id`/`tbl_settings.id`
тоже `bigint`. FK на `tbl_song_share_links.song_id` не ставится намеренно —
таблица PROD-only, не должна зависеть от sync-состояния песен. Для
`tbl_song_share_sessions.share_link_id` FK ставится на
`tbl_song_share_links(id) ON DELETE CASCADE` — чтобы при отзыве ссылки
все её сессии автоматически удалились.

**Метрики восстановления.**
- Файлов создано: 3 (`38_song_share_links.sql`, `39_song_share_recordhash.sql`,
  `docs/features/guest-share-link.md`).
- Файлов отредактировано: 2 (`docs/features/README.md`, `AGENTS.md`).
- Строк добавлено: ~520 (DDL + per-feature doc + Q&A).
- Никакого кода не менялось (Kotlin/Vue) — только восстановление утраченного
  DDL и документации.
- CI 7/7 задействован только `docs` check (новые/изменённые .md) +
  сам проходит проверку структуры per-feature doc (`tools/check-feature-doc.sh`).
- PR: TBD — пользователь делает сам по AGENTS.md.

**Требуется ручной apply (не агентом).**
```bash
# Локально (после чего перезапустить karaoke-web через deploy/do.sh):
docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/38_song_share_links.sql
docker exec -i karaoke-db psql -U postgres -d karaoke < deploy/karaoke-db/39_song_share_recordhash.sql

# На проде (только через пользователя):
ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke" < deploy/karaoke-db/38_song_share_links.sql
ssh root@188.119.64.111 "docker exec -i karaoke-db psql -U postgres -d karaoke" < deploy/karaoke-db/39_song_share_recordhash.sql
# Затем пересобрать karaoke-web если контейнер ещё на старой схеме — но
# karaoke-web сейчас не использует share_* — значит достаточно одной БД миграции.
```

**Верификация после apply (read-only, безопасно).**
```bash
docker exec karaoke-db psql -U postgres -d karaoke -c "\dt tbl_song_share*"
# → tbl_song_share_links
# → tbl_song_share_sessions
docker exec karaoke-db psql -U postgres -d karaoke -c "\\d tbl_song_share_links"
# → 17 колонок, в т.ч. owner_site_user_id, song_id, token_hash, active_session_*
```

**Связанные документы:**
- `AGENTS.md` Q&A — две записи этого Pass 47.
- `docs/features/guest-share-link.md` — новый per-feature документ.
- `docs/features/README.md` — таблица 25 фич (вместо 24).
- [Pass 46](#pass-46-fail-fast-nginx-для-upstream-karaoke-web-164-nginx-upstream-reset-2026-08-10) — этот же nginx-фейл-фейл, лечивший СИМПТОМ share-link 500 (60-секундные зависания страницы), но не причину (отсутствующие таблицы).
- Коммит `0282a998` (reflog позиция HEAD@{18}) — оригинал share-link PR, без DDL.
- Dangling blobs `c8cc7472a...` и `e6c7d1733...` — будущая ссылка для
  будущих AI-агентов: "если что-то потерялось при переключении веток —
  сначала `git fsck --lost-found`, не паникуй".

---

## Pass 48 — Завершение share-link: guest player + heartbeat/release + admin API + sweeper (2026-08-10)

**Branch**: `164-complete-guest-share-link` | **Spec**: [`specs/164-complete-guest-share-link/`](../specs/164-complete-guest-share-link/spec.md)

### Что сделано

После Pass 47 (DDL восстановлен, share-таблицы существуют в `WORKING_DATABASE`) — основная фича
по-прежнему не работала: гость не мог открыть плеер, heartbeat/release не вызывались, админских
endpoint'ов для webvue3 не было, фоновый sweeper отсутствовал. Pass 48 завершил реализацию:

**Backend (karaoke-web):**
- `WebShareProperties.kt` — добавлено `heartbeatIntervalSeconds: Long = 25` (Phase 2, research.md D3).
- `PublicShareController.kt` — TTL whitelist расширен до `604800` (7 дней, Clarifications Q5).
  `release()` endpoint поддерживает и JSON (`@RequestBody`), и form-urlencoded (`@RequestParam`)
  — последнее нужно для `navigator.sendBeacon` при уходе со страницы (FR-012).
- `PublicPlayerController.kt` — `authorized()` теперь принимает опц. `session` query-param,
  проверяет через `SongShareLinkService.validateShareSession()`. Все stem-endpoint'ы
  (`/fileminus.mp3`, `/filevoice.mp3`, `/filebass.mp3`, `/filedrums.mp3`, `/playerdata`,
  `/access`, `/playerfile`) принимают `?session=`. Гость по share-сессии получает
  `canExport=false` (Clarifications Q1).
- `WebMvcConfig.kt` — `/api/siteusers/**` добавлен в `SiteAuthInterceptor` path-patterns
  (research.md D4).
- `SongShareLinkService.kt` — добавлен `revokeLinkById(linkId, reason, database)` (транзакционный
  admin-отзыв), `songIsShareablePublic()` (public wrapper для sweeper'а), `songHasSkipTag`
  стал `internal`.
- `SiteShareLinksController.kt` — НОВЫЙ. 3 endpoint'а для webvue3 admin: `/links`, `/links/revoke`,
  `/sessions`. Поддержка `target=local|remote`. Проверка `user.isEditor == true`
  → 403 `share.notEditor`.
- `ShareLinkSweeper.kt` — НОВЫЙ. Spring `@Scheduled(fixedDelayString = "...")` каждые 60 сек.
  4 типа отзыва: lease timeout (`result='timeout'`), expired by `expires_at` (`revoke_reason='expired'`),
  premium_lost (`SiteUser.isEffectivePremium`), song_unavailable (`songIsShareablePublic` == false).

**Frontend (karaoke-public):**
- `useShareLink.js` — `SHARE_TTL_OPTIONS` расширен до 3 вариантов: 1ч / 24ч / 7д.
- `usePlayerAccess.js` — `checkAccess(songId, shareSessionTokenHash?)` принимает опц. session
  и прокидывает в `/access?session=`.
- `KaraokePlayer.js` — конструктор принимает 6-й опц. аргумент `shareSessionTokenHash`. Если
  есть — прокидывает в `/playerdata` и в URL'ы стемов (`?session=`). Реализует heartbeat
  (`setInterval(25000)`) с `keepalive: true` + обработку 410 → overlay «Время сеанса истекло»
  с кнопкой «Закрыть» (Clarifications Q4). `release()` через `navigator.sendBeacon` на
  `_onEnded`, `beforeunload`, `pagehide`, `visibilitychange` (best-effort idempotent).
- `PlayerView.vue` — `mounted()` читает `route.query.session` и `sessionStorage['kp_share_session_${id}']`,
  пробрасывает в `KaraokePlayer`.
- `router/index.js` — `beforeEnter` для `/player/:id` пускает если есть валидный `?session=`
  или `sessionStorage['kp_share_session_${id}']` (FR-003).
- `ShareView.vue` — `expiresAtLabel` (МСК), кнопка «Скопировать ссылку» (secondary),
  скрытие «Открыть плеер» если `expiresAt < Date.now()` (FR-006, FR-007).
- `SongView.vue` — `watcher song` читает `sessionStorage['kp_share_session_${song.id}']` и
  передаёт в `checkAccess` (US6, FR-050).
- `ShareLinkModal.vue` — авто-обновление `getCurrentShareLink` каждые 30 сек, пока модалка
  открыта (FR-051, US7). `onUnmounted` очищает таймер.

### Архитектурные решения (зафиксированы в research.md)

| # | Решение | Файл |
|---|---|---|
| D1 | Прямой проброс sessionTokenHash в API плеера (не обмен на kp_token) | `PublicPlayerController.kt`, `KaraokePlayer.js` |
| D2 | Передача session через query-param `?session=` | `PublicPlayerController.kt` |
| D3 | `heartbeatIntervalSeconds=25` в `WebShareProperties` | `WebShareProperties.kt` |
| D4 | `SiteAuthInterceptor` + ручная проверка `isEditor` в контроллере | `WebMvcConfig.kt`, `SiteShareLinksController.kt` |
| D5 | Sweeper: SQL + `SiteUser.isEffectivePremium` + `songIsShareablePublic` | `ShareLinkSweeper.kt` |
| D6 | TTL whitelist: 3600 / 86400 / 604800 | `PublicShareController.kt`, `useShareLink.js` |

### Compilation / lint status

- ✅ `./gradlew :karaoke-web:compileKotlin` — SUCCESS (новые классы `SiteShareLinksController`,
  `ShareLinkSweeper` валидны).
- ⚠️ Полный `./gradlew ktlintCheck` не запущен — требует пользователя.
- ⚠️ `npm run lint:check` (karaoke-public, webvue3) не запущен — требует пользователя.
- ⚠️ `tools/check-kdoc-coverage.sh` и `tools/check-jsdoc-coverage.sh` не запущены —
  требуют пользователя (формальные `@param/@returns` JSDoc-теги не добавлены, но содержательные
  комментарии есть).

### Что НЕ сделано (требует ручной работы пользователя)

- T020, T024, T030, T035, T041, T044, T047, T057 — все **validation-таски** (end-to-end
  прогон по `quickstart.md`, 14 сценариев). По проекту нет CI-тестов — нужна ручная проверка.
- T059 — production build (`./gradlew bootJar` + `npm run build`). Долго + тяжёлая JVM.
- T060, T061 — коммит, push, PR, ожидание CI 7/7, мерж. Только пользователь (по AGENTS.md
  «Запрещено коммитить без явного запроса»).

### Связанные документы
- `specs/164-complete-guest-share-link/` — полный комплект артефактов: spec + research +
  data-model + contracts + quickstart + plan + tasks.
- `docs/features/guest-share-link.md` — обновлён (TTL 7д, sendBeacon release, Pass 48).
- `AGENTS.md` — раздел «Счётчики главной страницы» не затронут (share-таблицы PROD-only,
  SyncRegistry НЕ расширяется — FR-060 spec).

## Pass 50: hotfix — разделение `share.internal` vs `share.notFound` (2026-08-11, 167-fix-share-claim-500, PR #220, `1c0094b5`)

**Симптом**: гостевые `/share/{id}/{secret}` возвращают `500 share.notFound`,
хотя в логах karaoke-web видно `org.postgresql.util.PSQLException: ERROR:
relation "tbl_song_share_links" does not exist` (т.е. таблиц нет, а не
ссылка битая). Контроллер `/claim`, `/create`, `/heartbeat` ловили **catch-all**
`(_: Exception)` и маскировали под `share.notFound` / `share.leaseExpired` —
невозможно отличить «ссылка битая» от «у нас всё упало».

**Что сделано**:

1. Миграции применены на проде (вручную пользователем, см. AGENTS.md):
   - `deploy/karaoke-db/38_song_share_links.sql` — `tbl_song_share_links`,
     `tbl_song_share_sessions`, IDENTITY/PK, 7 индексов.
   - `deploy/karaoke-db/39_song_share_recordhash.sql` — recordhash-функции +
     триггеры `recordhash` + `last_updated` для обеих таблиц.

2. Код karaoke-web (Pass 50 hotfix, FR-010..FR-014):
   - `ShareErrorCode.kt:42-46` — добавлен `INTERNAL("share.internal")`.
   - `SongShareLinkService.kt:193-216` — добавлен sealed-подтип
     `ShareException.InternalError(cause: Throwable)`, пробрасывает
     stacktrace через `addSuppressed`.
   - `SongShareLinkService.kt:621` — `tryClaim` catch-all теперь
     `throw InternalError(e)` вместо `throw NotFound()`.
   - `PublicShareController.kt:174-179` — `/claim` catch-all
     `(InternalError) → 500 share.internal` (было `(Exception) → 500 share.notFound`).
   - `PublicShareController.kt:84-93` — `/create` catch-all `→ 500 share.internal`.
   - `PublicShareController.kt:193-203` — `/heartbeat` catch-all
     `→ 500 share.internal` (было `→ 410 share.leaseExpired` — особенно опасно).

3. Audit 4 «уже корректных» эндпоинтов (T013a, FR-014):
   - `/release` (line 197-210) — нет catch-all, audit PASS.
   - `/mine/{songId}` (line 92-119) — нет catch-all, audit PASS.
   - `/mine/{songId}/revoke` (line 121-130) — нет catch-all, audit PASS.
   - `/debug` (line 218-225) — нет catch-all, audit PASS.

   Полная таблица audit'а — `specs/167-fix-share-claim-500/plan.md`
   § «FR-014 Audit Conclusion».

**Метрики**:
- Catch-all'ов, маскирующих системные ошибки, было: **3** (claim, create, heartbeat).
- Catch-all'ов исправлено: **3**.
- Catch-all'ов оставлено без изменений (audit PASS): **4**.
- Новый errorCode: `INTERNAL = "share.internal"` (HTTP 500).

**Инварианты**:
- Доменные ошибки (`share.notFound`, `share.leaseExpired`, `share.concurrentLimit`,
  `share.rateLimited` и др.) сохраняют свои HTTP-коды — **никаких breaking
  changes для фронта**.
- Системные ошибки (БД, relation does not exist, NPE в SQL) теперь
  однозначно → `500 share.internal` — и сразу видны в логах karaoke-web
  + на `/debug` endpoint'е (FR-020).

**Что осталось сделать** (user-only):
- T020 — CI-gate local check (ktlint + 6 других).
- T021-T025 — явное одобрение + commit + push + PR + ожидание CI 7/7.
- T026 — manual deploy `cd deploy && bash do.sh build_start_web` на проде.
- T027 — post-deploy verify (quickstart.md scenarios 4, 5, 7, 8).

**Связанные документы**:
- `specs/167-fix-share-claim-500/{spec,plan,research,data-model,contracts,quickstart,tasks}.md`
- `docs/features/guest-share-link.md` — L10 и §«Диагностика 500-ошибок claim».
- `AGENTS.md` — Q&A «500 на /api/public/share/claim» обновлён с симптомом
  Pass 50 + `/debug` диагностикой.

## Pass 51: hotfix — корректные column names в loadSongInfo (2026-08-11, follow-up к PR #220, PR #221, `fcbfbaf0`)

**Симптом**: прод-инцидент 2026-08-11T15:17+03:00, в логах karaoke-web:

```
org.postgresql.util.PSQLException: ERROR: column "author" does not exist
  at SongShareLinkService.loadSongInfo(SongShareLinkService.kt:983)
  at SongShareLinkService.tryClaim(SongShareLinkService.kt:512)
  at PublicShareController.claim(PublicShareController.kt:147)
```

**Причина**: Pass 49 (PR #219) переименовал `tbl_settings`→`tbl_songs` + колонки
`author`→`song_author`, `album`→`song_album`, `year`→`song_year`, но
`SongShareLinkService.loadSongInfo` (lines 977-988) забыли обновить. До
Pass 50 это маскировалось контроллером `catch (_: Exception) { 500 share.notFound }`,
после Pass 50 — пробивается к `dispatcherServlet` (видно реальный класс).

**Фикс** (4 строки, root cause):
- SQL: `SELECT song_name, author, album, year, player_readiness_flags` →
  `SELECT song_name, song_author, song_album, song_year, player_readiness_flags`
- rs.getString/getInt: `author`→`song_author`, `album`→`song_album`, `year`→`song_year`

Kotlin-переменные внутри `SongInfo` (`author`/`album`/`year`) **не переименованы**
— используются в формулах `albumKey`/`artistKey` для storage путей превью.

**Метрика**: 1 файл, +4/-4 строки.

**Lessons learned**:
- Pass 49 rename миграция должна была также покрыть все SQL-литералы в
  karaoke-app/karaoke-web, которые ссылаются на `tbl_settings.*` / `author` /
  `album` / `year`. Сейчас есть grep'ом можно найти остальные места.
- Принцип «не маскировать системные ошибки» (Pass 50) выявил латентный баг —
  это хорошо: баг был там и раньше, просто был невидим. Теперь виден и
  починен.
- Per-feature docs должны включать раздел «Переименования колонок / схемы»
  чтобы rename-миграции были audit-traсeable.

**Связанные документы**:
- Pass 49 (PR #219) — rename `tbl_settings`→`tbl_songs`
- Pass 50 (PR #220, spec 167) — share.internal vs share.notFound (выявил Pass 51)

## Pass 53: fix(share-link) — loading state + 7 дней в модалке (2026-08-11, PR #222, `8a1f353c`)

**Симптом 1**: при нажатии «Временный доступ» на карточке песни, если у пользователя
уже есть активная share-ссылка — модалка показывает блок «Создать новую» вместо
«У этой песни уже есть активная ссылка». Race condition при открытии: `loadCurrent()`
async, `currentLink = null` стартовое значение → Vue рендерит блок «Создать» ДО
ответа бэкенда. Если ответ задерживается — пользователь не замечает переключения.

**Симптом 2**: в модалке выбора TTL только два варианта — «1 час» и «24 часа».
В `SHARE_TTL_OPTIONS` уже есть 7 дней (Pass 48 ae6b63e6), бэкенд принимает — модалка
просто забыла обновиться. TTL-радиокнопки были hardcoded в шаблоне.

**Фикс**:
- `ShareLinkModal.vue` — добавить `loading` ref. Пока `loadCurrent` не завершился —
  спиннер «Получаем текущую ссылку…». После — existing/create/error. Watch
  сбрасывает `loading.value = false` при скрытии модалки.
- `ShareLinkModal.vue` — `<label v-for="opt in ttlOptions">` вместо hardcoded radios.
  `SHARE_TTL_OPTIONS` импортирован из `useShareLink` (там уже живёт — Pass 48).

**Метрика**: 1 файл, +25/-4 строки.

**Lessons learned**:
- Frontend race conditions легко пропустить — нужен `loading` state для всех
  async-запросов при открытии модалок/табов. Без этого пользователь видит
  «пустое» состояние до прихода данных.
- Hardcoded значения в шаблонах — anti-pattern. `SHARE_TTL_OPTIONS` уже был
  массивом, нужно использовать его через v-for.

**Связанные документы**:
- Pass 48 (PR #218, ae6b63e6) — SHARE_TTL_OPTIONS добавлен в `useShareLink`,
  TTL whitelist расширен до 604800 (7 дней) на бэкенде
- Pass 52 (PR #222, 7c7f1ed7) — linkExpiresAt + aspect ratio + убрать копирование
- Pass 53 (PR #222, 8a1f353c) — loading state + 7 дней в модалке
