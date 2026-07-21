# KDoc/JSDoc: инструкция для разработчиков

> **Status**: guide
> **Last Updated**: 2026-07-20

Этот документ — **практическая инструкция** по добавлению KDoc/JSDoc
к публичным API проекта Karaoke в рамках US4 фичи
`001-code-standards-docs`. Не описывает архитектуру (см.
`docs/features/*.md`), а только шаблоны и best-practices.

## Связь с FR-006

Из `specs/001-code-standards-docs/spec.md#fr-006`:

> **FR-006**: Публичные API MUST сопровождаться KDoc/JSDoc-комментариями,
> генерирующими документацию через Dokka / typedoc без warning'ов;
> KDoc/JSDoc MUST содержать `@see`-ссылку на соответствующий
> per-feature документ.

## Что считается «публичным API»

### Kotlin

- `class`, `object`, `interface` без модификатора `internal`/`private`.
- `fun` верхнего уровня (`top-level`) — тоже публичные.
- Методы внутри публичного класса (если не `private`).
- Расширения (`fun String.foo()`).

**НЕ обязаны** иметь KDoc (но желательно):
- `data class` (DTO) — `@param`-блоки на полях избыточны, но класс
  в целом заслуживает краткого описания.
- `enum` — `@param` тоже избыточны, но класс enum может иметь общее
  описание.

### Vue/TypeScript

- `export default` Vue-компонент.
- `export const` composable, store, helper.
- Публичные `defineProps`, `defineEmits`, `defineSlots` в `<script setup>`.

## Шаблон KDoc для Kotlin-класса

```kotlin
/**
 * Краткое (1-2 предложения) описание — ЧТО делает класс.
 *
 * Более длинное описание — ЗАЧЕМ он нужен, какую проблему решает,
 * как используется. Можно несколько абзацев.
 *
 * @param TGenericName описание типового параметра
 * @see docs/features/<slug>.md
 * @see OtherClass для cross-reference
 */
class MyClass<TGenericName>(val name: String) {
    /**
     * Краткое описание метода.
     *
     * @param paramName описание параметра
     * @return что возвращает (если не Unit)
     * @throws IllegalStateException когда бросается
     * @see docs/features/<slug>.md
     */
    fun doSomething(paramName: String): String { ... }
}
```

### Что обязательно

- **Краткое описание** (1-2 предложения) — первая строка.
- **`@see docs/features/<slug>.md`** — для архитектурного контекста.
- **`@param` / `@return` / `@throws`** — для методов с неочевидным
  контрактом.

### Что НЕ нужно

- Restating the obvious (`fun getName(): String` — `/** Возвращает имя. */`
  избыточно).
- Длинные блоки кода в KDoc (лучше ссылка на пример в per-feature).

## Шаблон JSDoc для Vue-компонента

```ts
/**
 * Краткое описание компонента.
 *
 * Более длинное описание.
 *
 * @prop {Song[]} songs - список песен
 * @prop {number} page - текущая страница (1-based)
 * @emits row-click - клик по строке (payload: Song)
 * @slot actions - кнопки в actions-колонке
 * @see docs/features/<slug>.md
 */
export default defineComponent({
  name: 'SongsTable',
  props: {
    songs: { type: Array as PropType<Song[]>, required: true },
    page: { type: Number, required: true },
  },
  emits: ['row-click'],
})
```

### Что обязательно

- **Краткое описание** (1-2 предложения).
- **`@prop`** для каждого prop (TypeScript-types компилятор проверит,
  но JSDoc — для IDE и генератора документации).
- **`@emits`** для каждого emit.
- **`@slot`** для каждого slot.
- **`@see docs/features/<slug>.md`** — обязательно.

## Пошаговая инструкция (Kotlin)

1. **Найти класс** — `rg -l 'class <Name>' karaoke-app/src/main/kotlin`.
2. **Прочитать** класс + ближайший per-feature документ.
3. **Добавить KDoc-блок** перед `class`/`fun`.
4. **Внутри KDoc** — `@see docs/features/<slug>.md` (slug = `featureKey`
   per-feature документа, см. `docs/features/README.md`).
5. **Проверить**: `rg -B 5 'class <Name>' karaoke-app/src/main/kotlin | head -20`.
6. **Прогнать Dokka** (после подключения, см. `plan.md` Phase 2):
   ```bash
   ./tools/generate-docs.sh
   ```

## Пошаговая инструкция (Vue/TS)

