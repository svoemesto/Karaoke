# Feature Specification: isVpnActive — несколько «домашних» стран (Pass 427, #151)

**Feature Branch**: `427-vpn-multi-home-country`
**Created**: 2026-09-22
**Status**: Draft
**Input**: Наблюдение в логах `karaoke-app`:
`isVpnActive: countryCode=RU (homeCountry=DE) → ВПН включён`.
Машина может легально находиться и в Германии (DE), и в России (RU), но
настройка `vpnHomeCountry` допускает только одно значение → ВПН ложно активен.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#151` («isVpnActive: поддерживать несколько home-стран (DE и RU)»).
- **Title**: «isVpnActive: поддерживать несколько home-стран (DE и RU)».
- **Created in OpenProject**: 2026-09-22.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 151` — выполнено 2026-09-22
     (assignee=`ai agent`, статус `In progress`; PATCH вручную, т.к.
     `tracker.sh claim-issue` падает на смене assignee для Task в проекте Karaoke).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 151 --file specs/427-vpn-multi-home-country/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 151`.
  4. **Close** (owner, после merge + согласия на рестарт `karaoke-app`): `bash tools/tracker.sh close-issue 151`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-22
- **Grep-запросы** (минимум 3, по релевантным ключевым словам задачи):
  1. `grep -rln "vpnHomeCountry\|isVpnActive\|VPN" knowledge/` → 3 файла:
     `storage/components/storage-api-client.md`, `public/onboarding.md`,
     `guidelines/architecture-conventions.md` (все — про VPN в другом контексте).
  2. `grep -rln "country\|GeoIp\|api.country.is" knowledge/` → `integration/domain.md`,
     `integration/components/external-api-clients.md` (13. `GeoIpService`,
     `api.country.is/<ip>`), `stats/*`, `system/infra/sql-migrations*`.
  3. `grep -rin "vpn\|country" knowledge/domains/processing/components/karaoke-properties.md knowledge/adr/local-0001-karaoke-properties-defaults.md`
     → **0 hits**. `vpnHomeCountry` НЕ задокументирован в knowledge (пробел).
  4. `grep -rn "isVpnActive" --include='*.kt' karaoke-app/src` → callers:
     `AutoOneClickSyncScheduler.kt:162`, `AlbumCoverFinder.kt:332`,
     `ApiController.kt:5215`, `KaraokeProcessWorker.kt:991`.
  5. `grep -rn '"RU"' --include='*.kt' karaoke-app/src` → второе захардкоженное
     место: `Utils.kt:3771` (ветка `AlbumSearchResult.Unknown` в `checkLastAlbumYm`).
  6. `ls specs/124-vpn-check-before-sync/` → есть только `report.md`/`comment.md`
     (pre-modern, без spec.md); исходная задача OpenProject #124.

### Knowledge files consulted

- [`knowledge/domains/processing/components/karaoke-properties.md`](../../knowledge/domains/processing/components/karaoke-properties.md)
  — реестр Karaoke-properties; `vpnHomeCountry` отсутствует → его нужно задокументировать.
- [`knowledge/domains/integration/components/external-api-clients.md`](../../knowledge/domains/integration/components/external-api-clients.md)
  — `GeoIpService` использует тот же `api.country.is`, что `isVpnActive`.
- [`knowledge/system/utilities.md`](../../knowledge/system/utilities.md)
  — `Utils.kt` как hot-файл; сюда добавляется helper парсинга списка стран.
- [`knowledge/adr/local-0001-karaoke-properties-defaults.md`](../../knowledge/adr/local-0001-karaoke-properties-defaults.md)
  — конвенция дефолтов Karaoke-properties.
