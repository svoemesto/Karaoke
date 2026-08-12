# Implementation Plan: Бейдж «новые альбомы» в пункте меню «Авторы»

**Branch**: `176-authors-new-albums-badge` | **Date**: 2026-08-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/176-authors-new-albums-badge/spec.md`

## Summary

Добавить в левый сайдбар админки `webvue3` (пункт «Авторы») красный бейдж с количеством авторов, у которых есть новые альбомы (`haveNewAlbum = true`). Шаблон — бейджи «Чат» и «Задания редактора»: тот же визуал, тот же polling каждые 20 сек, тот же Vuex-паттерн.

Бэкенд: новый endpoint `POST /api/authors/withnewalbumcount` в существующем `ApiController.kt`, возвращающий `Int` — результат `SELECT COUNT(*) FROM tbl_authors WHERE ...` с условием, идентичным `Author.haveNewAlbum`.

Фронт: новое поле в `Authors/store.js` + `<span class="authors-nav-badge">` в `App.vue` + polling-таймер с cleanup.

Технический подход полностью определён [research.md](./research.md) — фича следует существующим паттернам проекта без новых архитектурных решений.

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x (Spring Boot 2.x/3.x), JDK 17 — для `karaoke-app/.../controllers/ApiController.kt` и `Author.kt`.
- Frontend: Vue 3 + Vite + JavaScript (ES2020+) — для `webvue3/src/App.vue` и `webvue3/src/components/Authors/store.js`.

**Primary Dependencies**:
- Backend: Spring Boot (existing), `KaraokeConnection` (raw JDBC wrapper, existing), Jackson (existing).
- Frontend: Vue 3 (existing), Vuex (existing), `bootstrap-vue-next` (existing), `promisedXMLHttpRequest` (existing helper).

**Storage**:
- PostgreSQL через raw JDBC. Читаем `tbl_authors` (5 задействованных колонок: `watched`, `ym_id`, `vk_id`, `last_album_ym`, `last_album_vk`, `last_album_processed`). Никаких новых таблиц/колонок/индексов не требуется (см. [data-model.md](./data-model.md)).

**Testing**: ручное (по [quickstart.md](./quickstart.md)). Автотестов нет — CI для бэка отсутствует (см. AGENTS.md «Тесты»).

**Target Platform**:
- Backend: Linux server (karaoke-app в контейнере на admin-машине).
- Frontend: браузер, SPA `webvue3`.

**Project Type**: web-service + SPA (комбинация Option 2 из шаблона — backend + frontend).

**Performance Goals**:
- Endpoint `/api/authors/withnewalbumcount` ≤ 100 ms на базе из ~18k авторов (SC-004).
- Polling раз в 20 сек — низкая нагрузка на БД.

**Constraints**:
- ≤ 20 секунд от изменения БД до обновления бейджа (SC-001, SC-003).
- Нет утечек polling-таймеров при HMR (SC-006).
- Бейдж не ломает верстку сайдбара при 3-значных числах (SC-007).
- При сбое сети — сохранение предыдущего значения (FR-010).

**Scale/Scope**:
- ~18 000+ строк в `tbl_authors` на проде.
- 3 polling-запроса в минуту суммарно (chat + songeditor + authors).
- Изменения: 1 новый метод в `ApiController.kt`, 1 новый companion-метод в `Author.kt`, 4 правки в `webvue3/src/App.vue`, 4 правки в `webvue3/src/components/Authors/store.js`.

## Constitution Check

*Gate: must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип (Constitution v2.1.0) | Применимость | Compliance | Заметки |
|---|-------------------------------|--------------|------------|---------|
| I | Self-contained автопайплайн | Нет (UI-фича) | ✅ N/A | Не затрагивает media pipeline. |
| II | Сырой JDBC + дифф по хэшам | Да — backend читает `tbl_authors` | ✅ PASS | Используем существующий `KaraokeConnection.getConnection()`, raw `SELECT COUNT(*)`. Никакого JPA/Hibernate, никакого `loadList` для счётчика. |
| III | SyncRegistry | Нет | ✅ N/A | `Author` уже в SyncRegistry (LOCAL→SERVER), фича только читает LOCAL. Никаких изменений в sync-конфиге. |
| IV | Async-очередь + `redirectErrorStream(true)` | Нет | ✅ N/A | Endpoint синхронный, никаких `ProcessBuilder`. |
| V | Двух-фронтенд | Да — только `webvue3` | ✅ PASS | Никаких изменений в `karaoke-public`. Endpoint только в admin-контроллере (`ApiController.kt`, не `Public*Controller`). |
| VI | Code Standards (KDoc/JSDoc, lint) | Да — новые публичные API | ✅ PASS | Новый `Author.countWithNewAlbum` получит KDoc с `@see docs/features/...` (или ссылкой на spec). Новый endpoint получит KDoc. Линтеры ktlint/ESLint прогоняются через pre-commit (см. [quickstart.md](./quickstart.md) секция «Post-implementation»). |
| VII | Cross-Machine Setup | Да — фича в публичном репо | ✅ PASS | Никаких персональных AI-конфигов; никаких изменений `.gitattributes`/`.git-blame-ignore-revs`. |
| VIII | Секреты и git-гигиена | Нет | ✅ N/A | Endpoint не принимает и не возвращает секретов. Никаких изменений в env/deploy. |

**Результат Constitution Check**: **PASS**. Все применимые принципы удовлетворены. Нарушений нет — секция «Complexity Tracking» пуста.

**Re-check после Phase 1**: PASS (без изменений — дизайн не ввёл новых принципов).

## Project Structure

### Documentation (this feature)

```text
specs/176-authors-new-albums-badge/
├── plan.md              # Этот файл
├── spec.md              # Спецификация (User Stories, FR, SC)
├── research.md          # Phase 0: принятые решения, контекст
├── data-model.md        # Phase 1: описание сущности Author
├── contracts/
│   └── api-authors-withnewalbumcount.md  # Phase 1: контракт endpoint
├── quickstart.md        # Phase 1: 7 ручных сценариев проверки
├── checklists/
│   └── requirements.md  # Quality checklist (все 13 пунктов ✅)
└── tasks.md             # Phase 2: генерируется /speckit.tasks
```

### Source Code (repository root)

Фича затрагивает 4 файла в существующей структуре:

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/Author.kt                                  # +1 companion-метод countWithNewAlbum
└── controllers/ApiController.kt                     # +1 endpoint apisAuthorsWithNewAlbumCount

webvue3/src/
├── App.vue                                          # +1 nav-link с бейджем, +1 polling-таймер, +2 CSS-класса
└── components/Authors/store.js                      # +1 state-поле, +1 getter, +1 mutation, +1 action
```

