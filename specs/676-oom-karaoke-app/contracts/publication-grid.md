# Contract: раскладка неопубликованных песен (уровень вызывающих)

> **Ветка**: `676-oom-karaoke-app` | **Дата**: 2026-10-30
> **Спека**: FR-001…FR-005, SC-001, SC-002 | **План**: [plan.md](../plan.md)

Контракт закрепляется тестом. **Product-код не меняется** — починка уже в master
(коммит `4d643554`, BR-188); фича добавляет недостающее покрытие (FR-004).

---

## 1. Точки входа

| Функция | Место | Вызывается из |
|---|---|---|
| `Publication.getUnPublicationList(database, storageService, storageApiClient)` | `model/Publication.kt:1691` | `MainController.kt:1923` (`GET /unpublications`), `ApiController.kt:2218` (`POST /api/unpublications`) |
| `Publication.getSkipedPublicationList(database, storageService, storageApiClient)` | `model/Publication.kt:1747` | `ApiController.kt:2235` (`POST /api/skipedpublications`) |
| `popPublicationStacks(stacks)` (вспомогательная) | `model/Publication.kt:2056` | обе вызывающие функции |

Авторизация и легаси-путь — **вне объёма** (KARAOKE-335 / KARAOKE-336).

---

## 2. Инварианты

| # | Инвариант | Источник |
|---|---|---|
| I1 | Раскладка завершается за конечное число итераций для любого входа, включая пустой | FR-001 |
| I2 | Каждая итерация уменьшает суммарное число песен в стопках хотя бы на одну | FR-001 |
| I3 | Число строк = максимальное число песен у одного автора | FR-002 |
| I4 | Число столбцов = число авторов с непустой стопкой; `rows[i].size` одинаково для всех `i` | FR-002 |
| I5 | Пустой вход → 0 строк, 0 итераций | US1.3 |
| I6 | Поведение легаси-пути функционально прежнее | FR-003 |
| I7 | `/api/unpublications2` и `/api/publicationsdigest` не изменены | FR-005 |

---

## 3. Границы теста (FR-004, SC-002)

| Параметр | Значение | Обоснование |
|---|---|---|
| Число песен | **500** | SC-002 |
| Число авторов | 10 (по 50 песен) | неравномерный набор: максимум у одного автора = 50 |
| Ожидаемый результат | 50 строк × 10 столбцов = 500 `Publication` | I3 + I4 |
| Ожидаемое время | < 5 с | тест не должен «пройти» на бесконечном цикле по таймауту тика |

Две ситуации (обе — уровень вызывающих):

| Ситуация | Вход | Ожидание |
|---|---|---|
| `getUnPublicationList` | авторов со `skip = true` нет → все 500 песен | 50×10 сетка |
| `getSkipedPublicationList` | все 10 авторов — `skip = true` → 500 песен | 50×10 сетка |

---

## 4. Как подменяется БД

Подмена **границы JDBC** (product-код не меняется — FR-003):

```text
Publication.getUnPublicationList(db, storageService, storageApiClient)
  ├─ Author.loadList(whereArgs = {"skip": "true"}, …)      ─┐
  │     └─ KaraokeDbTable.loadListInternal → SELECT tbl_authors.*   │
  └─ Song.loadListFromDb({"publish_date": "-", …})                 │  мок java.sql.Connection
        └─ SELECT * FROM tbl_songs WHERE …                          │  (Mockito, как в
                                                                     │   SqlInjectionTestSupport.kt)
```

| Мок | Что отдаёт |
|---|---|
| `ResultSet` для `tbl_songs` | 500 строк; `song_author` — 10 имён по 50 песен; остальные геттеры — дефолты мока (`null`/`0`) |
| `ResultSet` для `tbl_authors` | пусто (для `getUnPublicationList`) либо 10 строк со `skip = true` (для `getSkipedPublicationList`) |
| `KaraokeConnection` | `FakeKaraokeConnection` (уже есть в `src/test/.../FakeKaraokeConnection.kt`) |
| `KaraokeStorageService`, `StorageApiClient` | моки (`MockJdbc.storageService()`, `MockJdbc.storageApiClient()`) |

**Почему это безопасно**: `Song.compareTo` → `sortString` (`Song.kt:420`) и
`Song.state` (`Song.kt:7319`) — чистые функции; `Publication(database)` и
`publication.publish10 = song` БД не трогают (`Publication.kt:89-107`).

---

## 5. Что тест обязан доказать

1. На **текущей** реализации — вызов возвращается за конечное время, сетка
   50×10, все `Publication.publish10` заполнены, суммарно 500 песен.
2. На **прежней** реализации (условие выхода — захваченный максимум) — тест не
   завершается → падает по таймауту. Это и есть «падает на прежней
   реализации» (SC-001).
3. Существующие `audit/PkgS212BehaviorTest.kt:43-65` и
   `audit/PkgS212SourceContractsTest.kt:33-38` — **не трогаются** и остаются
   зелёными (FR-004: «Существующие тесты MUST быть сохранены»).
4. Тест не меняет и не подменяет `/api/unpublications2`, `/api/publicationsdigest`
   (FR-005) — проверяется ревью диффа.

---

## 6. Что НЕ входит

| Не входит | Почему |
|---|---|
| Тест HTTP-эндпоинтов | требует Spring-контекста и живой БД; легаси-путь без авторизации — вне объёма (KARAOKE-335) |
| Ограничение доступа к трём точкам входа | KARAOKE-335 (отдельная задача) |
| Выпилка мёртвого кода легаси-пути | KARAOKE-336 (отдельная задача) |
| Ограничение объёма раскладки (пагинация) | § «Известные ограничения» спеки — отдельная тема |