- [`specs/124-vpn-check-before-sync/report.md`](../../specs/124-vpn-check-before-sync/report.md)
  — история появления `isVpnActive`/`vpnHomeCountry` (OpenProject #124).

### Прецедент

2026-09-22, машина `nsa-i9` (может работать и через DE-, и через RU-интернет):

```
isVpnActive: countryCode=RU (homeCountry=DE) → ВПН включён (via https://api.country.is/)
```

`vpnHomeCountry` хранит ровно одну страну, поэтому любая вторая законная
страна трактуется как ВПН. Это блокирует `AutoOneClickSyncScheduler`,
`AlbumCoverFinder`, ручную синхронизацию (`ApiController.postSyncOneClick`),
`KaraokeProcessWorker` — ложные срабатывания.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Машина в DE и RU не считается под ВПН (Priority: P1)

**Описание**: Админ задаёт `vpnHomeCountry = "DE,RU"`. Если внешний IP
определяется как `DE` **или** `RU` — ВПН выключен; любая другая страна — ВПН включён.

**Why P1**: основная боль; ложный ВПН блокирует синхронизацию и процессы.

**Independent Test**: Unit-тест `parseHomeCountries("DE,RU")` → `{DE, RU}`;
`isVpn("DE", {DE,RU}) == false`, `isVpn("RU", {DE,RU}) == false`,
`isVpn("NL", {DE,RU}) == true`.

**Acceptance Scenarios**:

1. **Given** `vpnHomeCountry="DE,RU"`, **When** `countryCode=RU`, **Then**
   `isVpnActive()=false`, лог `homeCountry=[DE, RU]`.
2. **Given** `vpnHomeCountry="DE,RU"`, **When** `countryCode=DE`, **Then**
   `isVpnActive()=false`.
3. **Given** `vpnHomeCountry="DE,RU"`, **When** `countryCode=NL`, **Then**
   `isVpnActive()=true`.
4. **Given** `vpnHomeCountry="RU"` (legacy, без изменений в файле настроек),
   **When** `countryCode=RU`, **Then** `isVpnActive()=false` (backward compatible).
5. **Given** разделители `,`, `;`, пробел (например `"DE, RU"` / `"de ru"`),
   **When** парсинг, **Then** набор `{DE, RU}` (uppercase, trim, без пустых).

### User Story 2 — Согласованность второго места с захардкоженным RU (Priority: P2)

**Описание**: Ветка `AlbumSearchResult.Unknown` в `checkLastAlbumYm` жёстко
сравнивает `country != "RU"`. Привести к тому же списку `homeCountries`.

**Why P2**: тот же баг в другом месте; после расширения списка поведение
должно быть единообразным.

**Independent Test**: `grep -n '"RU"' Utils.kt` не находит сравнение страны;
ветка использует `homeCountries`.

**Acceptance Scenarios**:

1. **Given** `vpnHomeCountry="DE,RU"`, **When** Unknown + `country=RU`, **Then**
   сообщение НЕ про ВПН (как и раньше для RU).
2. **Given** `vpnHomeCountry="DE"`, **When** Unknown + `country=RU`, **Then**
   сообщение про ВПН (согласовано с `isVpnActive`).

## Requirements *(mandatory)*

### Functional

- **FR-001**: Настройка `vpnHomeCountry` MUST принимать **список** стран
  (ISO 3166-1 alpha-2), разделённых `,`, `;` и/или пробелом; регистр
  не важен. Backward-compatible: одиночное значение (`"RU"`) — список из 1.
- **FR-002**: `isVpnActive()` MUST считать ВПН активным, если определённая
  страна **не входит** в список home-стран (сравнение по uppercase).
- **FR-003**: Ветка `AlbumSearchResult.Unknown` в `checkLastAlbumYm` MUST
  использовать тот же список home-стран вместо захардкоженного `"RU"`.
- **FR-004**: Утилита парсинга списка стран MUST быть единой (одна функция),
  экспортируемая для обоих мест; пустые элементы MUST отбрасываться.
- **FR-005**: Описание `KaraokeProperty` `vpnHomeCountry` MUST быть обновлено:
  список стран через запятую (пример `DE,RU`), а не одна страна.
- **FR-006**: Лог `isVpnActive` MUST печатать весь список home-стран
  (для диагностики), например `homeCountry=[DE, RU]`.

### Non-Functional

- **NFR-001**: Изменение MUST быть backward compatible: существующий файл
  `Karaoke.properties` со значением `vpnHomeCountry=DE` работает без миграции.
- **NFR-002**: Парсинг — чистая функция без сети/БД; MUST быть покрыт unit-тестом.
- **NFR-003**: Все существующие callers `isVpnActive()` (4 места) не меняют
  сигнатуру.

### Key Entities

- **`vpnHomeCountry`** (`KaraokeProperty`) — MODIFY: значение = список стран.
- **Utils.kt** — NEW helper `vpnHomeCountries(): Set<String>` (парсинг значения);
  MODIFY `isVpnActive()`, `checkLastAlbumYm()`.

## Success Criteria *(mandatory)*

- **SC-001**: `vpnHomeCountry="DE,RU"` → обе страны не считаются ВПН;
  третья (`NL`) — считается. Verified unit-тестом.
- **SC-002**: `vpnHomeCountry="RU"` (legacy) работает как раньше.
- **SC-003**: Оба места (`isVpnActive`, `checkLastAlbumYm`) используют единый
  список; захардкоженного `"RU"`-сравнения страны нет.
- **SC-004**: `:karaoke-app:ktlintCheck` — 0 violations, `:karaoke-app:test` — PASS,
  `tools/check-knowledge-structure.sh` — 9/9 OK, `gh pr checks` — all PASS.

## Assumptions

1. **Список разделителей** — `,`, `;`, пробелы (выбор владельца: «список через запятую/пробел»).
2. **UI-редактор Karaoke-properties** — свободный текст, правки описания
   достаточно; отдельный multi-select не требуется.
3. **`checkLastAlbumYm` Unknown-ветка** тоже приводится к списку (решение владельца).

## Out of Scope

- **Реальный детект VPN по множеству признаков** (ASN, proxy-признаки) — нет,
  проверка остаётся «страна по IP vs список home-стран».
- **Авто-определение home-стран** — список задаёт админ вручную.
- **Изменение GeoIpService** — не трогаем.
- **Изменение источников определения страны** (`api.country.is`, `ipapi.co`) — нет.

## Migration Path

### Что нужно изменить

- `Utils.kt` (MODIFY) — helper `vpnHomeCountries()`, `isVpnActive()`, `checkLastAlbumYm()`.
- `KaraokeProperties.kt` (MODIFY) — описание/пример значения `vpnHomeCountry`.
- `UtilsTest` (NEW/ADD) — unit-тесты парсинга и `isVpn` (чистая логика).
- `knowledge/system/utilities.md` (MODIFY) — helper + поведение.
- `knowledge/domains/processing/components/karaoke-properties.md` (MODIFY) —
  задокументировать `vpnHomeCountry` (был пробел).
- `docs/features/vpn-home-country.md` (NEW, при необходимости per-feature doc).

### Что НЕ нужно менять

- Callers `isVpnActive()` — сигнатура та же.
- Значение существующей настройки в `Karaoke.properties` — backward compatible.
- `GeoIpService`, внешние geo-сервисы.

## Validation

| Проверка | Ожидаемо |
|---|---|
| `:karaoke-app:test` (Utils/vpn-тесты) | PASS |
| `:karaoke-app:ktlintCheck` | 0 violations |
| `:karaoke-app:bootJar` | OK |
| `tools/check-knowledge-structure.sh` | 9/9 OK |
| Ручная: `vpnHomeCountry=DE,RU` + лог | `homeCountry=[DE, RU]`, RU/DE → выключен |

## Rollback

`git revert <merge-commit>` — возврат к одному значению; строка `vpnHomeCountry`
в файле настроек со списком будет прочитана как одно значение (сравнение
не совпадёт) → возможен ложный ВПН, поэтому откат сопроводить правкой настройки
обратно на `DE`.

## Clarifications

### Session 2026-09-22

- **Q**: Формат значения?
  - **A**: Список через `,` и/или пробел (`DE,RU`, `DE RU`, `de ru`), регистр не важен.
- **Q**: Трогать ли захардкоженный `"RU"` в `checkLastAlbumYm`?
  - **A**: Да, привести к тому же списку home-стран (согласованность).
