# Implementation Plan: 563 — Закрыть обход оплаты по скрытому жесту (спека 557)

**Branch**: `563-impl-gesture-token` | **Date**: 2026-10-24 | **Spec**: [../557-close-gesture-token-paywall/spec.md](../557-close-gesture-token-paywall/spec.md)

**Input**: Feature specification from `specs/557-close-gesture-token-paywall/spec.md` (контракт,
утверждён владельцем), решения владельца от 2026-10-24 (вариант А + TTL 30 минут).

## Summary

Полный доступ к стемам и разметке сейчас выдаётся по «скрытому жесту»: три анонимных запроса
в `POST /api/public/events` дают короткоживущий токен, а `PublicPlayerController.authorized()`
считает **факт владения токеном** достаточным основанием для отдачи полных mp3. Токен жеста и
легитимный токен структурно неразличимы, поэтому «просто убрать выдачу токена» недостаточно.

Правка состоит из двух рубежей (решение владельца №1 — вариант А):

1. **Рубеж выдачи.** Публичный эндпоинт событий больше не возвращает значение доступа
   (`meta` — всегда `null`), механика жеста удалена из сервиса, фронтенд перестаёт
   открывать по нему плеер (тихий отказ, без ошибок посетителю).
2. **Рубеж выдачи байт.** Право на полный доступ проверяется **в момент запроса** по правилам
   `docs/features/song-air-access.md`: публичность песни / живой премиум / живая подписка на
   песню / гостевая share-сессия / владение заданием редактора. Токен больше не является
   правом — он лишь указывает, что проверять.

Посетитель без полного доступа по-прежнему получает демо-фрагмент (обрезанный на сервере),
а не ошибку; легитимные пути (премиум, подписка, эфир, гость по share-ссылке, предпросмотр
редактора) работают как раньше. TTL токена остаётся 30 минут (решение владельца №2, FR-013).

## Technical Context

**Language/Version**: Kotlin 2.2 / Spring Boot 3.5.6 (`karaoke-web`), Vue 3 + Vite (`karaoke-public`)

**Primary Dependencies**: Spring Web (`@RestController`), SLF4J (категория `infra.player.access`),
сырой JDBC через `KaraokeDbTable`/`KaraokeConnection` (ADR-0001), `Mp3Trimmer` (обрезка mp3)

**Storage**: PostgreSQL (сырой JDBC) — правка схемы БД не требует (FR-015); токены — в памяти
процесса, как и раньше

**Testing**: JUnit 5 + Mockito (`karaoke-web/src/test/kotlin/...`), стиль — как в
`MainControllerChangeRecordsTest.kt` (образец спеки 554): контроллер создаётся напрямую,
зависимости — моки

**Target Platform**: Linux-сервер (прод-контур `sm-karaoke.ru`), админская машина `nsa-i9`

**Project Type**: web-service (публичный API + SPA)

**Performance Goals**: анонимный слушатель демо-режима не платит лишними запросами в БД
(в демо-токене нет id пользователя → живая проверка премиума не выполняется); премиум-пользователь
получает +1–2 запроса в БД на стрим (замена прежней «нулевой» проверки токена)

**Constraints**: прод-деплой — только с согласия владельца; рестарт `karaoke-web` на бою —
решение владельца (токены живут в памяти процесса, старые переживут перезапуск максимум
30 минут — Edge Case спеки); публичный сайт не должен ломаться (FR-010)

