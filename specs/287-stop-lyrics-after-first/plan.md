# Implementation Plan: 287 — Прекращение извлечения текста после первого успеха + ручная попытка по ссылке

**Branch**: `287-stop-lyrics-after-first` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/287-stop-lyrics-after-first/spec.md`

## Summary

Модификация алгоритма автоматического поиска текста песни: после того как для одной из найденных ссылок успешно извлечён непустой текст, дальнейшие HTTP-запросы и парсинг для остальных ссылок прекращаются. В БД сохраняются все ссылки, но только одна (первая успешная) имеет непустой `text`/`html`, остальные — пустые («серые»). В модалке «Поиск текста песни в интернете» добавляется кнопка «Получить текст по ссылке» под «Открыть на сайте», которая позволяет пользователю вручную запустить попытку извлечения текста для конкретной «серой» ссылки.

Технический подход (из [research.md](research.md)):
- Правка в общих точках обхода: `SearchResult.getSearchResultsForSearchAsync` (Yandex-путь, покрывает `YANDEX_SYNC` + Yandex-воркер) + `UtilsAI.getLyricsSearchViaSearchTool` (Search-tool-путь, покрывает `SEARXNG`/`FOURGET`).
- Новый endpoint `POST /api/song/extractlyricsbysearchresultid` для ручной попытки + новая общая функция `UtilsAI.extractLyricsBySearchResultId`.
- Изменение `SearchResultDTO` — добавлено поле `lastError: String?` (без миграции БД, т.к. поле в DTO, не в таблице).
- Frontend: новая кнопка + новый Vuex action в `webvue3/src/components/Songs/store.js`.

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x, Spring Boot 2.x/3.x, JDK 17 (Gradle multi-module, как в остальном проекте).
- Frontend: Vue 3 + Vite, Node 22, Bootstrap-vue-next.

**Primary Dependencies**:
- Backend: Spring Boot (web), Jsoup (HTML-парсинг), kotlinx-serialization (для DTO), PostgreSQL JDBC (без JPA/Hibernate — см. Constitution §II).
- Frontend: Vue 3 (Composition API), Vuex 4 (state), Vite (build).
- Без новых зависимостей — всё переиспользует существующие библиотеки.

**Storage**:
- PostgreSQL через сырой JDBC (`KaraokeConnection`). Существующие таблицы `tbl_search_async`, `tbl_search_results` — **без изменений схемы**. SQL-миграции НЕ нужны.
- Новое поле `lastError` — только в `SearchResultDTO` (data class на стороне Kotlin/JSON), в БД не сохраняется (это runtime-информация о последней попытке).

**Testing**:
- В CI тестов нет (см. AGENTS.md / Constitution §«Рабочий процесс»: «Тесты: в CI нет»).
- Существующие тесты (`karaoke-app/src/test`) — `@Disabled`, требуют сеть/браузер.
- Валидация — пользователем в dev-окружении по [quickstart.md](quickstart.md).

**Target Platform**:
- Backend `karaoke-app`: Linux server (JRE 17+), admin-машина.
- Frontend `webvue3`: любой современный браузер (Chrome/Firefox/Safari).

**Project Type**: web-service + SPA (fullstack модификация).

**Performance Goals**:
- Сокращение времени автоматического поиска пропорционально количеству ссылок после первой успешной. Для типичного сценария (20 ссылок, успех на 2-й) — ~10x быстрее HTTP-этап.
- Ручная попытка: HTTP-запрос + парсинг одной страницы (3-10 сек), без влияния на общую производительность.

**Constraints**:
- Не сломать регрессии Pass 020 / 278 / 281 (см. SC-007).
- Не делать HTTP-запросы для «серых» ссылок (FR-001 — снижение нагрузки на исходные сайты).
- Не дублировать логику остановки в 3+ местах (D-1 в research.md — правка в общих точках).
- Обратная совместимость: все 4 существующих endpoint-а (`/searchasync`, `/searchresult`, `/deletesearchresults`, `/searchsongtextall`) сохраняют свои контракты.

**Scale/Scope**:
- 1 таблица результатов поиска (~20-100 строк на песню в худшем случае).
- 1 новая функция в backend + 1 новая кнопка в frontend.
- ~50-100 строк изменений в существующем коде (Kotlin + Vue).

## Constitution Check

*Проверяется до Phase 0 и после Phase 1 (re-check).*

### Pre-Research Gate (Phase 0)

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| §I Self-contained автопайплайн | ✅ Pass | Не затрагивается (никаких новых внешних API в горячем пути; HTTP-запросы к исходным сайтам — как и раньше). |
| §II Сырой JDBC + дифф по хэшам | ✅ Pass | Не затрагивается (схема БД не меняется; `recordhash`-триггер не трогается). |
| §III Двух-БД синхронизация через SyncRegistry | ✅ Pass | `tbl_search_results` уже участвует в sync (есть `recordhash`); наше изменение только в логике приложения, не в схеме. |
| §IV Async-очередь задач | ✅ Pass | Не затрагивается. |
| §V Двух-фронтенд (admin / public) | ✅ Pass | Изменения только в `webvue3` (admin), публичный `karaoke-public` не трогается. |
| §VI Code Standards | ⏳ To verify | После реализации: добавить KDoc на новые публичные функции (`extractLyricsBySearchResultId`, новый endpoint). JSDoc на новый Vuex action. |
| §VII Cross-Machine Setup | ✅ Pass | Не затрагивается. |
| §VIII Секреты и git-гигиена | ✅ Pass | Не затрагивается (нет новых секретов). |

**Вердикт**: можно переходить к Phase 0.

### Post-Design Gate (Phase 1, re-check)

| Принцип | Статус | Комментарий |
|---------|--------|-------------|
| §I Self-contained автопайплайн | ✅ Pass | Новый endpoint делает исходящий HTTP-запрос (как и существующие `/searchsongtextall` и т.п.). Никаких новых внешних SaaS. |
| §II Сырой JDBC | ✅ Pass | Используется `KaraokeDbTable`/`SearchResult.createDbInstance` (уже существующий паттерн). |
| §III Sync | ✅ Pass | Новая функция `extractLyricsBySearchResultId` обновляет запись через `SearchResult.save()` (через `KaraokeDbTable.save()`), что автоматически обновляет `recordhash`. Никаких новых таблиц/колонок — sync работает как раньше. |
| §IV Async-очередь | ✅ Pass | Не затрагивается. |
| §V Двух-фронтенд | ✅ Pass | Изменения только в `webvue3`. |
| §VI Code Standards | ✅ Pass | В плане: добавить KDoc на `extractLyricsBySearchResultId` (с `@see` на эту спеку), JSDoc на `extractLyricsBySearchResultId` action. См. `tasks.md` (Phase 2). |
| §VII Cross-Machine Setup | ✅ Pass | Не затрагивается. |
| §VIII Секреты | ✅ Pass | Не затрагивается. |

**Вердикт**: нарушений нет, все принципы соблюдены. Можно переходить к `/speckit.tasks`.

## Project Structure

### Documentation (this feature)

```text
specs/287-stop-lyrics-after-first/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output — обоснование технических решений
├── data-model.md        # Phase 1 output — описание сущностей
├── quickstart.md        # Phase 1 output — сценарии ручной проверки
├── contracts/           # Phase 1 output — контракты
│   ├── api-endpoints.md # описание нового и изменённых endpoint-ов
│   └── ui-modal.md      # UI-контракт для модалки SearchText.vue
├── spec.md              # уже создан /speckit.specify
├── checklists/
│   └── requirements.md  # уже создан /speckit.specify
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся этим планом)
```

### Source Code (repository root)

```text
# Backend (Kotlin / Spring Boot)
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── controllers/ApiController.kt              # MODIFY: новый endpoint /api/song/extractlyricsbysearchresultid
├── model/
│   ├── SearchResult.kt                       # MODIFY: цикл getSearchResultsForSearchAsync (D-1)
│   └── SearchResultDTO.kt                    # MODIFY: добавить поле lastError
├── UtilsAI.kt                                # MODIFY: ранний выход в getLyricsSearchViaSearchTool (D-1) + extractLyricsBySearchResultId
└── KaraokeProcessWorker.kt                   # НЕ ТРОГАЕМ (использует общую точку)

