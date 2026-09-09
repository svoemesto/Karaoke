# Composable: useKaraokeEditor

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useKaraokeEditor.js`
> — чистая логика караоке-разметки.

## Файл

`karaoke-public/src/composables/useKaraokeEditor.js` (557 строк)

## Назначение

**Чистая логика караоке-разметки**, портированная из `webvue3 SubsEdit.vue`
(минимальный набор: слоги + концы/новые строки + END).

**Без завязки на WaveSurfer** — оперирует простыми объектами маркеров:
```typescript
{ uid, time, label, color, position, markertype }
```

Отрисовку регионов и транспорт держит компонент `EditorWorkView.vue`.

**Формат маркеров идентичен admin-редактору** — разметка, одобренная
админом, применяется в `tbl_songs` один-в-один.

## Цвета маркеров

```javascript
const MARKER_COLOR_SYLLABLES = '#D2691E'         // обычные слоги
const MARKER_COLOR_FIRSTSYLLABLE = '#008000'    // первый слог
const MARKER_COLOR_ENDOFLINE = '#FF0000'        // конец строки
const MARKER_COLOR_NEWLINE = '#FF0000'          // новая строка
const MARKER_COLOR_ENDOFSYLLABLE = '#99004C'    // конец слога
const MARKER_COLOR_END = '#000080'              // конец песни
const MARKER_COLOR_SETTING = '#000080'          // настройки
```

## Настройки редактора (localStorage)

Зеркало `_loadPersistedSettings/_savePersistedSettings` из
`KaraokePlayer.js` — чтобы редактор запоминал размеры шрифтов,
громкость, скорость, масштаб, активный стем, видимость клавиатуры
между сессиями (per-user, per-browser, через localStorage).

**Ключ ОТДЕЛЬНЫЙ** от плеерского (`karaoke-player-settings`) —
настройки редактора и плеера не пересекаются.

```javascript
const EDITOR_DEFAULTS = Object.freeze({
  textFontSize: 16,
  previewFontSize: 18,
  volume: 1,
  playbackRate: 0.75,
  zoom: 100,
  activeSound: 'voice',
  showKeyboard: false,
});
```

**localStorage** может быть недоступен (приватный режим / квота) —
обе функции тихо ничего не делают и возвращают дефолты.

## Hot paths

- **Marker creation** — каждое нажатие мыши в `EditorWorkView.vue`.
- **Syllable splitting** — автоматическое при наборе текста.
- **Validation** — перед сохранением (синхронизация с admin).

## Известные TODO

- [ ] **Полный API** — какие функции экспортируются, какие
      сигнатуры (Pass 343+).
- [ ] **Тесты** — есть ли unit-тесты.
- [ ] **Sync с admin-форматом** — гарантируется ли 1-в-1.

## Changelog

- **Pass 372** (2026-09-09): Initial. Автор: agent (Karaoke).