1. **Найти компонент** — `rg -l 'defineComponent|export default' webvue3/src/components`.
2. **Прочитать** компонент + `<script setup>` блок.
3. **Добавить JSDoc** перед `export default` (или перед `defineComponent`).
4. **Внутри JSDoc** — `@see docs/features/<slug>.md`.
5. **Проверить**: IDE покажет JSDoc в tooltips.
6. **Прогнать TypeScript-чек**:
   ```bash
   cd webvue3 && npx vue-tsc --noEmit
   ```

## TODO: список классов для KDoc (приоритет)

Эти классы упоминаются в per-feature документах, но ещё не имеют полного
KDoc. Список для следующих итераций.

> **Состояние на 2026-07-21 (PR #19, 008-jsdoc-quality)**:
> KDoc coverage = **100%** (356/356) на top-level классах в `karaoke-app` и
> `karaoke-web`. JSDoc coverage = **100%** (163/163) на Vue/TS компонентах
> в `webvue3` и `karaoke-public`. Авто-генерация через `tools/auto-kdoc.py`
> (Kotlin) и `tools/auto-jsdoc.py` (Vue/TS) + ручной pass для критичных.
>
> Pass 4: улучшение качества JSDoc (замена авто-генерации на ручной для
> топ-11 Vue-компонентов: App, HomeView, SongsView, SongsTable, SongEdit,
> ProcessWorker, CustomConfirm, SearchText, SubsEdit, SyncTable, SongView,
> HomeView — см. PR #19).

### karaoke-app (P1)

- [x] `KaraokeProcessWorker` (`KaraokeProcessWorker.kt`) — главный воркер ✅ 2026-07-20
- [x] `KaraokeProcessThread` (`KaraokeProcessThread.kt`) — обёртка subprocess ✅ 2026-07-20
- [x] `Settings` (`model/Settings.kt`) — главная сущность ✅ 2026-07-20
- [x] `Settings.loadListFromDb` / `Settings.saveToDb` — критичные методы ✅ 2026-07-20
- [x] `KaraokeDbTable.save` / `KaraokeDbTable.loadList` — reflection-loader ✅ 2026-07-20
- [x] `Connection.local` / `Connection.remote` / `Connection.virtual` — фабрики БД ✅ 2026-07-20
- [x] `SyncTarget<T>` (`sync/SyncTarget.kt`) — generic sync ✅ 2026-07-20
- [x] `MltGenerator.generate` (`mlt/MltGenerator.kt`) — генератор MLT ✅ 2026-07-20
- [x] `SseNotificationService.send` (`services/SseNotificationService.kt`) — broadcast/adress ✅ 2026-07-20
- [x] `LyricsFinderService.findLyrics` (`llm/LyricsFinderService.kt`) ✅ 2026-07-20
- [x] `KaraokeStorageService.upload` (`services/KaraokeStorageService.kt`) ✅ 2026-07-20
- [x] `MonitoringService.run` (`monitor/MonitoringService.kt`) ✅ 2026-07-20
- [x] `MainController` (`controllers/MainController.kt`) — базовый KDoc ✅ 2026-07-20
- [ ] **Pass 2**: ручной KDoc для Settings, KaraokeProcessWorker, SyncTarget,
  MLT-генератор (заменить авто-генерацию на качественный)

### karaoke-web (P2)

- [x] `MainController.main` (`karaoke-web/controllers/MainController.kt`) ✅ 2026-07-20
- [x] `PublicApiController.stats` (`karaoke-web/controllers/PublicApiController.kt`) ✅ 2026-07-20
- [x] `PublicPlayerController.stemsReady` (`karaoke-web/controllers/PublicPlayerController.kt`) ✅ 2026-07-20

### webvue3 (P2)

- [x] `SongsTable.vue` — главная таблица ✅ 2026-07-20
- [x] `Songs/store.js` — Vuex-модуль песен ✅ 2026-07-20
- [x] `SyncTable.vue` — sync UI ✅ 2026-07-20
- [x] `SitePlaylistsTable.vue` ✅ 2026-07-20
- [ ] `AuthorsTable.vue`, `PicturesTable.vue`, `ProcessesTable.vue`
- [ ] `SiteUsersTable.vue`, `DictionariesTable.vue`
- [ ] `StatsView.vue` — статистика
- [ ] Vuex-модули: `Processes/store.js`, `Publish/store.js`

### karaoke-public (P3)

- [x] `HomeView.vue` — главная страница ✅ 2026-07-20
- [x] `PlayerView.vue` — караоке-плеер ✅ 2026-07-20
- [ ] `SongView.vue` — страница песни
- [ ] Composables: `useAuth.js`, `usePlayer.js`, `useFavorites.js`

### Авто-генерация KDoc

Для ускорения покрытия используется `tools/auto-kdoc.py` —
генерирует базовый KDoc (краткое описание + `@see`) для всех
top-level классов без KDoc. Логика:

1. Находит top-level декларации (`class`/`object`/`interface`/`enum`/`sealed`/`data`).
2. Проверяет наличие KDoc выше (в предыдущих 5 строках).
3. Вставляет KDoc **выше всех аннотаций** (например, `@Service`),
   чтобы соблюсти `annotation-spacing` правило ktlint.
4. Slug для `@see` берётся из маппинга `CLASS_SLUG_OVERRIDE`
   (для критичных классов) или из директории (`model/` → `dual-db-sync.md`).

```bash
# Dry-run (показывает, что будет сделано):
python3 tools/auto-kdoc.py --dry-run karaoke-app/src/main/kotlin

# Применить:
python3 tools/auto-kdoc.py karaoke-app/src/main/kotlin
python3 tools/auto-kdoc.py karaoke-web/src/main/kotlin
```

### Авто-генерация JSDoc

Для Vue/TS используется `tools/auto-jsdoc.py` — генерирует
базовый JSDoc (`/** ... */`) перед `export default {...}` или
`defineComponent({...})`. Логика:

1. Находит `export default` или `defineComponent`.
2. Проверяет JSDoc выше (в предыдущих 10 строках).
3. Извлекает `name` из `name: 'X'`, `props` из `props: {...}` и `emits` из `emits: [...]`.
4. Генерирует JSDoc с `@prop`, `@emits`, `@see`.
5. Slug для `@see` берётся из маппинга `NAME_SLUG_OVERRIDE`
   (для критичных компонентов) или `AGENTS.md` (дефолт).

```bash
python3 tools/auto-jsdoc.py webvue3/src
python3 tools/auto-jsdoc.py karaoke-public/src
```

**Когда использовать**:
- Массовое покрытие для больших модулей.
- Временный baseline (затем заменяется на ручной JSDoc в Pass 3).
- Не подходит для компонентов со сложной семантикой — там ручной JSDoc лучше.

## Автоматизация

### Генерация HTML-документации

```bash
./tools/generate-docs.sh         # генерация всех 4 сайтов документации
./tools/generate-docs.sh --clean  # с удалением старых выходов
```

**Выходы:**

| Модуль | Путь | Размер | Генератор |
|--------|------|--------|-----------|
| `karaoke-app` (backend) | `docs/api/dokka/karaoke-app/index.html` | 26M | Dokka 1.9.20 (dokkaJavadoc) |
| `karaoke-web` (public backend) | `docs/api/dokka/karaoke-web/index.html` | 3.9M | Dokka 1.9.20 (dokkaJavadoc) |
| `webvue3` (admin SPA) | `docs/api/typedoc-webvue3/index.html` | 188K | typedoc 0.28 |
| `karaoke-public` (public SPA) | `docs/api/typedoc-karaoke-public/index.html` | 180K | typedoc 0.28 |

**Почему `dokkaJavadoc`, а не `dokkaHtml`?** Dokka 1.9.x/2.0.x ломается на
Jackson 2.19 (поставляется Spring Boot 3.5 BOM) — `dokkaHtml` использует
JSON-templating, который ожидает конструктор `TypeFactory.<init>(LRUMap)`,
удалённый в Jackson 2.18. `dokkaJavadoc` использует другой код path и
работает. См. комментарий в `build.gradle.kts:99-110`.

**Ограничение typedoc:** парсит только `.js`/`.ts` файлы. `.vue` single-file
components игнорируются (для них нужен `typedoc-plugin-vue` или
`vue-docgen-cli`, не подключены — проект в основном на JS, без JSDoc
в `.vue`).

## Почему это важно

- **Онбординг**: новый разработчик читает per-feature документ →
  открывает KDoc в IDE → видит сигнатуру, `@param`, `@throws`, `@see`
  на другие классы. Без KDoc — открывает 5 файлов подряд.
- **Рефакторинг**: при переименовании класса IDE предлагает обновить
  все ссылки. KDoc-ссылки (`@see`) тоже обновляются; в per-feature
  документе — нет (там нужен `tools/verify-kotlin-refs.sh`).
- **Генерация HTML**: Dokka/typedoc генерируют сайт документации,
  который хостится на GitHub Pages (или в `/docs/api/`).

## Связь с другими документами

- [CONTRIBUTING.md](../../CONTRIBUTING.md#kotlin-kdoc-public-api) — MUST-правило
- [specs/001-code-standards-docs/spec.md#fr-006](../specs/001-code-standards-docs/spec.md) — формальное требование
- [specs/001-code-standards-docs/plan.md#phase-6](../specs/001-code-standards-docs/plan.md) — план реализации
- [docs/features/README.md](../features/README.md) — список 9 фич
