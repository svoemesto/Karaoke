# Research: Удалить блок «Ссылки на просмотр» со страницы песни

> Phase 0 output для спеки `specs/142-remove-watch-links-block/`.
> Сгенерировано `/speckit.plan` 2026-08-04.

## Резюме

Задача — **одно-файловая удаляющая правка** Vue-SFC. Архитектурно нового ничего
не вводится, нет новых компонентов, нет новых API, нет новых БД-полей,
нет новых сборщиков/инструментов. Никаких NEEDS CLARIFICATION в Technical
Context не появилось — все спорные моменты уже зафиксированы в спеке
(раздел `Assumptions` и `Out of Scope`).

Соответственно, классическое research-дерево «изучить, выбрать лучшую
альтернативу» здесь сводится к **документированию отсутствия открытых
решений** и фиксации нескольких рисков, обнаруженных при анализе исходников.

## Решения (однозначные, без NEEDS CLARIFICATION)

### Decision 1: Удалить только `SongView.vue`, не трогать `PlatformLink.vue`

- **Decision**: удалить DOM-блок `.km-links-card` + локальный CSS + импорт `PlatformLink`
  из `karaoke-public/src/views/SongView.vue`. Сам `PlatformLink.vue` и поля
  `Song.link*` оставить без изменений.
- **Rationale**: `PlatformLink` используется ещё в двух публичных view
  (`SearchView.vue`, `ZakromaView.vue`) — см. `grep -rn "PlatformLink"
  karaoke-public/src/views/`. Если удалить компонент целиком — сломаются
  результаты поиска и Закрома (см. AGENTS.md, раздел «Двух-фронтенд» —
  смешивание ответственности запрещено, но мы ничего не смешиваем: правка
  только в одном view). Поля `Song.link*` сохраняем для возможного будущего
  использования в этих view (и на случай отката).
- **Alternatives considered**:
  - «Удалить компонент `PlatformLink.vue` целиком + поля `link*`» — отклонено:
    сломает `SearchView.vue` и `ZakromaView.vue`.
  - «Сделать `v-if=false` через условие в SongView» — отклонено: пользователь
    явно сказал «убрать», а не «скрыть за флагом». Заглушенный код
    остаётся в репо и всплывёт позже (техдолг).
  - «Сделать админский toggle «показывать ссылки»» — отклонено: согласно
    спеке, Out of Scope.

### Decision 2: Удалить весь связанный CSS (`km-links-*`, `km-link-*`) из `SongView.vue`

- **Decision**: удалить CSS-правила `.km-links-card`, `.km-links-title`,
  `.km-links-grid`, `.km-link-group`, `.km-link-label`, `.km-link-icons` +
  адаптивное `@media .km-links-grid { gap: 0.5rem }`.
- **Rationale**: эти правила объявлены только в `SongView.vue` (`grep -rn
  "km-links-\|km-link-" karaoke-public/src/` должен показать
  единственный файл — `SongView.vue`, блок в 199–334 и CSS в 927–961/1177).
  После удаления DOM-блока эти стили станут мёртвым CSS (доп. baseline-
  шум, ESLint не валит, но `stylelint`/visual-review потом заметят).
- **Pre-чек перед удалением** (`tasks.md`, шаг 1):
  ```bash
  grep -n "km-link" karaoke-public/src/views/SongView.vue
  ```
  Если в выводе окажутся строки ВНЕ удаляемого блока (199–334) — оставить
  CSS и пересмотреть план. Это перенесено в `plan.md → Open Questions /
  Carry-over` и явно войдёт в `tasks.md`.
- **Alternatives considered**:
  - «Оставить CSS на случай отката» — отклонено: откат делается через
    `git revert` PR (полный возврат DOM-блока вместе с CSS), держать
    мёртвые стили из-за теоретического отката — не наш стиль (AGENTS.md,
    «Модули — karaoke-public dual design»).

### Decision 3: Деплой — через `deploy/do.sh build_start_public`, как обычно

- **Decision**: после мержа — пользователь запускает `bash do.sh
  build_start_public` из `deploy/`, агенту деплой запрещён (AGENTS.md,
  «Деплой» + Constitution §V ст. 2 «Категорически запрещено агенту»).
