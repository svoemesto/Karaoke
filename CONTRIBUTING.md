# Contributing to Karaoke (svoemesto)

> **Скоуп правил**: 5 активных модулей — `karaoke-app`, `karaoke-web`, `webvue3`,
> `karaoke-public`, `deploy/`. Legacy `karaoke-db` и `karaoke-vue` вне скоупа
> (удаляются в отдельной задаче).
>
> **Связь с другими документами**:
> - `AGENTS.md` — инструкции для AI-агента (приоритет у человека).
> - `DEVELOPMENT.md` — архитектурный контекст и dated-история.
> - `.specify/memory/constitution.md` — непреложные принципы.
> - `docs/features/<slug>.md` — per-feature документы.

## TL;DR

- **Kotlin**: используем ktlint (форматирование). `redirectErrorStream(true)`
  для всех `ProcessBuilder`. JSON-ключи БЕЗ `is`-префикса.
  Nullable-колонки → nullable-поля. (detekt отключён — несовместим с
  Kotlin 2.2.20; см. `.editorconfig` + `AGENTS.md`.)
- **Vue/TS**: `<select class="form-select">` (не `form-control`). KDoc/JSDoc
  на публичных API. Wildcard-импорты **допустимы** (правило ktlint
  `no-wildcard-imports` отключено в `.editorconfig` как не-autofixable —
  см. ниже).
- **Pre-commit**: `pip install pre-commit && pre-commit install`. Обход:
  `git commit --no-verify`.
- **PR с новой фичей** = PR с per-feature документом в `docs/features/`.
- **Документация на русском**. Комментарии в коде — на русском, когда это
  не нарушает читаемость для иноязычного контрибьютора.

## Kotlin / Spring Boot

### kotlin-naming-classes: Именование классов

**Severity**: MUST
**Section**: kotlin
**Enforced by**: ktlint

Имена классов — `UpperCamelCase`. Один файл — один публичный класс
(исключение: маленькие `data class` и `sealed`-иерархии в одном файле).

**Правильно**:
```kotlin
class KaraokeProcessWorker { ... }
data class SettingsDto(val id: Long, val name: String)
```

**Неправильно**:
```kotlin
class karaokeProcessWorker { ... }   // нижний регистр
class settings_dto                    // snake_case в имени
```

**Рациональ**: Kotlin-конвенция + совместимость с reflection-loader'ом
`KaraokeDbTable`.

**Связанные инварианты**: [`constitution.md#ii-сырой-jdbc--дифф-по-хэшам`](../.specify/memory/constitution.md)

---

### kotlin-naming-functions: Именование функций и переменных

**Severity**: MUST
**Section**: kotlin
**Enforced by**: ktlint

Функции и `val`/`var` — `lowerCamelCase`. Константы (`const val` / `val` с
uppercase) — `UPPER_SNAKE_CASE`.

**Правильно**:
```kotlin
fun processFrame(frame: ByteArray): String { ... }
val currentTime = System.currentTimeMillis()
const val MAX_QUEUE_SIZE = 100
```

**Неправильно**:
```kotlin
fun Process_Frame(...)             // snake_case
val CurrentTime = ...              // PascalCase для val
```

---

### kotlin-imports-no-wildcard: Импорты — без wildcard

**Severity**: SHOULD
**Section**: kotlin
**Enforced by**: code-review (ktlint `standard:no-wildcard-imports` отключён)

Каждый импорт — отдельной строкой, без `*`. **Рекомендуется**, но НЕ
enforced: правило ktlint `no-wildcard-imports` отключено в `.editorconfig`,
потому что ktlint 1.7.1 не поддерживает autofix для этого правила
(возвращает "cannot be auto-corrected"). См. `.editorconfig` + ниже раздел
"Отключённые правила ktlint".

**Правильно**:
```kotlin
import java.io.File
import java.sql.Timestamp
```

**Неправильно** (но допустимо в текущем коде):
```kotlin
import java.io.*
import java.sql.*
```

**Рациональ**: явные импорты упрощают grep, устраняют ambiguity при
одинаковых именах из разных пакетов.

---

### kotlin-processbuilder-redirect-error-stream: ProcessBuilder и stderr

**Severity**: MUST
**Section**: kotlin
**Enforced by**: code-review-only

