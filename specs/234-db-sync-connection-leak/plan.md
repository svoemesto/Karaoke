# Implementation Plan: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Branch**: `234-db-sync-connection-leak` | **Date**: 2026-08-16 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/234-db-sync-connection-leak/spec.md`

## Summary

**Проблема.** Фабрики `Connection.Companion.local()/remote()` (`karaoke-app/.../Connection.kt:79,92`) возвращают **новый инстанс** `Connection` на каждый вызов. У каждого инстанса — свой `ThreadLocal<java.sql.Connection?>`, который при первом `getConnection()` открывает **отдельное физическое JDBC-соединение к Postgres**. В `POST /api/sync/oneclick` для каждого из 18 `SyncTarget` вызывается `Connection.local()` + `Connection.remote()` (см. `Utils.kt:629-647 runEntitySync`) — итого **36 свежих инстансов Connection за один клик** на одном Tomcat-потоке. Каждый со своим JDBC-каналом в ThreadLocal, который никогда не закрывается (KDoc `closeThreadConnection()` явно запрещает вызов из переиспользуемых потоков). При `max_connections=100` (Postgres дефолт) пул быстро упирается — каскад `FATAL: sorry, too many clients already` на каждом клике.

**Решение.** Сделать `Connection.Companion.local()/remote()` **singleton-фабриками** (Kotlin `by lazy(SYNCHRONIZED)`). Один инстанс `Connection` на весь процесс `karaoke-app` (отдельно для `local`, отдельно для `remote`); их `ThreadLocal` кеширует по одному физическому JDBC-каналу **на поток**, как и сейчас (контракт спеки `087-fix-shared-db-connection` сохранён). Плюс добавить SLF4J `log.warn` с `target`/`thread`/`cause` в `KaraokeConnection.getConnection()` для диагностики (контракт `Connection?` сохранён). Симметричный фикс в `karaoke-web/.../Connection.kt` обязателен.

**Технологический подход.** Минимальный, без новых зависимостей:
- `by lazy(LazyThreadSafetyMode.SYNCHRONIZED)` — стандартный Kotlin-паттерн для thread-safe singleton
- SLF4J `LoggerFactory` — уже в classpath через Spring Boot
- Без HikariCP (отложен, см. спеку `174-fix-stats-connection-leak`/FR-007)

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17 (см. Constitution § «Технологический стек»)
**Primary Dependencies**: Spring Boot 2.x/3.x, Gradle multi-module, SLF4J (через Spring Boot), `org.postgresql:postgresql` JDBC driver (существующий), без новых зависимостей
**Storage**: PostgreSQL через сырой JDBC (см. Constitution § Principle II «Сырой JDBC + дифф по хэшам»)
**Testing**: Ручной smoke-тест (см. Constitution § «Рабочий процесс» — тесты в CI отсутствуют, существующие интеграционные `@Disabled`); проверка пользователем
**Target Platform**: Linux-сервер / Docker-контейнер (`eclipse-temurin:22-jre-jammy` для `karaoke-app`/`karaoke-web`); локальный dev-pc через `deploy/do.sh`
**Project Type**: Spring Boot web service (multi-module Gradle: `karaoke-app` + `karaoke-web`), две точки фикса (singleton в `Connection.kt` + SLF4J warn в `KaraokeConnection.kt`)
**Performance Goals**: Снижение количества одновременных JDBC-соединений от `karaoke-app` к Postgres с пиков 100+ до ≤10 при 10 кликах «Синхронизация БД в 1 клик» подряд (SC-002 spec.md)
**Constraints**: Не сломать контракт `getConnection(): java.sql.Connection?` (174+ вызывающих мест); не сломать контракт `closeThreadConnection()` из спеки `091-fix-connection-leak`; не сломать контракт ThreadLocal-per-поток из спеки `087-fix-shared-db-connection`; HikariCP не подключать; не коммитить секреты (Principle VIII)
**Scale/Scope**: 18 `SyncTarget` × 2 БД (LOCAL+SERVER) = 36 точек создания Connection за один `/api/sync/oneclick`; ~200 Tomcat-потоков; Postgres `max_connections=100` (дефолт, не меняется в этой спеке)

## Constitution Check

*Gate: должен пройти до Phase 0 research. Re-check после Phase 1 design.*

