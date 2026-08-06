# Implementation Plan: Актуализация статусов и цветов песен

**Branch**: `155-song-state-colors` | **Date**: 2026-08-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/155-song-state-colors/spec.md`

## Summary

Заменить устаревшую классификацию `SongState`, основанную на наличии публикаций в социальных каналах, на пять производных состояний онлайн-плеера: `DONE`, `TODAY`, `ON_AIR`, `EXCLUSIVE`, `IN_WORK`. Пересчитать `Song.state` по готовности, `free`, расписанию эфира и текущему времени, передавать новые цвета через существующие DTO и обновить кнопки выбора состояния на экране публикаций.

Технический подход: сохранить состояние и цвет производными значениями без изменения схемы БД; вынести расчёт с явным временем в тестируемый внутренний путь; удалить старые ветви `SongState` и старый fallback цвета по `idStatus`; перевести endpoint выбора даты и Vue-кнопки на пять новых токенов; покрыть матрицу JUnit-тестами и ручным quickstart-сценарием.

## Technical Context

**Language/Version**: Kotlin 2.2.20 / JDK 17 для backend; JavaScript и Vue 3 для `webvue3`.

**Primary Dependencies**: Spring Boot 3.5.6, Kotlin/JUnit 5 через `spring-boot-starter-test`, Vue 3, Vuex 4, Vite, ESLint.

**Storage**: PostgreSQL через существующий raw JDBC слой. Миграции, новые колонки и изменения recordhash не требуются.

**Testing**: Офлайн unit-тесты JUnit 5 в `karaoke-app`; `compileKotlin`, `ktlintCheck`, `webvue3` `npm run lint:check`, pre-commit и ручная проверка по `quickstart.md`.

**Target Platform**: Linux admin backend и административный SPA `webvue3`. `karaoke-public` не изменяется.

**Project Type**: Многомодульное web-приложение с Kotlin backend и Vue admin frontend.

**Performance Goals**: Расчёт состояния — O(1) на песню, без дополнительных SQL-запросов и N+1; сохранить обработку каталога 18k+ песен в существующем времени загрузки.

**Constraints**: Не добавлять внешние runtime-интеграции, persistence-поля или sync-target; использовать московский часовой пояс для расписания; `IN_WORK` передаёт пустой цвет без старого fallback по `idStatus`; не менять самостоятельные статусы процессов и каналов публикации.

**Scale/Scope**: Один enum и один расчёт состояния в `karaoke-app`, существующие `SongDTO`/`SongDTOdigest`/`PublicationDTO`, endpoint `POST /api/publications/date`, экран публикаций `webvue3`, один offline test class и per-feature документация.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | До исследования | После дизайна | Обоснование |
|---|---|---|---|
| I. Self-contained pipeline | PASS | PASS | Фича не добавляет внешние сервисы и не меняет медиа-пайплайн. |
| II. Raw JDBC и hash-diff | PASS | PASS | Новых DB-запросов и ORM нет; состояние вычисляется из уже загруженной песни. |
| III. Dual DB sync | PASS | PASS | Нет новых колонок/сущностей; виртуальный цвет не требует sync-target, флагов или миграции. |
| IV. Async queue | PASS | PASS | `ProcessBuilder`, очереди и длительные операции не затрагиваются. |
| V. Admin/public separation | PASS | PASS | Изменения UI ограничены `webvue3`; `karaoke-public` не получает admin-логику. |
| VI. Code standards | PASS | PASS | Новый/изменённый публичный код получает KDoc/JSDoc и ссылку на `docs/features/song-state-colors.md`; добавляются unit-тесты и обязательные lint/coverage-проверки. |
| VII. Cross-machine setup | PASS | PASS | Личные конфиги, ветки других агентов и секреты не затрагиваются. |
| VIII. Secrets and git hygiene | PASS | PASS | Нет env-файлов, ключей, токенов или операций с секретными данными. |

Нарушений, требующих Complexity Tracking, нет.

## Project Structure

### Documentation (this feature)

```text
specs/155-song-state-colors/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── song-state-color.md
│   └── publications-date-filter.md
└── tasks.md                 # создан этой же сессией через /speckit.tasks
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── SongState.kt          # пять значений и канонические цвета
│   ├── Song.kt               # расчёт state и производного color
│   └── Publication.kt        # актуализация устаревшего описания состояния
└── controllers/
    └── ApiController.kt      # /api/publications/date: пять новых токенов

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/
└── SongStateTest.kt          # offline JUnit 5 матрица состояний

