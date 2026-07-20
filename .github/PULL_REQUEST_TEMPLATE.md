<!-- PR-шаблон для проекта Karaoke.
     GitHub подхватывает файл автоматически из .github/PULL_REQUEST_TEMPLATE.md
     при создании PR через UI. -->

## Что меняет этот PR

<!-- Краткое (1-3 предложения) описание сути изменений. -->

## Тип изменения

- [ ] Новая фича (одна из 9 ключевых подсистем из [docs/features/README.md](./docs/features/README.md))
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
