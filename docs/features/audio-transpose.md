# Audio Transpose (Транспонирование аудио)

## Что делает

Функция позволяет премиум-пользователям изменять тональность воспроизведения караоке-трека в онлайн-плеере на лету (real-time), без перезагрузки страницы и без сохранения транспонированных стемов в хранилище. Доступен диапазон ±6 полутонов от базовой тональности песни.

## Зачем

- Увеличение конверсии visitor→registration за счёт явной премиальной ценности.
- Повышение удержания премиум-пользователей: возможность петь в комфортной тональности.
- Аддитивная фича, не ломающая обратную совместимость.

## Как работает

### Client-side v1 (ScriptProcessorNode)
1. Плеер декодирует FLAC-стемы (instrumental + vocal) в AudioBuffer через `decodeAudioData`.
2. `ScriptProcessorNode` (2-in, 2-out) обрабатывает аудио в реальном времени.
3. Phase-vocoder pitch shifter (`soundtouchjs` library) изменяет высоту обоих стемов на одинаковый offset.
4. Транспонированные стемы миксируются и воспроизводятся без промежуточного сохранения.

### Client-side v2 (AudioWorklet — deferred)
- Планируется миграция на `@soundtouchjs/audio-worklet` с `AudioWorkletNode`.
- Требует архитектурного изменения: `MediaElementAudioSourceNode` вместо `AudioBufferSourceNode`.
- `@soundtouchjs/audio-worklet` уже установлен как dependency для будущей миграции.

### Server-side fallback (документированный, не реализован в v1)
- Если client-side недоступен (старый мобильный браузер), можно вернуться к ffmpeg streaming с `rubberband`/`atempo` фильтром.
- Не требует сохранения файлов: ffmpeg читает из MinIO и стримит в плеер.

### UI
- Кнопка ♫ в overlay плеера (рядом с FavoriteIcon) открывает dropdown/modal.
- Отображается базовая тональность (если известна) и целевая для каждого offset.
- Для бесплатных пользователей — upsell prompt с CTA на /premium.

## Инварианты / правила

1. **Никаких сохранённых транспонированных стемов** — только real-time processing (spec FR-004).
2. **Одинаковый offset для instrumental и vocal** — иначе голос и музыка разойдутся.
3. **Default = 0 semitones** — при открытии песни всегда оригинальная тональность.
4. **Non-cumulative** — выбор +1 после -2 даёт +1 от оригинала, не -1.
5. **localStorage persistence** — `transpose_${songId}` сохраняет выбор между сессиями.
6. **Premium-only gate** — бесплатные пользователи видят prompt, не controls.
7. **Playback position continuity** — смена тональности не сбрасывает текущее время.
8. **Debounced input** — 300ms debounce при быстром переключении значений.
9. **Graceful fallback** — если pitch shifter не инициализируется, песня просто играет в оригинальной тональности.

## Известные ловушки

- **ScriptProcessorNode deprecated** — API deprecated в Web Audio API, но единственный вариант совместимый с `AudioBufferSourceNode` архитектурой KaraokePlayer (v1).
- **Artifacts при ±6 semitones** — phase vocoder вносит "phasiness". UI-hint ⚠️ на крайних значениях.
- **Rapid switching glitches** — без debounce быстрое переключение ломает audio pipeline.
- **Enharmonic spelling** — C♯ major vs D♭ major. Принято: диезы (♯) для "острых" ключей, бемоли (♭) для "плоских".

## Ссылки

- Spec: `specs/095-transpose-audio/spec.md`
- Plan: `specs/095-transpose-audio/plan.md`
- Research: `specs/095-transpose-audio/research.md`
- Tasks: `specs/095-transpose-audio/tasks.md`
- Quickstart: `specs/095-transpose-audio/quickstart.md`
