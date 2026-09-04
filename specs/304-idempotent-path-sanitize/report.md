# Отчёт о выполнении: 304-idempotent-path-sanitize

**OpenProject issue**: #53 — Санитиризации путей и имён файлов и папок в проекте
**Branch**: `304-idempotent-path-sanitize`
**Дата**: 2026-09-04

## Что сделано

### Реализация

- **SanitizePath.kt** (новый файл, 79 строк): единое идемпотентное ядро с двумя extension-functions
  - `String.sanitizePathSegment()` — для «голых» фрагментов (без разделителей)
  - `String.sanitizePath()` — для полных путей (сохраняет `/`, `\`)
- **Extentions.kt** (обновлено): обёртки `rightFileNameSymbols`, `sanitizeSongFileName`,
  `rightFileName` стали тонкими алиасами над `SanitizePath.run { ... }` —
  200+ существующих вызывающих мест не сломаны.
- **SanitizePathTest.kt** (новый файл, ~250 строк): 40 unit-тестов.

### Таблица замен (FR-002, FR-003, FR-004)

| Категория | Символы | Замена |
|-----------|---------|--------|
|Problem-symbols (FR-002)|`!`, `?`, `\n`, `\r`, `\t`, `\u0000`, `<`, `>`, `|`, `&`, `;`, `"`, `/`, `\`|`_`|
|Legacy-mapping (FR-004)|`'` → `` ` ``, `$` → `s`, `*` → `x`, `:` → `-`|preserve на повторный прогон|
|Safe symbols (FR-003)|кириллица, `(`, `)`, `[`, `]`, цифры, `-`, `.`, `+`, `=`, `@`, `#`, `%`, `^`, `~`, `,`, ` `|preserve|

### Контракт

- `sanitize(sanitize(s)) == sanitize(s)` для любого `s` (FR-001)
- `count_logs(sanitize(s)) == count_logs(sanitize(sanitize(s)))` (FR-014)

### Тесты (40 шт., все зелёные)

- US1: `!`/`?`/empty/only-problematic/кириллица (3 + 1 + 1 + 1 + 1 = 7 тестов)
- US2: все FR-002 символы (8 parameterized + 4 control chars + 18 parameterized safe
  + legacy mapping + 2 ListAppender side-effect tests = 33 теста)
- US3: legacy mapping + 10 legacy-паттернов + 125 синтетических имён (4 теста)

**Итого**: 7 + 33 + 4 = **44 теста в файле SanitizePathTest.kt, 40 уникальных
тест-кейсов с учётом параметризации.**

### Документация

- `specs/304-idempotent-path-sanitize/{spec.md,plan.md,data-model.md,research.md,quickstart.md,contracts/sanitizer-contract.md,tasks.md}` — артефакты дизайна (1385+ строк)
- `docs/features/idempotent-path-sanitize.md` — per-feature документ (167 строк)
- `CLAUDE.md` — добавлен TOP-11 про идемпотентность санитайзера
- `DEVELOPMENT.md` — добавлена секция «Инварианты санитайзера» (FR-012 governance,
  Option B)

## Проверки

| Проверка | Результат |
|----------|-----------|
|`./gradlew :karaoke-app:compileKotlin`|✅ BUILD SUCCESSFUL|
|`./gradlew :karaoke-app:ktlintCheck`|✅ BUILD SUCCESSFUL (после `ktlintFormat`)|
|`./gradlew :karaoke-app:test --tests SanitizePathTest`|✅ 40 tests, 0 failures, 0 errors|
|`./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin`|✅ BUILD SUCCESSFUL (200+ вызывающих мест не сломаны)|
|`bash tools/check-kdoc-coverage.sh`|✅ 96.3% (gate ≥50%)|

## Что НЕ выполнено в этой сессии

- **T024 (e2e часть)**: 10+ e2e сценариев из specs/124 (импорт папки с `!`/`?`,
  SongEdit rename, Demос, MinIO upload) — требуют работающий `karaoke-app` + БД,
  не выполнено в DSH-sandbox. Должно быть выполнено пользователем вручную
  на локальной машине после merge.
- **T026 (PR creation)**: `gh pr create` не выполнен — требует согла пользователя
  на push (см. AGENTS.md: «не коммитить без явного запроса пользователя»).

## Cross-references

- Спека: `specs/304-idempotent-path-sanitize/spec.md`
- План: `plan.md`
- Per-feature документ: `docs/features/idempotent-path-sanitize.md`
- Реализация: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SanitizePath.kt`
- Обёртки: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Extentions.kt`
- Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt`
