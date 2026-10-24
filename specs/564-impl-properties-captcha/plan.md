# Implementation Plan: 564 — Закрыть публичную запись в `tbl_public_settings` и fail-open проверку капчи

**Branch**: `564-impl-properties-captcha` | **Date**: 2026-10-24 | **Spec**: [`../559-close-public-properties-captcha/spec.md`](../559-close-public-properties-captcha/spec.md) | **Research**: [research.md](research.md)

**Input**: Feature specification from `specs/559-close-public-properties-captcha/spec.md`
(задачи OpenProject **#359** — BR-019 и **#362** — BR-518, аудит #336).

## Summary

Две связанные S1-дыры аудита #336 закрываются одной поставкой:

1. **#359 (BR-019) — периметр публичных настроек.**
   - **Запись** `POST /api/properties/setproperty` закрывается двумя рубежами: сетевой
     фильтр nginx по адресу админской машины (`allow`/`deny`) и общий секрет в заголовке
     `X-Internal-Secret` (fail-safe). Секрет — уже настроенный общий server-to-server
     (`SYNC_INTERNAL_SECRET`), новый не заводится.
   - **Чтение** остаётся, но по белому списку: публичные ключи — анонимно, операционные —
     только по защищённому каналу, секретные (`yandexSmartCaptchaServerKey`) — **никогда**.
2. **#362 (BR-518) — проверка капчи.**
   - Серверный ключ капчи переезжает в env (`CAPTCHA_SERVER_KEY`), читается при старте,
     кеша нет (окно 5 минут исчезает).
   - Проверка становится **fail-closed** и различает четыре исхода; при незаконной
     конфигурации регистрация отклоняется с отдельным кодом `captcha_not_configured`.

Операционная часть (инвентаризация таблицы, чистка, выдача ключа, деплой) — за владельцем,
регламент описан в [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**: Kotlin 2.2 / Spring Boot 3.5.6 (`karaoke-web`), nginx (прод-контур)

**Primary Dependencies**: Spring Web (`@RestController`), Spring Security (цепочка `karaoke-web`),
`java.sql` (сырой JDBC), `org.slf4j` (категории `infra.*`), nginx `ngx_http_access_module`

**Storage**: PostgreSQL 16 (сырой JDBC, ADR-0001) — модель данных не меняется

**Testing**: JUnit 5 + Mockito (`karaoke-web/src/test/kotlin/...`); nginx — `nginx -t` в
контейнере `nginx:stable`

**Target Platform**: Linux-сервер (прод-контур `sm-karaoke.ru`), админская машина `nsa-i9`

**Project Type**: web-service (backend + edge proxy + публичный SPA)

**Performance Goals**: проверка заголовка — O(1), до обращения к БД; чтение публичных
ключей по-прежнему обслуживается TTL-кешем (60 секунд); окно устаревания секрета капчи —
нулевое (замена при рестарте контейнера)

**Constraints**: секреты не попадают в репозиторий (Constitution VIII.5); прод-деплой — только
с согласия владельца (матрица `nsa-i9`); nginx-конфиг боевого хоста меняется только после
`nginx -t`; публичная регистрация не ломается при корректно настроенной капче

**Scale/Scope**: 4 файла кода в `karaoke-web` + 1 в `karaoke-public` + 1 nginx-конфиг +
3 compose-файла + 1 миграция SQL + 3 файла тестов + документация/knowledge

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Требование | Как выполняется | Статус |
|---|---|---|---|
| I. Self-contained автопайплайн | правка не тянет внешних SaaS | секрет и фильтр — локальные средства (nginx + заголовок); внешний сервис капчи не меняется | ✅ |
| II. Сырой JDBC | никаких JPA/Hibernate | правка не трогает доступ к данным (тот же `WORKING_DATABASE`, те же `prepareStatement`) | ✅ |
| III. Двух-БД синхронизация | не ломаем `SyncRegistry` | формат тела и семантика применения действий не меняются (вне объёма) | ✅ |
| IV. Обработка ошибок | R-43 `redirectErrorStream`, структурированное логирование | наружу — код отказа без текста ошибки БД; в журнал — запись категории безопасности; логирование через SLF4J-категории `infra.*` | ✅ |
| V. Производительность | без регрессий | проверка заголовка — O(1) до обращения к БД; кеш чтения сохранён; окно секрета — 0 | ✅ |
| VI. Per-feature документация (FR-009) | `docs/features/<slug>.md` | `docs/features/public-settings-perimeter.md` обновляется в рамках задачи | ✅ (в задачах) |
| VII. Тесты | регрессионный тест «до/после» | FR-007/SC-009: 3 файла тестов на `karaoke-web` | ✅ (в задачах) |
| VIII. Секреты и git-гигиена | секрет не в репозитории | значения задаются env (`SYNC_INTERNAL_SECRET`, `CAPTCHA_SERVER_KEY`), в коде — только имена параметров; `do.env.example` — `CHANGE_ME` | ✅ |
| IX. Knowledge-first + изоляция | pre-flight выполнен, работа в своей ветке | pre-flight — `spec.md § Knowledge References` + `research.md`; ветка `564-impl-properties-captcha`, worktree отдельный | ✅ |
| IX.3. Subagent isolation | субагент — в отдельном worktree | реализация — в `/home/nsa/Karaoke/.worktrees/impl359` | ✅ |

**Проверка после Phase 1** — та же таблица: новых нарушений не появилось, секреты по-прежнему
только в конфигурации, документация фичи и тесты — в задачах.

## Project Structure

### Documentation (this feature)

```text
specs/564-impl-properties-captcha/
├── research.md          # решения по реализации (Phase 0) — D1…D14
├── plan.md              # этот план
├── tasks.md             # задачи реализации
├── quickstart.md        # регламент инвентаризации/чистки и проверки
├── report.md            # отчёт для трекера (#359 и #362)
└── contracts/
    └── public-settings-secret-contract.md   # контракт защищённого канала
```

### Source Code (repository root)

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/PublicSettingsWebController.kt   # ← периметр: запись/чтение/дайджест (FR-001…FR-006)
├── services/CaptchaConfigService.kt             # ← ключ капчи из env, кеш 5 мин убран (FR-011, FR-013)
├── services/YandexCaptchaValidationService.kt   # ← fail-closed, 4 исхода (FR-009, FR-010)
├── controllers/PublicAuthController.kt           # ← код отказа регистрации (FR-009, решение п.6)
└── resources/application.yml                     # ← publicsettings.internal-secret, captcha.server-key

karaoke-public/src/views/RegisterView.vue         # ← сообщение о временной недоступности регистрации

deploy/prod-single-host/80to8897                  # ← location /api/properties/setproperty: allow/deny
deploy/docker-compose-web.yml                     # ← env CAPTCHA_SERVER_KEY
deploy/prod-single-host/docker-compose-web.yml    # ← env CAPTCHA_SERVER_KEY
deploy/web-server-deploy/deploy/docker-compose-web.yml # ← env CAPTCHA_SERVER_KEY
deploy/karaoke-db/51_public_settings_drop_captcha_server_key.sql # ← удаление строки-секрета

karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/
├── controllers/PublicSettingsWebControllerTest.kt   # ← NEW: периметр (FR-007)
├── services/YandexCaptchaValidationServiceTest.kt   # ← NEW: fail-closed (FR-007)
└── controllers/PublicAuthControllerCaptchaTest.kt   # ← NEW: отказ регистрации (FR-007)

docs/features/public-settings-perimeter.md       # ← per-feature документ (FR-009 конституции)
knowledge/…                                        # ← Living Documentation (Knowledge SSoT)
```

## Complexity Tracking

| Элемент | Оценка | Обоснование |
|---|---|---|
| Объём кода | средний (≈250–350 строк) | периметр доступа + перенос секрета + 3 набора тестов |
| Новые сущности/таблицы | нет | модель данных не меняется; одна миграция — только `DELETE` строки |
| Новые зависимости | нет | используются уже подключённые Spring Web, SLF4J, Mockito |
| Миграции БД | 1 (удаление строки-секрета) | выполняется владельцем **после** инвентаризации |
| Внешние сервисы | нет | Yandex SmartCaptcha не меняется (только fail-closed и место хранения ключа) |
| Риск отката | низкий-средний | откат = возврат кода + конфигурации; при откате кода закрытая запись остаётся закрытой только если вернуть и nginx, и код |
| Операционный риск | средний | (1) до выдачи `CAPTCHA_SERVER_KEY` регистрация будет отклоняться — это ожидаемо, но требует порядка «сначала env, потом деплой»; (2) адрес админской машины плавает — до добавления нового адреса операция с него не пройдёт (компенсируется рубежем приложения) |