- **Rationale**: локально (admin-машина) пользователь — единственный, кто
  правит образы `karaoke-public` для прода; агент собирает, но не
  деплоит. На `dev-pc` под пользователем `dev` (Constitution §V ст. 6) —
  разрешено пересобирать и перезапускать ЛОКАЛЬНЫЙ контейнер, но даже там
  явного согласия на пуш в прод нет без отдельного запроса.
- **Alternatives considered**:
  - «Попросить агента собрать и подсказать команду» — план так и делает
    (см. шаг 7 в `plan.md → План реализации`).

## Не-решения (исследовать дальше не нужно)

- **Никаких новых сторонних зависимостей** (`package.json` не меняется).
- **Никаких изменений API** (`/api/public/song` возвращает то же JSON; DTO
  не трогается; `link*`-поля остаются в ответе).
- **Никаких изменений БД** (DDL/DML/миграций нет; `SyncRegistry.all`
  остаётся прежним; `recordhash`-триггеры не пересоздаются).
- **Никаких изменений backend** (`karaoke-app`, `karaoke-web` не
  пересобираются).
- **Никаких новых env-переменных / секретов / конфигов** (Constitution
  §VIII — N/A).
- **Никаких per-feature документов** в `docs/features/` — задача не
  относится к 9 ключевым подсистемам (см. Constitution §VI ст. FR-009
  в `plan.md → Constitution Check`).
- **Никаких изменений в CI** (`lint.yml` уже прогоняет 7 проверок,
  включая ESLint для `karaoke-public`; новая правка должна остаться в
  зелёном baseline — мы код УДАЛЯЕМ, не добавляем, так что baseline
  должен сократиться или остаться прежним; см. SC-007 ниже).

## Риски и проверки (carry-over в `tasks.md`)

| ID | Риск | Митигация | Где в плане |
|----|------|-----------|-------------|
| R-001 | `km-link-*` классы случайно используются в `SongView.vue` вне удаляемого блока | Pre-чек `grep` (см. `plan.md → Open Questions`) | task #1 |
| R-002 | Pre-commit `git ls-files \| grep ...` ловит случайный `.env`-коммит | Уже зелёный: правка не вводит секрет-файлов | Constitution §VIII.3 |
| R-003 | Baseline (`karaoke-public/.eslint-baseline.json`) вырастет после удаления кода | Проверить `npm run lint:check` после правки: новых `error/warning` не должно быть; baseline может только СОКРАТИТЬСЯ (если правка убрала нарушения). Если вырос — перегенерировать baseline через `bash tools/generate-eslint-baseline.sh karaoke-public`. | post-task lint |
| R-004 | После деплоя на проде блок всё ещё виден из-за кеша CDN/браузера | Проверить `Content-Type: text/html` и отсутствие `.km-links-card` через `curl -s https://sm-karaoke.ru/song?id=<id_эфирной> \| grep km-links-card` (должен вернуть пусто). Если нет — принудительный Ctrl+Shift+R на клиенте. | post-deploy |
| R-005 | `SearchView.vue` или `ZakromaView.vue` перестали работать (например, если правка `SongView.vue` непреднамеренно затронула общий импорт) | Эти файлы НЕ редактируются — мы меняем только `SongView.vue`. Проверить `grep -rn "PlatformLink" karaoke-public/src/` после правки — должны остаться только `SearchView.vue` и `ZakromaView.vue`. | quickstart, SC-005 |

## Архитектурные альтернативы (отклонёнены, документированы для истории)

1. **«Сделать дизайн-флагами» (`:class="{ 'km-links-card--hidden': !showExternalLinks }`)** — отклонено: пользователь хочет УБРАТЬ, а не СКРЫТЬ за флагом; лишняя семантика в коде.
2. **«Перенести блок в отдельный `<WatchLinks>` компонент»** — отклонено: компонент создаётся, чтобы его переиспользовать; переиспользовать его негде (только эта view); получает один компонент ради одного использования — анти-паттерн.
3. **«Сделать настройку в `tbl_public_settings` для A/B-теста»** — отклонено: спекой явно сказано, решение пользователя окончательное, без A/B (см. `spec.md → Out of Scope`).

## Вывод

Никаких технических NEEDS CLARIFICATION не осталось. Все спорные решения
зафиксированы в спеке и подтверждены этим research-документом. Можно
переходить к Phase 1 (`data-model.md`, `contracts/`, `quickstart.md`).