**ВСЕГДА** `redirectErrorStream(true)` при создании `ProcessBuilder` для
subprocess (ffmpeg, melt, Demucs, Sheetsage, etc.). `redirectErrorStream(false)`
**ЗАПРЕЩЁН** — буфер stderr переполняется и блокирует процесс.

**Правильно**:
```kotlin
val process = ProcessBuilder(command)
    .redirectErrorStream(true)   // ОБЯЗАТЕЛЬНО
    .start()
```

**Неправильно**:
```kotlin
val process = ProcessBuilder(command)
    .redirectErrorStream(false)  // ❌ ЗАБЛОКИРУЕТ ПРОЦЕСС
    .start()
```

**Рациональ**: восстановлено после инцидента на проде, см.
[DEVELOPMENT.md](../DEVELOPMENT.md).

**Связанные инварианты**: [`constitution.md#iv-async-очередь-задач-с-парсингом-stdout`](../.specify/memory/constitution.md)

---

### kotlin-jackson-no-is-prefix: JSON-ключи без `is`-префикса

**Severity**: MUST
**Section**: kotlin
**Enforced by**: code-review-only

Kotlin `val isX: Boolean` Jackson сериализует в JSON как `"x"` (без `is`).
Фронт читает `dto.premium`, НЕ `dto.isPremium`. Называйте поля сразу без
`is`-префикса, иначе фронт получит `undefined`.

**Правильно**:
```kotlin
data class UserDto(
    val premium: Boolean,        // → JSON: {"premium": true}
    val original: Boolean        // → JSON: {"original": false}
)
```

**Неправильно**:
```kotlin
data class UserDto(
    val isPremium: Boolean,      // → JSON: {"premium": true}, но Kotlin-имя обманчивое
    val isOriginal: Boolean
)
```

**Связанные инварианты**: [`DEVELOPMENT.md#kotlin-is-поля`](../DEVELOPMENT.md)

---

### kotlin-nullable-columns: Nullable-колонки → nullable-поля

**Severity**: MUST
**Section**: kotlin
**Enforced by**: code-review-only

`KaraokeDbTable.loadList` кидает NPE на `SQL NULL`, если Kotlin-поле объявлено
non-null. Для колонок, которые могут быть `NULL` (строка, timestamp, число),
Kotlin-поле **обязано** быть nullable.

**Правильно**:
```kotlin
data class Song(
    val id: Long,
    val name: String,
    val description: String?,     // может быть NULL в БД
    val createdAt: Timestamp?,    // может быть NULL
    val parentId: Long?           // может быть NULL
)
```

**Неправильно**:
```kotlin
data class Song(
    val description: String,      // NPE при SQL NULL
    val createdAt: Timestamp      // NPE при SQL NULL
)
```

**Связанные инварианты**: [`DEVELOPMENT.md#reflection-loader-и-nullable-колонки`](../DEVELOPMENT.md)

---

### kotlin-sync-registry: Новые syncable-сущности через SyncRegistry

**Severity**: MUST
**Section**: kotlin
**Enforced by**: code-review-only

Если сущность должна синхронизироваться LOCAL↔SERVER, она **обязана** быть
явно добавлена в `SyncRegistry.all` (`sync/SyncTarget.kt`) с 8 флагами
`sync_<key>_<push|pull>_<insert|update|delete|move>_allowed` в
`KaraokeProperties.kt`. Наличие `recordhash`-триггера в SQL **не** означает
автоматическое участие в sync.

**Рациональ**: см. ловушку с `tbl_subscriptions` в DEVELOPMENT.md (2026-07-09).

**Связанные инварианты**: [`constitution.md#iii-двух-бд-синхронизация-через-syncregistry`](../.specify/memory/constitution.md)

---

### kotlin-recordhash-triggers: Пересоздание recordhash при миграциях

**Severity**: MUST
**Section**: kotlin, sql
**Enforced by**: code-review-only

При добавлении/изменении колонок таблицы, участвующей в sync, **обязательно**
пересоздаётся `recordhash`-триггер (`deploy/recordhash_*.sql`) для этой
таблицы на **обоих** окружениях (LOCAL и PROD) — иначе md5 разойдётся и
sync сломается.

**Связанные инварианты**: [`DEVELOPMENT.md#dual-database-targets`](../DEVELOPMENT.md)

---

