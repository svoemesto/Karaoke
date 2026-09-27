<!-- PR-шаблон для проекта Karaoke.
     GitHub подхватывает файл автоматически из .github/PULL_REQUEST_TEMPLATE.md
     при создании PR через UI. -->

## Что меняет этот PR

<!-- Краткое (1-3 предложения) описание сути изменений. -->

## Тип изменения

- [ ] Новая фича (одна из 9 ключевых подсистем — список ключей: [контракт per-feature-doc](../specs/001-code-standards-docs/contracts/per-feature-doc.md))
- [ ] Исправление бага
- [ ] Рефакторинг / приведение к стандартам
- [ ] Обновление документации
- [ ] Другое (опишите)

## Чек-лист (FR-009 + CONTRIBUTING.md)

### Документация

- [ ] Если меняется код **одной из 9 ключевых подсистем** (FR-004), соответствующий
      per-feature документ в `docs/features/` обновлён/создан в этом же PR
      (FR-009).
- [ ] Ссылки на ключевые классы в per-feature документе обновлены и
      валидны (`./tools/verify-kotlin-refs.sh`).
- [ ] Если добавляется новое MUST-правило — `CONTRIBUTING.md` обновлён
      + semver MINOR для `constitution.md`.

### Линтеры

- [ ] `./gradlew ktlintCheck detekt` — SUCCESS (или известный baseline).
- [ ] `cd webvue3 && npm run lint:check` — SUCCESS.
- [ ] `cd karaoke-public && npm run lint:check` — SUCCESS.
- [ ] `./tools/baseline-stats.sh` — не увеличилось количество нарушений.

### Документ-линк-чекер

- [ ] `./tools/verify-doc-links.sh docs/features/ CONTRIBUTING.md` — 0 errors.
- [ ] `./tools/check-feature-doc.sh docs/features/*.md` — 0 errors.

### Миграции БД (FR-013, см. `.specify/memory/constitution.md` §III)

- [ ] Если PR вводит новые миграции в `deploy/karaoke-db/` — миграция применена
      **на LOCAL и на PROD** (обе БД имеют новые колонки/таблицы).
      Проверка: `\\d tbl_<name>` на обеих сторонах показывает одинаковую схему.
      (Sync-механизм оперирует записями, не DDL — рассинхрон схемы не
      восстанавливается автоматически.)

### Knowledge SSoT (Tier-1 AGENTS.md, см. [knowledge/README.md](../knowledge/README.md))

- [ ] Если меняется bounded context (`Song`, `Album`, `KaraokeVideo`, и т.п.) —
      `knowledge/domains/<domain>/domain.md` и затронутые
      `knowledge/domains/<domain>/components/*.md` обновлены/созданы в этом же PR.
- [ ] Если меняется C4 уровень (новый контейнер/компонент) — соответствующий
      документ в `knowledge/system/` обновлён.
- [ ] Если добавляется новая фича — per-feature документ `docs/features/<slug>.md`
      создан по [контракту per-feature-doc](../specs/001-code-standards-docs/contracts/per-feature-doc.md)
      (6 обязательных секций, шапка `Status`/`Feature Key`/`Last Updated`).
- [ ] `bash tools/check-knowledge-structure.sh` — **9/9 PASS**.
- [ ] `bash tools/check-knowledge-cross-links.sh` — **0 broken**.
- [ ] `python3 tools/lint-knowledge.py` — 0 violations (без `--baseline`).
- [ ] `python3 tools/check-doc-references.py` — 0 битых ссылок/путей/эндпоинтов.
- [ ] `bash tools/check-feature-doc.sh docs/features/*.md` — все документы валидны.
- [ ] `bash tools/validate-mermaid.sh` — 0 issues (если есть mermaid-блоки).
- [ ] `bash tools/test-livedocs.sh` — все self-tests PASS.

### Constitution Check

- [ ] Все 6 принципов в `.specify/memory/constitution.md` (I-VI) соблюдены.
- [ ] Если есть нарушения — обоснование в `plan.md` → секция «Complexity Tracking».

### KDoc / JSDoc (FR-006)

- [ ] Новые/изменённые публичные API имеют KDoc/JSDoc с `@see docs/features/<slug>.md`.
- [ ] `./tools/generate-docs.sh` отработал без ошибок.

## Как тестировать

<!-- Шаги для ревьюера: что сделать, чтобы убедиться, что PR работает.
     Например: "1. Запустить ./gradlew bootRun 2. Открыть webvue3 3. ..." -->

## Связанные задачи

<!-- Ссылки на issue, связанные PR, memory-файлы. Например:
     Closes #123
     Related: .specify/memory/feedback_xxx.md
-->

## Скриншоты / логи (опц.)

<!-- Если меняется UI — приложите скриншот. Если сложный баг — приложите лог. -->