# Frontend (Vue 3 + Vite)
webvue3/src/components/Songs/
├── store.js                                  # MODIFY: новый action extractLyricsBySearchResultId
└── edit/
    ├── SearchText.vue                        # MODIFY: новая кнопка «Получить текст по ссылке»
    ├── SearchTextResultsTable.vue            # НЕ ТРОГАЕМ (визуал уже работает через text === '')
    └── SubsEdit.vue                          # НЕ ТРОГАЕМ

# SQL
karaoke-db/                                   # НЕ ТРОГАЕМ (миграций нет)

# LiveDocs
livedocs/features/                            # НЕ ТРОГАЕМ в этой фиче
```

**Structure Decision**: existing 2-tier structure (backend + frontend) с модификацией ~5 файлов. Никаких новых модулей/папок не требуется. Все правки локальны для конкретных функций.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Все правки укладываются в существующую архитектуру, без новых слоёв/модулей.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

## Изменения кода (резюме)

### Backend

| Файл | Изменение | Строки |
|------|-----------|--------|
| `karaoke-app/.../model/SearchResult.kt` | В `getSearchResultsForSearchAsync` после строки 213 — ранний выход из `links.forEach` если `searchResult.text.isNotBlank()` | ~5 строк |
| `karaoke-app/.../model/SearchResultDTO.kt` | Добавить поле `lastError: String? = null` | ~3 строки |
| `karaoke-app/.../UtilsAI.kt` | В `getLyricsSearchViaSearchTool` — рефакторинг цикла с ранним выходом; новая функция `extractLyricsBySearchResultId` | ~30-40 строк |
| `karaoke-app/.../controllers/ApiController.kt` | Новый endpoint `POST /api/song/extractlyricsbysearchresultid` | ~20-30 строк |

### Frontend

| Файл | Изменение | Строки |
|------|-----------|--------|
| `webvue3/.../store.js` | Новый action `extractLyricsBySearchResultId` | ~10 строк |
| `webvue3/.../edit/SearchText.vue` | Новая кнопка «Получить текст по ссылке» + метод `extractLyricsFromSelectedResult` + computed `canExtractLyrics` | ~30-40 строк |

**Итого**: ~100-150 строк изменений в существующем коде. Никаких новых файлов, кроме этой спеки/плана/артефактов.

## Риски

| Риск | Митигация |
|------|-----------|
| Сломать регрессию Pass 281 (`applyFoundLyricsIfMissing`) | Эта функция НЕ модифицируется (Pass 281); правки локальны для `getSearchResultsForSearchAsync` и `getLyricsSearchViaSearchTool`. |
| Сломать автоподстановку текста (Pass 020) | `applyFoundLyricsIfMissing` вызывается как раньше; единственное изменение — теперь ему передаётся список из 1 непустого `text` (а не из N), что не ломает логику (Pass 020 использует `firstOrNull { it.isNotBlank() }`). |
| Race condition при ручной попытке | Маловероятна (один пользователь в одной модалке); UI блокирует кнопку во время запроса. |
| Новый endpoint нарушает безопасность | Использует `permitAll()` (как и другие `/api/song/*`); нет новых секретов/токенов. |
| Изменение поведения автопоиска ломает чей-то другой use case | Все клиенты (админка `webvue3`) ожидают, что `text` первой успешной ссылки будет подставлен — это сохраняется. |

## Сборка / деплой

После реализации:
1. Backend: `./gradlew :karaoke-app:bootJar --parallel` + перезапуск контейнера `karaoke-app` (по машинно-специфичному исключению для `nsa-i9`/`nsa` — сборка разрешена без явного согласия, перезапуск — только по согласию).
2. Frontend: `cd webvue3 && npm run build` → собрать Docker-об через `deploy/do.sh build_webvue3`.
3. Деплой: через `deploy/deploy_web.sh` и `deploy/deploy_public.sh` (согласно AGENTS.md).

Тесты: нет в CI; проверка — пользователем по `quickstart.md`.