webvue3/src/components/Publish/
├── components/
│   └── PublishTableFooter.vue # пять кнопок и безопасная обработка пустой даты
└── store.js                    # проверка/сохранение контракта параметра при необходимости

docs/features/
├── song-state-colors.md      # per-feature документ с инвариантами и ловушками
├── songs-table.md            # точечная ссылка на новый контракт цвета
└── README.md                 # регистрация per-feature документа
```

`SongDTO.kt`, `SongDTOdigest.kt`, `PublishTableBodyTd.vue` и `SongsTable.vue` проверяются как потребители существующего поля `color`; их структура сохраняется, если контракт уже выполняется после изменения backend.

**Structure Decision**: сохранить текущую многомодульную структуру: производная доменная логика остаётся в `karaoke-app`, административное отображение и выбор даты — в `webvue3`, публичный SPA и схема данных не меняются. Контракты и дизайн-артефакты лежат в feature-директории.

## Implementation Sequence

### Phase 0: Research

1. Зафиксировать в `research.md` источник истины, приоритеты состояний, семантику `isFreelyAvailableNow`, отсутствие миграции и тестовую стратегию.
2. Проверить все ссылки на старые `SongState`/`STATE_*`/`STATUS_*`, не удаляя независимые статусы процессов и каналов.
3. Использовать результаты как основание для задач backend, frontend, тестов и документации.

### Phase 1: Backend model and color propagation

1. В `SongState.kt` оставить только пять enum-значений с цветами из FR-002–FR-006 и обновить KDoc-ссылку на per-feature документ.
2. В `Song.kt` заменить старое дерево условий по Telegram/VK/Dzen/Sponsr/PL на детерминированное вычисление по `idStatus`, `free`, валидному `dateTimePublish` и текущему времени в `Europe/Moscow`.
3. Сохранить приоритеты: `IN_WORK`, затем постоянная бесплатность/действующее окно `ON_AIR`, затем отсутствие расписания `EXCLUSIVE`, сегодняшний будущий эфир `TODAY`, остальные готовые песни `DONE`.
4. Передавать `song.state.color` напрямую при загрузке списка; убрать подстановку старой палитры по `idStatus`, чтобы `IN_WORK` оставался без специального фона.
5. Не менять поля `SongDTO.color`/`SongDTOdigest.color`, формат `PublicationDTO` и процессные `processColor*` — они должны автоматически получить новые производные значения.

### Phase 2: Endpoint and admin UI

1. В `ApiController.getPublicationsDateFrom` удалить ветви старых состояний и принимать только пять новых `STATE_*` токенов; `STATE_TODAY` должен искать именно `SongState.TODAY`.
2. Для `STATE_IN_WORK` использовать одну категорию без отдельных кнопок `STATUS_0..6`; неизвестные/старые токены возвращают пустую строку и не считаются частью нового контракта.
3. В `PublishTableFooter.vue` заменить старую легенду на пять кнопок с цветами и названиями из спеки.
4. Сохранить существующий сценарий кнопок как выбор даты начала диапазона. Для состояний без дат (`EXCLUSIVE`, `IN_WORK`) обработать пустой ответ endpoint без передачи пустой даты в арифметику дат и без ошибки интерфейса.
5. Проверить, что `PublishTableBodyTd.vue` и `SongsTable.vue` продолжают использовать только `color`, а `Publish/store.js` не содержит старых токенов.

### Phase 3: Tests and documentation

1. Добавить `SongStateTest.kt` с фиксированным `now` и тестами всех пяти состояний, приоритетов и временных границ.
2. Добавить `docs/features/song-state-colors.md` с правилами классификации, таблицей цветов, контрактом производного цвета, границами и известными ловушками.
3. Обновить `docs/features/README.md` и `docs/features/songs-table.md` ссылками на новый контракт; не менять документацию независимых статусов публикации.
4. Выполнить проверки из `quickstart.md`, включая per-feature structure check, Kotlin/JS lint, KDoc/JSDoc coverage и pre-commit.

## Design Artifacts

- `research.md` — решения и отклонённые альтернативы.
- `data-model.md` — входные атрибуты, порядок классификации, переходы и не в скоупе.
- `contracts/song-state-color.md` — контракт цветов DTO.
- `contracts/publications-date-filter.md` — контракт токенов endpoint выбора даты.
- `quickstart.md` — автоматическая и ручная валидация.

## Complexity Tracking

Нарушений Конституции нет; дополнительных архитектурных компонентов, persistence-слоёв или внешних интеграций не добавляется.