| Principle | Соответствие | Обоснование |
|-----------|--------------|-------------|
| **I. Self-contained автопайплайн** | ✅ N/A | Фикс не в горячем пути обработки медиа — только в `Connection`-инфраструктуре. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ Сохраняется | Singleton `Connection` всё равно использует сырой JDBC (`DriverManager.getConnection` в `KaraokeConnection.kt:41`); `recordhash`-дифф не трогаем. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ Не нарушается | Не добавляем/не убираем сущности из `SyncRegistry.all`. Меняем только способ получения `Connection`-инстанса. |
| **IV. Async-очередь задач с парсингом stdout** | ✅ N/A | Не трогаем `KaraokeProcess*`. |
| **V. Двух-фронтенд** | ✅ N/A | Меняем только бэкенд (`karaoke-app` + `karaoke-web`). |
| **VI. Code Standards (FR-006/007/009)** | ⚠️ Требует внимания | KDoc для изменённых публичных API обязателен (FR-006); обновить `archive/docs/features/dual-db-sync.md` (FR-009 — per-feature документ для sync). |
| **VII. Cross-Machine Setup** | ✅ N/A | Локальные конфиги не трогаем; `.git-blame-ignore-revs`/`.gitattributes` не меняем. |
| **VIII. Секреты и git-гигиена** | ✅ Не нарушается | Не добавляем секреты, не трогаем `.env`/`.gitignore`. Pre-commit проверка `git ls-files | grep -iE '\.env$'` пуста — без изменений. |

**Ограничения агента** (см. Constitution §):
- ❌ Пересобирать `karaoke-app` локально ЗАПРЕЩЕНО (кроме `dev-pc` под `dev`). Текущая машина: `nsa-i9` под `nsa` — **не dev-pc**, поэтому сборку/перезапуск контейнера делает пользователь вручную. Агент правит код и опционально собирает `gradle clean karaoke-app:bootJar` без перезапуска контейнера.
- ✅ Править код во всех модулях — разрешено.
- ✅ Собирать gradle-джары (`./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`) — разрешено, но без перезапуска контейнера.

**Re-check после Phase 1**: см. секцию «Constitution Check (post-design)» ниже.

## Project Structure

### Documentation (this feature)

```text
specs/234-db-sync-connection-leak/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/           # Phase 1 output (/speckit.plan) — неприменимо (см. ниже)
├── checklists/
│   └── requirements.md  # Уже создан /speckit.specify (✅ все позиции)
├── spec.md              # Уже создан /speckit.specify
└── tasks.md             # Phase 2 — НЕ создаётся /speckit.plan (создаётся /speckit.tasks)
```

### Source Code (repository root)

Фикс затрагивает **2 Kotlin-файла** + **1 документация**:

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Connection.kt                   # ← фикс FR-001..FR-003: singleton local()/remote()
└── KaraokeConnection.kt            # ← фикс FR-004..FR-005: SLF4J log.warn + log поле

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
└── Connection.kt                   # ← фикс FR-008: симметричный singleton