### kotlin-line-length: Длина строки ≤ 200

**Severity**: SHOULD
**Section**: kotlin
**Enforced by**: ktlint (`standard:max-line-length`, лимит 200 в `.editorconfig`)

Длинные строки ухудшают читаемость на узких экранах и в code review.
Лимит в `.editorconfig` — **200 символов** (повышен с дефолтного 140 для
SQL/HQL строк в `karaoke-app`). Prettier в SPA использует 120 — там
настройки жёстче.

**Правильно**:
```kotlin
data class Process(
    val id: Long,
    val name: String,
    val priority: Int = 0,
)
```

**Исключения**: SQL-запросы (допускается до 200), URL, тестовые данные.
Используйте `// ktlint:ignore` с обоснованием, **не** глобальный
`max-line-length`.

---

### kotlin-import-order: Порядок импортов

**Severity**: SHOULD
**Section**: kotlin
**Enforced by**: ktlint

Группы импортов через пустую строку, в каждой группе — алфавитный порядок:

1. `java.*`, `javax.*`
2. `kotlin.*`, `kotlinx.*`
3. Сторонние пакеты (`org.springframework.*`, `io.minio.*`, и т.п.)
4. Локальные (`com.svoemesto.*`)

**Правильно**:
```kotlin
import java.io.File
import java.sql.Timestamp

import kotlinx.serialization.Serializable

import org.springframework.stereotype.Service

import com.svoemesto.karaokeapp.model.Settings
```

---

### kotlin-kdoc-public-api: KDoc на публичных API

**Severity**: MUST
**Section**: kotlin
**Enforced by**: dokka

Каждый публичный класс и публичная функция **обязаны** иметь KDoc-блок
с описанием, `@param`, `@return` (если не `Unit`), `@throws` (если бросает
исключения). KDoc-блок **должен** содержать `@see docs/features/<slug>.md`
для архитектурного контекста (где применимо).

**Правильно**:
```kotlin
/**
 * Парсит stdout ffmpeg-процесса и обновляет прогресс в реальном времени.
 *
 * @param process запущенный [Process] (см. [KaraokeProcessThread])
 * @param onProgress callback для обновления прогресса (0..100)
 * @see docs/features/async-process-queue.md
 */
fun parseFfmpegProgress(process: Process, onProgress: (Int) -> Unit) { ... }
```

**Связанные инварианты**: [`specs/001-code-standards-docs/spec.md#fr-006`](../specs/001-code-standards-docs/spec.md)

---

## Vue 3 / TypeScript

### vue-select-form-select: `<select>` через `form-select`

**Severity**: MUST
**Section**: vue
**Enforced by**: code-review-only

Все `<select>` в `webvue3` и `karaoke-public` используют Bootstrap-класс
`form-select`, **не** `form-control` (это другой визуальный стиль в Bootstrap 5).

**Правильно**:
```html
<select class="form-select" v-model="selected">
  <option v-for="opt in options" :value="opt.value">{{ opt.label }}</option>
</select>
```

**Неправильно**:
```html
<select class="form-control" v-model="selected">  <!-- ❌ неверный класс -->
```

**Связанные инварианты**: [`AGENTS.md#bootstrap-5`](../AGENTS.md)

---

### vue-no-wildcard-imports: Импорты без wildcard

**Severity**: MUST
**Section**: vue, typescript
**Enforced by**: eslint (`no-duplicate-imports`, `import/no-default-export`)

Каждый импорт — отдельной строкой, никаких `import * as`.

**Правильно**:
```ts
import { ref, computed, watch } from 'vue'
import axios from 'axios'
```

**Неправильно**:
```ts
import * as vue from 'vue'  // tree-shaking ломается
```

---

### vue-table-layout-fixed: `table-layout: fixed` + явная `width`

**Severity**: MUST
**Section**: vue, css
**Enforced by**: code-review-only

`table-layout: fixed` требует явной `width: Npx` на каждой колонке
(`<col>` или `<th style="width: Npx">`). Без явной ширины браузер не
знает, как распределять колонки.

**Правильно**:
```html
<table class="table" style="table-layout: fixed">
  <colgroup>
    <col style="width: 200px">
    <col style="width: 100px">
    <col>
  </colgroup>
  ...
</table>
```

