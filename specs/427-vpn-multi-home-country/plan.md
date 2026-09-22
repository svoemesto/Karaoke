# Implementation Plan: isVpnActive — несколько «домашних» стран

**Branch**: `427-vpn-multi-home-country` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/427-vpn-multi-home-country/spec.md`

## Summary

Настройка `vpnHomeCountry` хранит одну страну, поэтому машина, законно бывающая
и в DE, и в RU, ложно считается под ВПН. Заменяем сравнение «одна страна» на
**список home-стран** (`DE,RU`), парсим его единой чистой функцией (split по
`,`/`;`/пробелам, uppercase, trim). Приводим к тому же списку второе место с
захардкоженным `"RU"` (`checkLastAlbumYm`, ветка `Unknown`). Backward-compatible:
одиночное `"DE"` работает как раньше.

## Technical Context

**Language/Version**: Kotlin (JVM 21, Spring Boot)
**Primary Dependencies**: `KaraokeProperties` (файл `/sm-karaoke/system/Karaoke.properties`)
**Storage**: свойства — файл (не БД)
**Testing**: JUnit 5 (чистые unit-тесты на парсинг/сравнение)
**Target Platform**: Linux, контейнер `karaoke-app`
**Project Type**: single backend service
**Performance Goals**: парсинг на каждом вызове (редкий), без сети/БД
**Constraints**: backward-compatible значение настройки; API `isVpnActive()` без изменений
**Scale/Scope**: правка `Utils.kt` + `KaraokeProperties.kt` + тесты + knowledge/docs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Principle IX (Knowledge-first)** — pre-flight выполнен, см. spec.md § Knowledge References.
  Обнаружен пробел: `vpnHomeCountry` не задокументирован в knowledge → закрываем в этом PR.
- **Principle VII / VI (FR-009 per-feature doc)** — при необходимости новый `docs/features/*`.
- **Tier-1 Hard Gate — Knowledge SSoT** — обновляем `knowledge/system/utilities.md`,
  `knowledge/domains/processing/components/karaoke-properties.md`.
- **Tier-1 Hard Gate — Git CI-gate** — только ветка `427-vpn-multi-home-country` + PR + CI.
- **Tier-1 Hard Gate — Machine-Specific** — `nsa-i9`/`nsa`: `karaoke-app` rebuild ✅, restart ❌.
- **R-43/R-44, R-04..R-11** — не затрагиваются.

Нарушений нет; Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/427-vpn-multi-home-country/
├── spec.md
├── plan.md
├── tasks.md
└── report.md
```

### Source Code (repository root)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── Utils.kt                 # MODIFY: vpnHomeCountries(), isVpnActive(), checkLastAlbumYm()
└── KaraokeProperties.kt     # MODIFY: описание/пример vpnHomeCountry

karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/
└── VpnHomeCountryTest.kt    # NEW: unit-тесты парсинга и сравнения (чистые)
```

**Structure Decision**: single-project. Чистую логику парсинга вынесем в
тестируемую top-level-функцию, чтобы покрыть unit-тестом без сети/БД.

## Phase 0 — Research

- `isVpnActive()` (`Utils.kt:3699`) — сетевой резолв страны + сравнение с одной
  `vpnHomeCountry`. Сеть оставляем; меняем только сравнение.
- Второе место: `Utils.kt:3771` — `country != "RU"` в `checkLastAlbumYm`.
- `KaraokeProperties.getString("vpnHomeCountry")` возвращает строку как есть.
- Тестов на VPN нет — добавляем чистые тесты, изолированные от сети.

## Phase 1 — Design

### Изменение 1: helper парсинга (тестируемый)

```kotlin
/** Разбирает vpnHomeCountry (список ISO-кодов через , ; или пробел). */
fun parseHomeCountries(raw: String): Set<String> =
    raw.split(',', ';', ' ', '\t', '\n')
        .map { it.trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()

/** Текущие home-страны из настроек. */
fun vpnHomeCountries(): Set<String> = parseHomeCountries(Karaoke.vpnHomeCountry)
```

### Изменение 2: `isVpnActive()`

```kotlin
val homeCountries = vpnHomeCountries()
...
val isVpn = homeCountries.isNotEmpty() && country !in homeCountries
println("isVpnActive: countryCode=$country (homeCountry=${homeCountries.toList().sorted()}) → ВПН ...")
```

- Если список пуст (настройка пуста) — не считаем ВПН (текущее поведение: любая
  страна != "" ; но пустой homeCountry раньше давал `country != ""` → ВПН=true).
  Решение: пустой список → `isVpn=false` (fail-open, безопаснее не блокировать).

### Изменение 3: `checkLastAlbumYm` (Unknown)

```kotlin
val homeCountries = vpnHomeCountries()
if (country.isNotEmpty() && country.uppercase() !in homeCountries) { ... ВПН ... }
```

### Изменение 4: `KaraokeProperties.kt` — описание

`description = "Список кодов стран сервера без ВПН (ISO 3166-1 alpha-2) через запятую/пробел, например DE,RU. Используется для определения активности ВПН."`

### Контракт

| Вход | homeCountries | isVpn |
|---|---|---|
| `DE` | {DE,RU} | false |
| `RU` | {DE,RU} | false |
| `NL` | {DE,RU} | true |
| `RU` | {RU} | false (legacy) |
| `DE` | {RU} | true |

## Phase 2 — Tasks

См. [tasks.md](./tasks.md).
