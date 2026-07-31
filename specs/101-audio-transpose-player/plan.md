# Implementation Plan: Транспонирование аудио в онлайн-плеере (админка)

**Branch**: `101-audio-transpose-player` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/101-audio-transpose-player/spec.md`

## Summary

Транспонирование аудио ±12 полутонов в плеере админки (`webvue3/src/player/KaraokePlayer.js`) на стороне браузера, через time-stretch pitch-shift узлы Web Audio API (Tone.PitchShift), вставляемые в существующий аудио-граф **для каждого проигрываемого стема** (acc + voc сейчас; bass/drums/прочие при появлении — map-based архитектура `_pitchShifts: Map<stemKey, Tone.PitchShift>`, без хардкода): `source → pitchShift → gain`. Базовая тональность берётся из уже существующего поля `data.key` (отдаётся обоими playerdata-эндпоинтами без изменений бэкенда). Выбор сдвига — через подменю «Тональность» в меню плеера (рядом со скоростью). Бейдж тональности (синий) под бейджем скорости. Персистентность — per-song в localStorage (по `data.id`), в отличие от скорости (глобальной).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17, бэкенд — без изменений) + JavaScript (ES2020+, Vue 3, Vite) для фронта.

**Primary Dependencies**: Существующие — Vue 3, Vite, Bootstrap-vue-next, Web Audio API (`AudioContext`, `AudioBufferSourceNode`, `GainNode`). Новая — pitch-shift time-stretch библиотека (исследование: [research.md](./research.md)).

**Storage**: localStorage браузера (per-song сдвиг тональности по `data.id`). Серверное хранилище — без изменений (БД, MinIO не затрагиваются).

**Testing**: Ручное тестирование пользователем в браузере (CI-тестов нет; интеграционные `@Disabled`). Проверка: ktlint (без изменений Kotlin), ESLint webvue3 (`npm run lint:check`), JSDoc coverage (`tools/check-jsdoc-coverage.sh webvue3`).

**Target Platform**: Современные десктоп-браузеры с Web Audio API (Chrome/Firefox/Edge/Safari актуальных версий). Админка — внутренний инструмент.

**Project Type**: web application (admin SPA `webvue3`, без backend-изменений в этой фазе).

**Performance Goals**: Бесшовная смена тональности «на лету» без остановки воспроизведения (FR-006) — задержка применения ≤1 с (SC-001). Pitch-shift без изменения темпа/длительности (синхронность с караоке-маркерами, расхождение ≤100 мс — SC-003). Рендер плеера 60 fps без деградации от двух бейджей.

**Constraints**: Транспонирование полностью клиентское (без сетевых запросов — SC-002). Не ломать существующую синхронизацию аудио с маркерами. Pitch-shift должен сохранять темп (time-stretch), а не resampling — иначе сломается FR-006.

**Scale/Scope**: Один файл плеера (`webvue3/src/player/KaraokePlayer.js`) + его HTML-шаблон (`_buildUI`) + CSS. Бэкенд — без изменений. Публичный плеер (`karaoke-public`) — не затрагивается (FR-017).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Статус | Заметка |
|-----------|--------|---------|
| I. Self-contained автопайплайн | ✅ PASS | Транспонирование клиентское, в браузере. Внешняя pitch-shift библиотека — JS-зависимость npm (bundle в webvue3), не SaaS в горячем пути. Не затрагивает медиа-пайплайн. |
| II. Сырой JDBC + дифф по хэшам | ✅ PASS (N/A) | Бэкенд не меняется; БД не затрагивается. |
| III. Двух-БД синхронизация | ✅ PASS (N/A) | Новых сущностей в БД нет; сдвиг хранится в localStorage браузера, не синхронизируется. |
| IV. Async-очередь задач | ✅ PASS (N/A) | Длительных операций нет; всё в браузере. |
| V. Двух-фронтенд | ✅ PASS | Изменения только в `webvue3` (admin). `karaoke-public` не затрагивается (FR-017). Смешивания ответственностей нет. |
| VI. Code Standards (FR-006/FR-007/FR-009) | ⚠️ Требует соблюдения | JSDoc на новые public-методы/свойства `KaraokePlayer` (transposing, setTranspose и т.п.) с `@see` на per-feature документ. ESLint без новых нарушений (или baseline). Per-feature документ `docs/features/player-transpose.md` обновить в том же PR (подсистема «плеер» — если есть в `docs/features/README.md`; иначе создать). |
| VII. Cross-Machine Setup | ✅ PASS (N/A) | Локальные AI-конфиги не коммитятся; новый код — общий, в гите. |

**Нарушений нет.** Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/101-audio-transpose-player/
├── plan.md              # This file
├── research.md          # Phase 0: выбор pitch-shift библиотеки + подход
├── data-model.md        # Phase 1: сущности (сдвиг, персистентность)
├── contracts/
│   └── player-transpose-ui-contract.md  # контракт меню/бейджа/меню-взаимодействия
├── quickstart.md        # Phase 1: руководство валидации
└── tasks.md             # Phase 2 (/speckit.tasks — НЕ в этом plan)
```

### Source Code (repository root)

```text
webvue3/
└── src/
    └── player/
        └── KaraokePlayer.js      # основной файл изменений: _buildUI, _buildMenu,
                                  # _updateSpeedMenu → _updateTransposeMenu, setTranspose,
                                  # _startAudio (вставка pitch-shift узлов), _renderSpeedBadge
                                  # → _renderTransposeBadge, _savePersistedSettings (per-song)
└── package.json                  # +зависимость pitch-shift библиотеки (research.md)

docs/features/
└── player-transpose.md           # новый per-feature документ (FR-009 constitution)
                                  # (или обновить существующий docs/features/player.md, если есть)
```

**Structure Decision**: Single-project admin SPA (`webvue3`). Бэкенд (`karaoke-app`/`karaoke-web`) НЕ меняется — `data.key` уже отдаётся обоими playerdata-эндпоинтами без изменений. Все изменения локализованы в `webvue3/src/player/KaraokePlayer.js` (уже несёт меню скорости, бейдж скорости, `setPlaybackRate`, `_savePersistedSettings`, `_startAudio` — точные точки расширения). Новый per-feature документ — в `docs/features/` (Constitution FR-009).

## Complexity Tracking

> Не заполняется — нарушений Constitution Check нет.