archive/docs/features/
└── dual-db-sync.md                 # ← фикс FR-012: обновить KDoc и секцию «Известные ловушки»
```

**Structure Decision**: точечный фикс в существующих модулях, без новых файлов/папок. Новых контрактов (`/contracts/`) не требуется — фикс внутренний, не добавляет HTTP-endpoint'ы и не меняет API.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

Constitution Check проходит полностью — `Complexity Tracking` пуст.

---

## Phase 0: Research (consolidated → research.md)

См. [research.md](research.md).

**Ключевые решения** (детали и обоснование — в research.md):

1. **Singleton через `by lazy(SYNCHRONIZED)`** (а не `@Volatile lateinit` или `object ConnectionFactory`):
   - `object` — не подходит, потому что `Connection` — параметризованный класс с `url/username/password/name`, нужны **два разных** singleton'а (LOCAL, REMOTE), а не один.
   - `@Volatile lateinit var` — работает, но требует ручной ленивости и инициализации в `@PostConstruct`; `by lazy(SYNCHRONIZED)` — стандартный Kotlin-идиом, инициализируется при первом обращении, потокобезопасен.
   - **Выбрано**: `by lazy(LazyThreadSafetyMode.SYNCHRONIZED)`.

2. **SLF4J через `LoggerFactory.getLogger(KaraokeConnection::class.java)`**:
   - Уже в classpath через Spring Boot starter-logging.
   - Spring Boot использует Logback по умолчанию — без новых зависимостей.
   - **Выбрано**: `LoggerFactory.getLogger` + `log.warn(...)`.

3. **Симметричный фикс в `karaoke-web`**:
   - В `karaoke-web/.../Connection.kt` тот же баг (фабрика возвращает `new`).
   - Используется через `withDb { ... }` в `NewsController`/`DictionariesController`/`SponsrSyncController`.
   - **Выбрано**: симметричный singleton в `karaoke-web`.

4. **HikariCP — НЕ подключается** (по решению Q1 из spec.md): минимальный фикс, отложен в отдельную задачу.

---

## Phase 1: Design & Contracts

### Data model → data-model.md

См. [data-model.md](data-model.md).

Сущности изменены:
- **`Connection.Companion.LOCAL_INSTANCE`** — новое `private val`, lazy singleton для `Connection(name="LOCAL", ...)`.
- **`Connection.Companion.REMOTE_INSTANCE`** — новое `private val`, lazy singleton для `Connection(name="SERVER", ...)`.
- **`KaraokeConnection.log`** — новое `private val`, `LoggerFactory.getLogger(KaraokeConnection::class.java)`.

Состояние: «нет state transitions» — фикс инфраструктурный, модельных сущностей не добавляем/меняем.

### Contracts → `/contracts/`

**Неприменимо.** Фикс внутренний, не добавляет публичных API:
- Не добавляет HTTP-endpoint'ы (`POST /api/sync/oneclick` не меняется).
- Не меняет DTO (`SyncOneClickResultDto` остаётся как есть).
- Не меняет сигнатуру `getConnection()`.

Единственный «контракт» — это **контракт thread-safety** singleton-инстанса: `Connection.local()` всегда возвращает один и тот же объект, `Connection.remote()` — другой (но тоже один и тот же). Зафиксировано в KDoc `Connection.kt` (FR-010 spec.md).

### Quickstart → quickstart.md

См. [quickstart.md](quickstart.md).

Содержит:
- Предусловия (Docker up, Postgres `max_connections=100`).
- Команды сборки (`./gradlew clean karaoke-app:bootJar karaoke-web:bootJar --parallel`).
- Команды перезапуска контейнеров (только **пользователем**, не агентом — Constitution § «Ограничения агента», п. 1).
- 4 ручных сценария проверки:
  1. SC-001: 10 кликов «1 клик» → `grep -c "too many clients"` = 0.
  2. SC-002: `pg_stat_activity WHERE application_name='karaoke-app'` ≤ 10.
  3. SC-003: Искусственная перегрузка → структурированный SLF4J warn в логе.
  4. SC-004: Smoke-тест (Статистика + редактор песни + фоновая задача).

---

## Constitution Check (post-design)

| Principle | Re-check | Обоснование |
|-----------|----------|-------------|
| **I. Self-contained** | ✅ N/A | — |
| **II. Сырой JDBC** | ✅ Сохраняется | `DriverManager.getConnection` без изменений; singleton — лишь обёртка над существующим JDBC. |
| **III. SyncRegistry** | ✅ Не нарушается | Только `Connection.kt` и `KaraokeConnection.kt`; `SyncTarget.kt` не трогаем. |
| **IV. Async-очередь** | ✅ N/A | — |
| **V. Двух-фронтенд** | ✅ N/A | Только бэкенд. |
| **VI. Code Standards** | ✅ Требует внимания | KDoc для изменённых `Connection.local()/remote()` обязателен (FR-006); обновление `archive/docs/features/dual-db-sync.md` (FR-009) — в quickstart.md как task. |
| **VII. Cross-Machine Setup** | ✅ N/A | — |
| **VIII. Секреты и git-гигиена** | ✅ Не нарушается | Не добавляем секреты. |

**Post-design вердикт**: Constitution Check проходит полностью. Никаких новых зависимостей, никаких новых API, минимальный точечный фикс.

---

## Implementation Order (для будущего `/speckit.tasks`)

1. **`karaoke-app/.../Connection.kt`**: заменить `fun local()/remote()` на `by lazy(SYNCHRONIZED)` + `LOCAL_INSTANCE`/`REMOTE_INSTANCE`. Обновить KDoc.
2. **`karaoke-app/.../KaraokeConnection.kt`**: добавить `private val log = LoggerFactory.getLogger(...)`. В `getConnection()` добавить `log.warn(...)` (сохранить `println` для обратной совместимости).
3. **`karaoke-web/.../Connection.kt`**: симметричный фикс.
4. **`archive/docs/features/dual-db-sync.md`**: обновить секцию «Известные ловушки» + добавить секцию «Singleton Connection-фабрики».
5. **Verification**: пользователь перезапускает `karaoke-app` + `karaoke-web` вручную; проверяет SC-001..SC-005.

---

## Done When

- [x] plan.md создан и заполнен (этот файл)
- [x] research.md создан (Phase 0)
- [x] data-model.md создан (Phase 1)
- [x] quickstart.md создан (Phase 1)
- [ ] tasks.md — НЕ создаётся в этой фазе, создаётся в `/speckit.tasks`
- [x] Constitution Check пройден (pre и post-design)
- [x] Без extension hooks (поле `after_plan` пусто в `.specify/extensions.yml`)
