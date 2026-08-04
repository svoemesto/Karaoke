# Implementation Plan: Удалить блок «Ссылки на просмотр» со страницы песни

**Branch**: `142-remove-watch-links-block` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/142-remove-watch-links-block/spec.md`

**Note**: Этот план заполняется командой `/speckit.plan`. Структура соответствует
`/home/nsa/Karaoke/.specify/templates/plan-template.md` (см. версию 0.13.0).

## Summary

Одно-файловая правка фронтенда: удалить блок `.km-links-card` (DOM + локальные CSS +
неиспользуемый импорт `PlatformLink`) из `karaoke-public/src/views/SongView.vue`.
Никаких изменений в backend (`karaoke-app`, `karaoke-web`), БД, `SyncRegistry`,
DTO, API-эндпоинтах и `webvue3`.

Подход — буквальное удаление кода, без флагов, заглушек и A/B-вариантов.
Источники данных (`Song.link*`-поля) и переиспользуемый компонент
`PlatformLink.vue` остаются: они нужны `SearchView.vue` и `ZakromaView.vue`.

## Technical Context

**Language/Version**: Vue 3.5 SFC (Composition API + `<script>` блок) + JavaScript
(см. `karaoke-public/package.json`); стиль — CSS в `<style scoped>` (`scoped`-CSS
через атрибут, см. AGENTS.md «karaoke-public dual design»).
**Primary Dependencies**: Vue 3.5 (`vue: ^3.5.21`), Vite 5 (`npm run build`),
Bootstrap 5 (`bootstrap: ^5.3.8`), Vue Router 4 (`vue-router: ^4.5.1`), Vuex 4
(`vuex: ^4.1.0`); касается только `karaoke-public` модуля.
**Storage**: N/A — никаких изменений данных, SQL/DDL/миграций нет.
**Testing**: N/A автоматизированных тестов. CI содержит только `frontend-lint`
(см. `docs/features/ci-lint-enforcement.md`); ручная проверка в `quickstart.md`.
**Target Platform**: статика, раздаваемая nginx на проде (`188.119.64.111`,
контейнер `karaoke-public`); см. AGENTS.md «Деплой».
**Project Type**: Vue 3 SPA (frontend-часть web-приложения), часть
мульти-модульного Gradle-репо (`karaoke-app`, `karaoke-web`, `webvue3`,
`karaoke-public`).
**Performance Goals**: N/A — удаление кода уменьшает payload статики
(~15 `PlatformLink`-узлов + ~30 строк CSS).
**Constraints**: 
- Никаких визуальных заглушек (NFR-001).
- Никаких изменений `PlatformLink.vue`, `SearchView.vue`, `ZakromaView.vue`,
  backend, БД (FR-003, FR-004, NFR-002).
**Scale/Scope**: 1 файл (`karaoke-public/src/views/SongView.vue`), ~135 строк
DOM-блока + ~40 строк CSS + 2 строки импорта/регистрации. Проверки после
правки — ручные (3 сценария) + `grep`/`npm run build`.

## Constitution Check

*GATE: должен пройти до Phase 0 research. Re-check после Phase 1 design — см.
раздел «Re-evaluation» ниже.*

| Principle | Применимость | Статус | Комментарий |
|-----------|--------------|--------|-------------|
| **I. Self-contained автопайплайн** | N/A | ✅ pass | Не затрагивает ffmpeg/Melt/Demucs/Sheetsage; фронтенд-only |
| **II. Сырой JDBC + дифф по хэшам** | N/A | ✅ pass | Нет обращений к БД |
| **III. Двух-БД синхронизация через SyncRegistry** | N/A | ✅ pass | `SyncRegistry.all` не меняется |
| **IV. Async-очередь задач с парсингом stdout** | N/A | ✅ pass | Нет `KaraokeProcess*` |
| **V. Двух-фронтенд** | ✓ | ✅ pass | Правка строго в `karaoke-public` (публичный SPA); `webvue3` не трогаем |
| **VI. Code Standards** | ✓ | ✅ pass | См. ниже под-проверки |
| ↳ FR-006 KDoc/JSDoc | ✓ | ✅ pass | `SongView.vue` — page-level view (не `export default Vue-компонент`); `PlatformLink.vue` не меняется; новых публичных API нет |
| ↳ FR-007 линтеры/pre-commit | ✓ | ✅ pass | `karaoke-public` ESLint baseline не должен расти (мы удаляем код, а не добавляем); пройти `npm run lint:check` без новых нарушений |
| ↳ FR-009 per-feature документ | ✓ | ✅ pass | Правка не затрагивает 9 ключевых подсистем из `docs/features/README.md`. Ближайший — `player-transpose` (онлайн-плеер), но мы не трогаем транспонирование/плеер-код, только соседний UI-блок в этом же файле. **Per-feature документ НЕ требуется** |
| **VII. Cross-Machine Setup** | ✓ | ✅ pass | Не затрагивает `.git-blame-ignore-revs`, `.gitattributes`, локальные AI-конфиги |
| **VIII. Секреты и git-гигиена** | ✓ | ✅ pass | Секрет-файлы не задействованы; `pre-commit` VIII.3 (`git ls-files \| grep ...`) остаётся зелёным |

**Вердикт до Phase 0**: все gates PASS.

### Re-evaluation (после Phase 1 design)

**Вердикт после Phase 1**: gates остаются PASS (правка не вводит никаких
новых архитектурных решений — это удаление уже существующего кода). Никакой
записи в Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```
specs/142-remove-watch-links-block/
├── plan.md              # Этот файл
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1 (см. N/A ниже)
├── checklists/
│   └── requirements.md  # Уже создан на фазе `/speckit.specify`
└── spec.md              # Уже создан на фазе `/speckit.specify`
```

### Source Code (repository root)

**Изменяется только один файл:**

```
karaoke-public/
└── src/
    └── views/
        └── SongView.vue   # ЕДИНСТВЕННЫЙ затронутый файл
                            #   - ~135 строк DOM (.km-links-card, .km-link-group…)
                            #   - ~40 строк локального CSS (.km-links-*, .km-link-*)
                            #   - 2 строки (импорт + регистрация PlatformLink)