**Scale/Scope**: 6 файлов бэка + 2 файла фронта + 2 теста + документация

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Требование | Как выполняется | Статус |
|---|---|---|---|
| I. Self-contained автопайплайн | правка не тянет внешних SaaS | всё — локальный код проекта | ✅ |
| II. Сырой JDBC | никаких JPA/Hibernate | живая проверка прав — через существующие `KaraokeDbTable.loadById`/`loadList` и `Subscription.isSubscribedToSong` | ✅ |
| III. Двух-БД синхронизация | не ломаем `SyncRegistry` | новых сущностей нет, `SyncRegistry` не трогается | ✅ |
| IV. Обработка ошибок | структурированное логирование (ADR local-0006) | отказы пишутся в категорию `infra.player.access` без токена и без аудио | ✅ |
| V. Двух-фронтенд | admin и public — разные приложения | правка трогает только `karaoke-public`; `webvue3` не затронут | ✅ |
| VI. Code Standards (FR-009) | per-feature документ в том же PR | `docs/features/close-gesture-token-paywall.md` (новый) + обновление `docs/features/song-air-access.md` | ✅ (в задачах) |
| VII. Тесты | регрессионный тест «до/после» (FR-011) | контроллерный тест «до/после» + матрица решений | ✅ (в задачах) |
| VIII. Секреты и git-гигита | секреты не коммитим | новых секретов и настроек нет; `.env.local-tracker` — вне индекса | ✅ |
| IX. Knowledge-first | pre-flight выполнен | `spec.md § Knowledge References` + `research.md` (6 grep'ов, домены `karaoke-web`/`monetization`/`catalog`, ADR `local-0001`…`local-0008`) | ✅ |
| IX.3. Subagent isolation | субагент — в отдельном worktree | работа в `/home/nsa/Karaoke/.worktrees/impl358`, ветка `563-impl-gesture-token` | ✅ |

**Проверка после Phase 1** — та же таблица: новых нарушений нет, схема БД не меняется,
SSoT (`knowledge/`) обновлён по факту, per-feature документ написан.

## Project Structure

### Documentation (this feature)

```text
specs/563-impl-gesture-token/
├── research.md          # решения D1…D10 с обоснованием и ссылками на код
├── plan.md              # этот план
├── tasks.md             # задачи реализации
├── quickstart.md        # сценарии проверки
└── report.md            # отчёт для трекера (после реализации)
```

Спека-контракт: `specs/557-close-gesture-token-paywall/spec.md` (не изменяется — это контракт).

### Source Code (repository root)

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── services/PlayerGestureUnlockService.kt   # ← удаление жеста; userId/assignmentId в токене (D1, D3)
├── services/PlayerAccessService.kt          # ← НОВОЕ: живое решение о доступе (D2, D4, D6)
├── services/PlayerRightsProvider.kt         # ← НОВОЕ: живой премиум/подписка/владение заданием (D2)
├── controllers/PublicPlayerController.kt    # ← проверка права в момент отдачи байт (D2, D4, D7)
├── controllers/PublicApiController.kt       # ← публичный эндпоинт событий больше не выдаёт доступ (D1)
├── controllers/PublicSongEditorController.kt# ← токен задания несёт id редактора (D3)
└── util/Mp3Trimmer.kt                       # ← обрезка демо-фрагмента fail-closed (D5)

karaoke-public/src/
├── services/tracking.js                     # ← трекинг клика — fire-and-forget (D1, D10)
└── views/SongView.vue                       # ← ветка скрытого жеста больше не срабатывает (D1, D10)

karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/
├── controllers/PublicPlayerControllerAccessTest.kt  # ← регрессионный тест «до/после» (D9)
└── services/PlayerAccessServiceTest.kt             # ← матрица решений (D9)

docs/features/
├── close-gesture-token-paywall.md           # ← per-feature документ (FR-009 конституции)
└── song-air-access.md                       # ← привести правило доступа в соответствие с фактом

knowledge/domains/karaoke-web/               # ← SSoT: контракт плеера, /events, каталог сервисов
knowledge/domains/monitoring/components/log-categories.md  # ← новая категория `infra.player.access`
```

## Complexity Tracking

| Элемент | Оценка | Обоснование |
|---|---|---|
| Объём кода | средний (≈250–350 строк) | 2 новых сервиса + живая проверка в 5 точках отдачи + фронт |
| Новые абстракции | 2 сервиса | `PlayerAccessService` — правило (тестируется без БД), `PlayerRightsProvider` — доступ к БД |
| Изменение схемы БД | нет | FR-015; токены — в памяти, как раньше |
| Риск для легитимных путей | средний → низ | правило доступа вынесено в одно место; тест матрицы фиксирует все основания |
| Побочные эффекты | 1 (D7) | выгрузка пакета премиум-пользователем начинает работать: проверка премиума берётся из живого источника (id из токена), а не только из заголовка `Authorization`, которого в запросе выгрузки нет |
