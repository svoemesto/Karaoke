# Отчёт по #151 — isVpnActive: несколько «домашних» стран (DE и RU)

> **Спека**: [spec.md](spec.md) | **Ветка**: `427-vpn-multi-home-country` |
> **Дата**: 2026-09-22 | **Pass**: 427.

## Симптом

В логах `karaoke-app`:

```
isVpnActive: countryCode=RU (homeCountry=DE) → ВПН включён (via https://api.country.is/)
```

Машина может легально находиться и в Германии (DE), и в России (RU), но настройка
`vpnHomeCountry` допускала только **одну** страну. Любая вторая законная страна
трактовалась как ВПН, что ложно блокировало:

- `AutoOneClickSyncScheduler` (авто-синхронизация),
- `AlbumCoverFinder`,
- `ApiController.postSyncOneClick` (ручная синхронизация),
- `KaraokeProcessWorker`.

## Что сделано

- **`Utils.kt`**:
  - новая чистая функция `parseHomeCountries(raw)` — split по `,`/`;`/пробелам,
    trim, uppercase, без пустых; `vpnHomeCountries()` читает настройку;
  - `isVpnActive()`: сравнение со **списком** home-стран
    (`homeCountries.isNotEmpty() && country !in homeCountries`), лог печатает
    весь список (`homeCountry=[DE, RU]`); пустой список → fail-open;
  - ветка `AlbumSearchResult.Unknown` в `checkLastAlbumYm`: захардкоженное
    `country != "RU"` заменено на общий список home-стран.
- **`KaraokeProperties.kt`**: описание `vpnHomeCountry` обновлено — список
  ISO-кодов через запятую/пробел, пример `DE,RU`.
- **`VpnHomeCountryTest.kt`** (NEW): 8 offline-тестов — парсинг разделителей,
  регистр, пустое значение, legacy `RU`, DE/RU как home, третья страна = ВПН,
  fail-open.
- **Knowledge**: `knowledge/system/utilities.md` (VPN-секция),
  `knowledge/domains/processing/components/karaoke-properties.md`
  (закрыт пробел: `vpnHomeCountry` ранее не был задокументирован).

## Использование

В настройках (KaraokeProperties) выставить:

```
vpnHomeCountry = DE,RU
```

После рестарта `karaoke-app` при `countryCode=RU` или `DE` ВПН будет выключен.

## Проверки

| Проверка | Результат |
|---|---|
| `:karaoke-app:compileKotlin` / `:karaoke-web:compileKotlin` | OK |
| `:karaoke-app:ktlintCheck` | OK (0 violations) |
| `:karaoke-app:test --tests "*VpnHomeCountryTest"` | 8/8 PASS |
| `:karaoke-app:bootJar` | OK |
| `tools/check-knowledge-structure.sh` | 9/9 OK |
| `tools/lint-knowledge.py --baseline ...` | PASS (no new violations) |
| `tools/check-spec-issue-link.py` | OK |

## Backward compatibility

- Одиночное значение (`vpnHomeCountry=DE` или `RU`) работает как раньше —
  список из одного элемента. Миграция файла настроек не требуется.
- Сигнатура `isVpnActive()` не изменилась; callers не правились.

## Follow-up

- Требуется **рестарт `karaoke-app` владельцем** (nsa-i9: агент не перезапускает)
  и установка `vpnHomeCountry=DE,RU` через UI настроек.
