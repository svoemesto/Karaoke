---
status: Active
slug: 101-audio-transpose-player
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../architecture/L3-components.md
  - ../../specs/101-audio-transpose-player/spec.md
---

# 101 — Транспонирование аудио в онлайн-плеере (админка) (LiveDoc)

> Drill-down — [specs/101-audio-transpose-player/spec.md](../../specs/101-audio-transpose-player/spec.md).

## Что делает

В онлайн-плеере админки можно **транспонировать** аудио в другую тональность
от базовой (поле `Song.key`, например `Am`). Диапазон — ±12 полутонов
(октава). Меню — рядом с регулятором скорости.

**Транспонирование — на стороне клиента** (браузер) — используя SoundTouch.js
или Tone.js для качественного pitch-shift с time-stretch.

При выборе тональности, отличной от базовой — синий бейдж сверху справа
(под бейджем скорости). Бейдж показывает сдвиг (например, `+3` или `-2`).

**Применяется ко всем загруженным стемам** (acc + voc по умолчанию; bass/
drums/прочее — когда воспроизводятся) синхронно, чтобы смесь оставалась
согласованной. Архитектура — перебор стемов, не хардкод acc/voc.

**Edge cases**:
- `key` пустое — подменю видно, пункты сдвигов (+3/-2/0), аудио от исходного.
- Браузер не поддерживает — пункты заблокированы с подсказкой.

**Сначала админка**, потом публичный плеер для премиум-пользователей.

## User Stories (краткий список)

- **US1** (P1): Админ выбирает транспозицию → плеер играет в другой тональности.
- **US2** (P1): Бесшовное переключение «на лету» во время воспроизведения.

## Functional Requirements (указатель)

- **FR-001**: Меню транспозиции (±12 полутонов).
- **FR-002**: SoundTouch.js (или аналог) для pitch-shift.
- **FR-003**: Бейдж со сдвигом.
- **FR-004**: Применить ко всем стемам синхронно.

## Acceptance Criteria

- [ ] **AC1**: Админ выбирает `+3` → плеер играет на 3 полутона выше.
- [ ] **AC2**: Переключение во время воспроизведения — без прерывания.
- [ ] **AC3**: Применяется ко всем стемам.
- [ ] **AC4**: Бейдж `+3` виден справа сверху.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md) (Song.key), [identity.md](../domain/identity.md) (editor role)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Frontend: `webvue3/src/components/Player/TranspositionMenu.vue` (новый)
- Frontend: `webvue3/src/components/Player/Player.vue` — интеграция + SoundTouch.js
- Frontend: `webvue3/src/components/Player/TransposeBadge.vue` (новый)
- `package.json`: `soundtouch-js` или `tone`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14