```

**НЕ затрагиваются** (намеренно):

```
karaoke-public/
└── src/
    ├── components/
    │   └── PlatformLink.vue       # используется SearchView, ZakromaView
    ├── views/
    │   ├── SearchView.vue         # продолжает импортировать PlatformLink
    │   └── ZakromaView.vue        # продолжает импортировать PlatformLink
    └── ...
```

```
backend (не затрагивается вовсе):
karaoke-app/
karaoke-web/
karaoke-public/src/services/api.js   # контракт /api/public/song не меняется
```

**Structure Decision**: веб-приложение (frontend + backend), правка строго в
одном `*.vue`-файле публичного SPA (`Option 2` из шаблона, но только
`frontend/`-секция). Backend, БД, админка — out of scope для этой фичи.

## Complexity Tracking

> Заполняется ТОЛЬКО при нарушениях Constitution Check.

Все gates PASS → таблица пуста.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## План реализации (для будущего `/speckit.tasks`)

Ниже — наметка задач, которые лягут в `tasks.md` после `/speckit.tasks`. На фазе
`speckit.plan` `tasks.md` НЕ создаётся (см. AGENTS.md выше и `plan-template.md`),
но фиксируем порядок тут для самоконтроля.

1. **Пре-чек (коммита не требует, но прогоняется локально)**:
   `grep -n "km-link" karaoke-public/src/views/SongView.vue` — проверить, что
   классы `km-link-*` не используются ЗА пределами удаляемого блока. Если
   используются — оставить соответствующие CSS-правила.
2. **Удалить DOM-блок**: `.km-links-card` со всем содержимым (строки ~201–333).
3. **Удалить CSS**: `.km-links-card`, `.km-links-title`, `.km-links-grid`,
   `.km-link-group`, `.km-link-label`, `.km-link-icons` + media-query
   `.km-links-grid { gap: 0.5rem }` в адаптивной секции (если нигде больше
   не используются — см. п. 1).
4. **Удалить импорт/регистрацию `PlatformLink`** в `<script>` (если других
   использований в файле нет — `grep -n PlatformLink SongView.vue` после шага 2
   должен быть пуст).
5. **Запустить**: `cd karaoke-public && npm run lint:check && npm run build`.
6. **Проверить по Quickstart**: 3 сценария + 5 success criteria.
7. **Деплой** — пользователь запускает `cd deploy && bash do.sh build_start_public`
   (см. AGENTS.md, раздел «Деплой» — делает только пользователь).
8. **PR + CI 7/7**: feature-ветка `142-remove-watch-links-block`, пуш,
   `gh pr create --base master`, дождаться `gh pr checks` = 7/7 PASS,
   `gh pr merge --merge --delete-branch`.
9. **Post-merge проверка на проде**:
   `curl -s https://sm-karaoke.ru/song?id=<id_эфирной> | grep km-links-card` —
   должен вернуть пусто (нет такого класса в HTML).

## Open Questions / Carry-over

Из спеки (раздел «Open Questions / Risks») переносится в таски:

- [ ] pre-commit шаг 1 — убедиться, что `km-link-*` классы не используются в
  `SongView.vue` за пределами удаляемого блока (проверять до удаления CSS).

## References

- Спека: [`spec.md`](./spec.md) — основной источник требований и acceptance criteria.
- Шаблон плана: `.specify/templates/plan-template.md`.
- Конституция: `.specify/memory/constitution.md` (v2.1.0, last amended 2026-08-03).
- AGENTS.md, разделы:
  - «Документация и иерархия» — иерархия документов и их приоритет.
  - «Деплой» — путь выкатки (`do.sh build_start_public`).
  - «karaoke-public dual design» — `table-layout: fixed`, `form-select`,
    `localStorage`-выбор дизайна.
- Релевантные файлы кода:
  - `karaoke-public/src/views/SongView.vue` — единственный затронутый.
  - `karaoke-public/src/components/PlatformLink.vue` — не затрагивается.
  - `karaoke-public/src/views/SearchView.vue`, `karaoke-public/src/views/ZakromaView.vue` — не затрагиваются.
- Per-feature документы: ни один из 9 не покрывает эту правку → новый
  per-feature документ НЕ создаётся (см. Constitution Check, FR-009).