**Неправильно**:
```html
<table class="table" style="table-layout: fixed">
  <!-- нет width на колонках -->
</table>
```

**Связанные инварианты**: [`AGENTS.md#таблицы-karaoke-public`](../AGENTS.md)

---

### vue-no-flex-on-td: `display: flex` на `<td>` запрещён

**Severity**: MUST
**Section**: vue, css
**Enforced by**: code-review-only

`display: flex` на `<td>` ломает высоту строки таблицы. Используйте
`text-align: center; vertical-align: middle`.

**Правильно**:
```html
<td style="text-align: center; vertical-align: middle">
  <img src="...">
</td>
```

**Неправильно**:
```html
<td style="display: flex; justify-content: center">  <!-- ❌ ломает высоту -->
  <img src="...">
</td>
```

**Связанные инварианты**: [`AGENTS.md#таблицы-karaoke-public`](../AGENTS.md)

---

### vue-ellipsis-on-fixed-grid: `text-overflow: ellipsis` на узких grid-ячейках

**Severity**: MUST
**Section**: vue, css
**Enforced by**: code-review-only

CSS grid с фиксированными колонками и переменной длиной текста:
`white-space: nowrap` без `overflow: hidden; text-overflow: ellipsis` —
контент визуально наезжает на соседнюю колонку.

**Правильно**:
```css
.grid-cell-narrow {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
```

**Связанные инварианты**: [`AGENTS.md#css-grid-с-фиксированными-колонками`](../AGENTS.md)

---

### vue-filter-modal-css: FilterModal.vue — копировать CSS-блок

**Severity**: MUST
**Section**: vue, css
**Enforced by**: code-review-only

Любой новый `FilterModal.vue` копирует CSS-блок из
`Songs/filter/SongsFilterModal.vue`:
- `.xxx-input-field { width: calc(100% - 18px); }`
- `.xxx-button-clear-field { margin-left: -10px; }`
- `select.xxx-input-field { appearance: none; }`

Без `width: fit-content` поле растягивается на 200px-контейнер и кнопка
«X» наезжает на текст.

**Связанные инварианты**: [`AGENTS.md#круглая-кнопка-очистки-в-entityfiltermodalvue`](../AGENTS.md)

---

### vue-vuex-mutations-sync: Мутации Vuex — только sync-присвоение

**Severity**: MUST
**Section**: vue, typescript
**Enforced by**: code-review-only

В модулях Vuex (`<Entity>/store.js`) **только синхронные** присвоения
`state`. Async (XHR, dispatch) — в `actions`. Удаление/правки песни — через
SSE (`recordDelete`/`recordChange`), не локальным рендерингом.

**Связанные инварианты**: [`DEVELOPMENT.md#vuex-паттерны-songsstorejs`](../DEVELOPMENT.md)

---

### vue-jsdoc-public-api: JSDoc на публичных компонентах

**Severity**: MUST
**Section**: vue, typescript
**Enforced by**: typedoc

Каждый экспортируемый Vue-компонент, store, composable **обязан** иметь
JSDoc-блок с описанием props, emits, slots. JSDoc-блок **должен** содержать
`@see docs/features/<slug>.md` для архитектурного контекста.

**Правильно**:
```ts
/**
 * Таблица песен с пагинацией, фильтрами и bulk-операциями.
 *
 * @prop {Song[]} songs - список песен
 * @prop {number} page - текущая страница (1-based)
 * @emits row-click - клик по строке
 * @see docs/features/songs-table.md
 */
export default defineComponent({ ... })
```

**Связанные инварианты**: [`specs/001-code-standards-docs/spec.md#fr-006`](../specs/001-code-standards-docs/spec.md)

---

## SQL

### sql-nullable-columns: Nullable-колонки явно

**Severity**: MUST
**Section**: sql
**Enforced by**: code-review-only

В `CREATE TABLE` явно указывайте `NULL` или `NOT NULL` для каждой колонки.
Не полагайтесь на дефолт (в разных СУБД отличается).

**Правильно**:
```sql
CREATE TABLE tbl_songs (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(500) NOT NULL,
    description TEXT NULL,            -- явно
    created_at  TIMESTAMP NULL
);
```

---

### sql-recordhash-triggers: recordhash-триггеры при миграции

**Severity**: MUST
**Section**: sql
**Enforced by**: code-review-only

