# Implementation Plan: 554 — Закрыть публичный доступ к `POST /changerecords`

**Branch**: `554-close-public-sql-endpoint` | **Date**: 2026-10-24 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/554-close-public-sql-endpoint/spec.md`

## Summary

Эндпоинт `POST /changerecords` на `karaoke-web` — по контракту внутренний server-to-server канал
двуx-БД синхронизации песен, но сейчас доступен анонимно из интернета и исполняет SQL-действия,
присланные клиентом. Правка закрывает его двумя рубежами, оба по образцам, уже принятым в проекте:

1. **Сетевой рубеж (nginx)** — `location /changerecords` получает список разрешённых адресов
   админской машины (`allow` + `deny all`), ровно как уже сделано для внутренних портов 5433 (прод-БД)
   и 9000 (MinIO) в `deploy/prod-single-host/nginx.conf`.
2. **Рубеж приложения (внутренний ключ)** — обработчик требует заголовок `X-Internal-Secret`
   по образцу `InternalStemJobController` (fail-safe: при пустом секрете доступ закрыт всегда),
   а клиент синхронизации начинает его отправлять.

Дополнительно: при отказе наружу не возвращается текст ошибки БД, а попытка пишется в журнал
категории безопасности. Текущая транзакционность применения действий и формат тела запроса
не меняются (транзакционность — отдельная задача BR-012).

## Technical Context

**Language/Version**: Kotlin 1.8+ / Spring Boot 3.5.6 (`karaoke-web`, `karaoke-app`), nginx (прод-контур)

**Primary Dependencies**: Spring Web (`@RestController`), Spring Security (цепочка `karaoke-web`),
`java.net.http.HttpClient` (клиент синхронизации), nginx `ngx_http_access_module`

**Storage**: PostgreSQL 16 (сырой JDBC, ADR-0001) — правка storage не меняет

**Testing**: JUnit 5 (`karaoke-web/src/test/kotlin/...`), unit-тесты клиента — `karaoke-app/src/test/kotlin/...`;
nginx-конфигурация — проверка `nginx -t` и регрессионный тест на стороне приложения

**Target Platform**: Linux-сервер (прод-контур `sm-karaoke.ru`), админская машина `nsa-i9` — клиент синхронизации

**Project Type**: web-service (backend + edge proxy)

**Performance Goals**: задержка легитимного обмена синхронизации не растёт (добавляется только проверка заголовка); отказ внешнего запроса — без обращения к БД

**Constraints**: секрет не попадает в репозиторий (Constitution VIII); прод-деплой — только с согласия владельца (матрица `nsa-i9`); nginx-конфиг боевого хоста меняется только после проверки `nginx -t`

**Scale/Scope**: 1 эндпоинт, 4 файла кода + 1 файл конфигурации, 1 тест на стороне клиента и 1 на стороне сервиса

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Требование | Как выполняется | Статус |
|---|---|---|---|
| I. Self-contained автопайплайн | правка не тянет внешних SaaS | секрет и фильтр — локальные средства (nginx + заголовок) | ✅ |
| II. Сырой JDBC | никаких JPA/Hibernate | правка не трогает доступ к данным | ✅ |
| III. Двух-БД синхронизация | не ломаем `SyncRegistry` | формат тела и семантика применения действий не меняются (FR-011) | ✅ |
| IV. Обработка ошибок | R-43 `redirectErrorStream`, структурированное логирование | наружу — код отказа без текста ошибки БД; в журнал — запись категории безопасности | ✅ |
| V. Производительность | без регрессий | проверка заголовка — O(1), до обращения к БД | ✅ |
| VI. Per-feature документация (FR-009) | `docs/features/<slug>.md` | документ фичи обновляется в рамках задачи | ✅ (в задачах) |
| VII. Тесты | регрессионный тест «до/после» | FR-010, тесты на сервисе и клиенте | ✅ (в задачах) |
| VIII. Секреты и git-гигиена | секрет не в репозитории | значение задаётся конфигурацией, в коде — только имя параметра | ✅ |
| IX. Knowledge-first + изоляция | pre-flight выполнен, работа в своей ветке | pre-flight в `spec.md § Knowledge References`; ветка `554-close-public-sql-endpoint` | ✅ |
| IX.3. Subagent isolation | субагент — в отдельном worktree | реализация — в отдельном worktree | ✅ (в задачах) |

**Проверка после Phase 1** — та же таблица: новых нарушений не появилось, секрет по-прежнему только
в конфигурации, документация фичи и тесты — в задачах.

## Project Structure

### Documentation (this feature)

```text
specs/554-close-public-sql-endpoint/
├── spec.md              # спека (утверждена владельцем 2026-10-24)
├── plan.md              # этот план
├── tasks.md             # задачи реализации
├── research.md          # решения по реализации (Phase 0)
├── data-model.md        # сущности и правила (Phase 1)
├── quickstart.md        # сценарии проверки (Phase 1)
├── contracts/
│   └── internal-secret-contract.md   # контракт заголовка X-Internal-Secret
├── checklists/
│   └── requirements.md  # чек-лист качества спеки
└── report.md            # отчёт для трекера (после merge)
```

### Source Code (repository root)

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/MainController.kt                 # ← проверка ключа, отказ, журнал (FR-001…FR-009)
└── config/…                                     # параметр внутреннего секрета (FR-004)

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── services/SyncRemoteClient.kt                 # ← отправка X-Internal-Secret (FR-003, FR-008, FR-009)
└── …                                            # параметр секрета на стороне клиента (FR-004)

deploy/prod-single-host/
├── 80to8897                                     # ← location /changerecords: allow/deny (FR-005)
└── nginx.conf                                   # образец уже работающей реализации (22–32, 41–50)

karaoke-web/src/test/kotlin/…                    # ← тест сервиса: без ключа — отказ (FR-010)
karaoke-app/src/test/kotlin/…                    # ← тест клиента: заголовок уходит в запрос (FR-010)

docs/features/<slug>.md                          # ← per-feature документ (FR-009 конституции)
```

## Complexity Tracking

| Элемент | Оценка | Обоснование |
|---|---|---|
| Объём кода | малый (≈60–90 строк) | проверка заголовка + конфиг nginx + 2 теста |
| Новые сущности/таблицы | нет | правка не трогает модель данных |
| Новые зависимости | нет | используются уже подключённые Spring Security и nginx |
| Миграции БД | нет | схема не меняется |
| Внешние сервисы | нет | всё локально |
| Риск отката | низкий | откат = возврат конфигурации и заголовка |
| Операционный риск | средний | адрес админской машины плавает у провайдера — до добавления нового адреса синхронизация не пройдёт (компенсируется вторым рубежом и своевременным добавлением адреса) |
