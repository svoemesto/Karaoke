# Vuex store: Properties

> **Домен**: system (frontend)
> **Компонента**: `webvue3/src/components/Properties/store.js` — UI для
> редактирования `KaraokeProperties`.

## Файл

`webvue3/src/components/Properties/store.js`

## State

```javascript
state: {
    propertiesDigest: [],              // ~150 свойств (см. karaoke-properties.md)
    propertiesDigestIsLoading: false,
    propertiesTableCurrentPage: 1,    // persistent
}
```

## Hot paths

- **`/api/properties/getproperty`** — загрузка значения по key.
- **`/api/properties/setproperty`** — сохранение.
- **`/api/properties/digest`** — список всех (с metadata).

## Связь

- **KaraokeProperties** ([karaoke-properties.md](../../domains/processing/components/karaoke-properties.md)) —
  ~150 параметров (Pass 341 P1).
- **150 параметров рендера** — редактируются через UI (см.
  [rendering domain](../../domains/rendering/domain.md)).

## Changelog

- **Pass 382** (2026-09-09): Initial. Автор: agent (Karaoke).