Каждая таблица, участвующая в sync, имеет триггер `tg_<table>_recordhash`,
обновляющий колонку `recordhash` (md5 от канонизированной строки) на
INSERT/UPDATE/DELETE. При изменении схемы таблицы **обязательно** обновить
триггер.

**Шаблон** (см. `deploy/recordhash_*.sql`):
```sql
CREATE OR REPLACE FUNCTION tg_tbl_songs_recordhash() RETURNS trigger AS $$
BEGIN
    NEW.recordhash := md5(NEW.id::text || ... );  -- все колонки
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Связанные инварианты**: [`constitution.md#ii-сырой-jdbc--дифф-по-хэшам`](../.specify/memory/constitution.md)

---

### sql-migration-filenames: Имена миграций

**Severity**: MUST
**Section**: sql
**Enforced by**: code-review-only

Файлы миграций в `deploy/karaoke-db/` называются `NN_<name>.sql`, где
`NN` — порядковый номер. Новая миграция **никогда** не редактирует старые.

**Правильно**: `deploy/karaoke-db/42_add_song_metadata.sql`

**Неправильно**: `deploy/karaoke-db/42.sql` (без имени)

---

### sql-skip-tag: Тег SKIP

**Severity**: MUST
**Section**: sql, kotlin
**Enforced by**: code-review-only

Если в `tags` есть `SKIP`, публичные страницы показывают заглушку
«удалено по требованию правообладателя». Тег `SKIP` в БД — **одно слово**
через пробел (не подстрока).

**Связанные инварианты**: [`DEVELOPMENT.md#тег-skip`](../DEVELOPMENT.md)

---

## Markdown / Documentation

### md-per-feature-doc: Per-feature документы — 6 обязательных разделов

**Severity**: MUST
**Section**: markdown
**Enforced by**: `tools/check-feature-doc.sh`

Каждый файл в `docs/features/` имеет 6 обязательных секций (см.
`contracts/per-feature-doc.md`):
1. Что делает
2. Зачем
3. Как работает (кратко)
4. Инварианты / правила
5. Известные ловушки
6. Ссылки на ключевые классы/файлы

PR с новой ключевой фичей (из FR-004 spec.md) **обязан** создать
соответствующий per-feature документ в том же PR.

**Связанные инварианты**: [`specs/001-code-standards-docs/spec.md#fr-009`](../specs/001-code-standards-docs/spec.md)

---

### md-link-validation: Валидация ссылок в документации

**Severity**: MUST
**Section**: markdown
**Enforced by**: `tools/verify-doc-links.sh` (lychee)

Все ссылки в `.md`-файлах валидируются через `lychee` в pre-commit и CI.
Битая ссылка — блокер.

**Связанные инварианты**: [`specs/001-code-standards-docs/spec.md#sc-005`](../specs/001-code-standards-docs/spec.md)

---

### md-russian-language: Документация на русском

**Severity**: MUST
**Section**: markdown, kotlin comments
**Enforced by**: code-review-only

`CONTRIBUTING.md`, `docs/features/*.md`, комментарии в коде — на русском.
Исключения: имена файлов, идентификаторы, KDoc-блоки, ссылки на
англоязычные инструменты (ktlint, eslint) — оставляются в оригинале.

**Рациональ**: единый язык проекта, согласован с `AGENTS.md`.

---

## Shell / Bash

### shell-set-e: `set -euo pipefail` в скриптах

**Severity**: MUST
**Section**: shell
**Enforced by**: shellcheck (`shellcheck` опционально, см. `AGENTS.md`)

Каждый новый `*.sh` в `deploy/` и `tools/` начинается с `set -euo pipefail`
(или эквивалент — `set -e` + `set -u` + `set -o pipefail`).

**Правильно**:
```bash
#!/usr/bin/env bash
set -euo pipefail
```

**Неправильно**:
```bash
#!/bin/bash
# без set -e — ошибка в середине скрипта не остановит выполнение
```

**Связанные инварианты**: [`DEVELOPMENT.md#очередь-взаимное-исключение-gradle-сборок-deploybuild-locksh`](../DEVELOPMENT.md)

---

### shell-quote-variables: Кавычки для переменных

**Severity**: MUST
**Section**: shell
**Enforced by**: shellcheck (`SC2086`)

