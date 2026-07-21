# Architecture Notes — актуальный changelog

> **Что это.** Датированные записи о значимых изменениях архитектуры, новых
> подсистемах, лит-инструментах и уроках, извлечённых из PR. Фокус — **«что
> изменилось и почему»**, без пересказа деталей реализации (они в git-истории
> и в per-feature документах).
>
> **Связанные документы**:
> - [`architecture-notes-archive.md`](./architecture-notes-archive.md) — старая
>   детальная история фич и ловушек (1718 строк, до PR #12).
> - [`DEVELOPMENT.md`](./DEVELOPMENT.md) — durable-карта архитектуры.
> - [`AGENTS.md`](../AGENTS.md) — инструкции для AI-агента.
> - [`docs/features/<slug>.md`](./features/) — per-feature документы.

---

## 2026-07 — Phase 001: Code Standards & Documentation

> **Цель фазы.** Привести проект в состояние «production-grade» по качеству
> кода, документации и CI. 14 PR, 548 файлов, +57217/−27869 строк.
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

## Метрики

| Метрика | До Phase 001 | После Phase 001 |
|---------|--------------|-----------------|
| ktlint baseline | 30612 | **0** |
| ESLint baseline | 436 | **0** |
| KDoc coverage | 0% | **100%** (356/356) |
| JSDoc coverage | 0% | **100%** (165/165) |
| Качественный KDoc | 0 классов | **89+ классов** |
| Per-feature документы | 0 | **11 + 1** |
| Документация | 1 файл (DEVELOPMENT.md) | **4 файла** (DEPLOYMENT, DATABASE, PUBLIC-MODULES, INVARIANTS) + 12 per-feature |
| CI jobs | 0 | **7 jobs** (все enforced) |

## Принципы, зафиксированные в Phase 001

1. **Baseline = 0** — единственное «правильное» состояние. Техдолг по
   лит-правилам не копится.
2. **100% coverage = baseline, не цель.** Качественные KDoc/JSDoc — это
   ручная работа поверх coverage.
3. **CI enforced** — лит-проверки, coverage, baseline stats.
   `informational` jobs (kdoc-coverage, jsdoc-coverage) дают метрики,
   но не блокируют merge.
4. **Per-feature документы** обязательны для каждой продуктовой фичи
   (FR-009 spec.md).
5. **KDoc/JSDoc выше всех аннотаций** (`@Service`/`@Component`),
   а не между ними и class — иначе ktlint ругается.
6. **Backticks в KDoc** могут сломать парсер — заменять на «пакет mko»,
   «файлы mko», «multitrack», «playlist» без backticks.
7. **Wildcard imports** разрешены (правило `no-wildcard-imports` отключено
   в `.editorconfig` как не-autofixable).

## Следующие фазы

- **Phase 002**: продуктовая фича (по согласованию).
- **Phase 003**: TBD (после Phase 002).

---

*Последнее обновление: 2026-07-21. Следующий changelog — после PR #26.*
