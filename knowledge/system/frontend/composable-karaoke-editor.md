# Composable: useKaraokeEditor

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useKaraokeEditor.js`
> — чистая логика караоке-разметки.
> **ВНИМАНИЕ**: этот документ описывает ОДНУ из трёх копий логики маркеров
> (см. «Копий логики маркеров — ТРИ, не одна» ниже). Перед правкой разметки
> проверьте, не расходится ли ваша копия с контрактом `tag-registry.md`.

## Файл

`karaoke-public/src/composables/useKaraokeEditor.js` (557 строк)

## Назначение

**Чистая логика караоке-разметки** (минимальный набор: слоги + концы/новые строки + END).

**Без завязки на WaveSurfer** — оперирует простыми объектами маркеров:
```typescript
{ uid, time, label, color, position, markertype }
```

Отрисовку регионов и транспорт держат компоненты-обёртки.

**Формат маркеров идентичен admin-редактору** — разметка, одобренная
админом, применяется в `tbl_songs` один-в-один.

## Направление порта: публичная версия — оригинал

**ИСТОРИЯ (исправлено 2026-09-29, тикет #204 карты wayfinder #186).** Этот
документ раньше утверждал обратное: «портированная из `webvue3 SubsEdit.vue`».
По git это не так:

| Файл | Первый коммит | Дата | Сообщение |
|---|---|---|---|
| `karaoke-public/src/composables/useKaraokeEditor.js` | `ee4ee7e4` | 2026-07-07 | «Онлайн-редактор караоке-разметки для пользователей сайта» |
| `webvue3/src/composables/useKaraokeEditor.js` | `9e213b1f` | 2026-07-16 | «…в админке (webvue3) — **порт karaoke-public**» |

Оригинал — **публичный** composable. Оба админских файла — его потомки.

## Копий логики маркеров — ТРИ, не одна

Документ описывал только публичную копию. На деле в JS логика маркеров живёт
в трёх независимых реализациях:

1. `webvue3/src/components/Songs/edit/SubsEdit.vue` (5810 стр.) — полновесный
   админский редактор, всё внутри компонента.
2. `webvue3/src/composables/useKaraokeEditor.js` (612 стр.) — лёгкий админский
   редактор (`SongKaraokeEditorView.vue`). **Отдельная физическая копия, не
   импорт.**
3. `karaoke-public/src/composables/useKaraokeEditor.js` (557 стр.) — то, что
   описывает этот документ.

Контракт `specs/010-lyrics-spec-tags/contracts/tag-registry.md` говорит о
**пяти** местах (включая Python-сервис выравнивания) и прямо требует: «Все
пять реализаций ДОЛЖНЫ согласованно… расхождение — это баг».

**[WARN] Осторожно при поиске мест:** «webvue3» — это не один редактор, а минимум
два, с двумя отдельными копиями логики. Ищите по файлам, а не по имени пакета.
Это уже стоило проекту: опечатка `locklad` вместо `lockLad` прошла через все
три копии незамеченной, потому что `ignoreUnknownKeys` молча её отбрасывал, а
комментарии в контроллерах описывали опечатку как норму. Фича «прибить ноту к
струне/ладу» не работала ни разу (тикет #201).

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