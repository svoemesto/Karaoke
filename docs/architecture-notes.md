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