Всегда `"$VAR"`, никогда `$VAR` без кавычек. Иначе пробелы и спецсимволы
ломают скрипт.

**Правильно**:
```bash
cp "$SRC" "$DST"
```

**Неправильно**:
```bash
cp $SRC $DST   # ❌ ломается на пробелах в путях
```

---

### shell-no-secrets-in-echo: Не печатать секреты

**Severity**: MUST
**Section**: shell
**Enforced by**: code-review-only

Никакой `echo "$DOCKER_PASSWORD"` или `cat deploy/do.env` в `do.sh`,
рантайм-скриптах, логах. Секреты — только в `do.env`/`.env` (в `.gitignore`).

**Рациональ**: попадает в логи и в переписку с AI-ассистентом.

**Связанные инварианты**: [`AGENTS.md#не-редактировать-напрямую-на-сервере`](../AGENTS.md), [`DEVELOPMENT.md#do.sh-не-должен-печатать-секреты`](../DEVELOPMENT.md)

---

## Docker

### docker-node-image: `node:22-alpine` для фронтендов

**Severity**: MUST
**Section**: docker
**Enforced by**: code-review-only

Все `Dockerfile` для `webvue3` и `karaoke-public` используют `node:22-alpine`,
**не** `node:latest` (недетерминирован).

**Связанные инварианты**: [`AGENTS.md#dockerfile-ловушки`](../AGENTS.md)

---

### docker-nginx-image: `nginx:stable` (не `alpine`)

**Severity**: MUST
**Section**: docker
**Enforced by**: code-review-only

Production-стадия `Dockerfile` для `webvue3` и `karaoke-public` использует
`nginx:stable`, **не** `nginx:alpine` — compose использует `/bin/bash -c`,
в alpine его нет → контейнер падает.

**Связанные инварианты**: [`AGENTS.md#dockerfile-ловушки`](../AGENTS.md)

---

### docker-jre-not-jdk: JRE, не JDK в прод-образах

**Severity**: MUST
**Section**: docker
**Enforced by**: code-review-only

Production-стадия `karaoke-app` и `karaoke-web` использует
`eclipse-temurin:22-jre-jammy`, **не** `-jdk-`. Spring Boot fat jar не
требует компилятора.

**Связанные инварианты**: [`AGENTS.md#dockerfile-ловушки`](../AGENTS.md)

---

## Pre-commit и CI

### precommit-install: Установка pre-commit

**Severity**: MUST
**Section**: tooling
**Enforced by**: code-review-only

Каждый разработчик **обязан** запустить `pre-commit install` после клона.
Хуки запускаются автоматически при `git commit`.

```bash
pip install pre-commit
pre-commit install
```

Обход при срочном коммите: `git commit --no-verify` (только для emergency).
Baseline-чек в CI строгий, `--no-verify` отменяет только локальный хук.

---

### precommit-baseline-check: Baseline-чек в CI

**Severity**: MUST
**Section**: tooling
**Enforced by**: CI (workflow `.github/workflows/lint.yml`)

CI **обязательно** запускает (см. [`.github/workflows/lint.yml`](../.github/workflows/lint.yml)):

- `backend-lint` — `./gradlew ktlintCheck` (baseline-aware,
  читает `config/ktlint/baseline-*.xml`).
- `frontend-lint` (matrix `webvue3` + `karaoke-public`) —
  `tools/check-eslint-baseline.sh <spa>` (baseline-aware)
  + `npx prettier --check` (strict).
- `docs-lint` — `lychee --offline` + `tools/check-feature-doc.sh docs/features/*.md`.

Любое увеличение baseline-метрик (новые нарушения сверх baseline) — блокер.
Workflow запускается на push в `master` и pull_request в `master`.

**Связанные инварианты**: [`specs/001-code-standards-docs/spec.md#sc-002`](../specs/001-code-standards-docs/spec.md), [`.github/workflows/lint.yml`](../.github/workflows/lint.yml), [`docs/features/ci-lint-enforcement.md`](../docs/features/ci-lint-enforcement.md)

---

### precommit-add-new-hook: Добавление нового хука

**Severity**: SHOULD
**Section**: tooling
**Enforced by**: code-review-only