**Structure Decision**: Фича вписывается в существующую структуру web-application (backend `karaoke-app` + frontend `webvue3`). Никаких новых модулей, директорий или gradle subproject-ов не требуется. Все 4 файла уже существуют — фича их только дополняет.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений — секция пуста.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Реализация (резюме для следующих фаз)

**Backend (1 файл + 1 новый метод):**

1. **`Author.kt`** — добавить companion-метод:
   ```kotlin
   // Количество авторов с haveNewAlbum = true (бейдж в App.vue webvue3).
   // Зеркало SQL-условия Author.haveNewAlbum / Author.getWhereList["haveNewAlbum=+"] —
   // если меняется семантика haveNewAlbum, синхронизировать все три места.
   fun countWithNewAlbum(database: KaraokeConnection): Int {
       val connection = database.getConnection() ?: return 0
       val sql = """
           SELECT COUNT(*) AS cnt FROM $TABLE_NAME
           WHERE watched = true
             AND (ym_id <> '' OR vk_id <> '')
             AND (last_album_ym <> last_album_processed OR last_album_vk <> last_album_processed)
       """.trimIndent()
       return try {
           connection.prepareStatement(sql).use { ps ->
               ps.executeQuery().use { rs -> if (rs.next()) rs.getInt("cnt") else 0 }
           }
       } catch (e: SQLException) {
           println("Author.countWithNewAlbum SQLException: ${e.message}")
           0
       }
   }
   ```

2. **`ApiController.kt`** — добавить endpoint рядом с `apisAuthorsDigest`:
   ```kotlin
   // Количество авторов с haveNewAlbum = true — бейдж пункта меню «Авторы» в webvue3 (App.vue).
   @PostMapping("/authors/withnewalbumcount")
   @ResponseBody
   fun apisAuthorsWithNewAlbumCount(): Int =
       Author.countWithNewAlbum(WORKING_DATABASE)
   ```

**Frontend (2 файла):**

3. **`webvue3/src/components/Authors/store.js`** — добавить state/getter/mutation/action:
   ```javascript
   // В state: authorsWithNewAlbumCount: 0,
   // В getters: getAuthorsWithNewAlbumCount(state) { return state.authorsWithNewAlbumCount },
   // В mutations: setAuthorsWithNewAlbumCount(state, count) { state.authorsWithNewAlbumCount = count },
   // В actions:
   loadAuthorsWithNewAlbumCount(ctx) {
     return promisedXMLHttpRequest({ method: 'POST', url: '/api/authors/withnewalbumcount' })
       .then((data) => {
         ctx.commit('setAuthorsWithNewAlbumCount', parseInt(data, 10) || 0)
       })
       .catch((error) => console.log(error))
   },
   ```

4. **`webvue3/src/App.vue`** — заменить `<router-link to="/authors">Авторы</router-link>` на версию с бейджем; добавить computed `authorsWithNewAlbumCount`; добавить polling в `mounted` + cleanup в `beforeUnmount`; добавить CSS-классы `.authors-nav-link` и `.authors-nav-badge` (зеркало `.chat-nav-*`).

**Никаких миграций БД, никаких изменений в `karaoke-public`, никаких изменений в `deploy/`.**

**Post-implementation**: прогнать [quickstart.md](./quickstart.md) (7 сценариев) и pre-commit (`pre-commit run --all-files`) для проверки ktlint/ESLint/KDoc/JSDoc coverage.