Новый pre-commit-хук добавляется через PR в `.pre-commit-config.yaml`:
1. Хук объявлен в `repos[].hooks[]`.
2. Указан `id`, `name`, `entry`, `language`, `files` (опц.).
3. Для multi-file проверок — `pass_filenames: false` + `entry: bash -c '...'`.
4. Хук протестирован локально (`pre-commit run --all-files`).

---

## Обновление этого документа

**Governance**:
- Изменения через PR с review от тимлида.
- Новые MUST-правила → semver MINOR для `constitution.md` (если правило
  повышает уровень governance).
- Deprecation правила → оставить `<!-- DEPRECATED: ... -->` + новое правило
  рядом, удалить в следующем релизе.

**Семантика MUST/SHOULD/MAY** (RFC 2119):
- **MUST** — обязательно, нарушение = блокер.
- **SHOULD** — настоятельно рекомендуется, исключения возможны, но
  обосновываются в PR.
- **MAY** — на усмотрение разработчика, не enforced.

**Шаблон нового правила** (см. `contracts/code-style-doc.md`):
```markdown
### <kebab-case-id>: <Краткое название>

**Severity**: MUST | SHOULD | MAY
**Section**: <kotlin|vue|sql|json|markdown|shell|gradle|docker>
**Enforced by**: <ktlint|eslint|prettier|pre-commit|code-review-only>

<Описание>

**Правильно** (path/to/file.ext:NN):
```kotlin
// пример
```

**Неправильно**:
```kotlin
// антипример
```

**Рациональ**: <почему>

**Связанные инварианты**: [`<file>#<anchor>`](<path>)

---

## Приложение A. Отключённые правила ktlint

В ktlint **1.7.1** (текущая версия) autofix **не работает** для
большинства правил (ktlint 0.x умел, 1.x — нет). Чтобы baseline был
"лечимым" без ручной правки сотен строк, **часть стилистических правил
отключена** в `.editorconfig`. Эти правила не ловят баги — только стиль.

| Правило | Причина отключения | Альтернатива |
|---------|---------------------|--------------|
| `no-wildcard-imports` | Не autofixable; 77 в baseline. IntelliJ IDEA сворачивает в wildcard автоматически. | Соблюдать вручную при добавлении нового импорта. |
| `argument-list-wrapping` | Не autofixable; 54 в baseline. | Соблюдать вручную. |
| `function-literal` | Не autofixable; 24 в baseline. | Соблюдать вручную. |
| `parameter-list-wrapping` | Не autofixable; 11 в baseline. | Соблюдать вручную. |
| `function-signature` | Не autofixable; 6 в baseline. | Соблюдать вручную. |
| `no-consecutive-comments` | Не autofixable; 1 в baseline. | Соблюдать вручную. |
| `import-ordering` | Не autofixable; 1 в baseline. | Соблюдать вручную. |
| `trailing-comma-on-call-site` | Зависит от `wrapping`. | Соблюдать вручную. |
| `trailing-comma-on-declaration-site` | Зависит от `wrapping`. | Соблюдать вручную. |

**Когда включать обратно**: после выхода ktlint с работающим autofix
для Kotlin 2.2 (отслеживать [ktlint releases](https://github.com/pinterest/ktlint/releases))
или после миграции на альтернативный инструмент (например, diktat).

## Приложение Б. Отключённые правила ESLint

| Правило | Файл | Причина отключения |
|---------|------|---------------------|
| `vue/require-explicit-emits` | `webvue3/.eslintrc.cjs`, `karaoke-public/.eslintrc.cjs` | 50 в baseline. Стилистическая рекомендация (явная декларация emits для IDE автодополнения), не баг. Рекомендуется добавлять `emits: [...]` в новых компонентах. |
| `vue/no-template-shadow` | `webvue3/.eslintrc.cjs` | 1 в baseline. Стилистическое (имя переменной в `<template>` совпадает с внешней). |
| `vue/multi-word-component-names` | оба | Имя компонента может быть одним словом (Vue-компоненты админки). |
| `vue/singleline-html-element-content-newline` | оба | Конфликтует с Prettier. |
| `vue/html-indent` | оба | Конфликтует с Prettier. |

**Когда включать `vue/require-explicit-emits` обратно**: добавить
`emits: [...]` в каждый компонент с emit() (50 файлов) → удалить строку
`'vue/require-explicit-emits': 'off'` → регенерировать baseline.
```
