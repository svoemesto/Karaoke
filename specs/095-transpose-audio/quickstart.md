# Quickstart: Audio Transpose for Premium Users

## Prerequisites

- Node.js 22 (LTS)
- Браузер Chrome/Firefox/Safari (desktop)
- Премиум-аккаунт на karaoke-public (для тестирования P1)
- Бесплатный аккаунт (для тестирования P2)

## Setup

```bash
cd karaoke-public
npm install  # уже установлен soundtouchjs
npm run dev    # или npm run build + preview
```

## Validation Scenarios

### User Story 1 — Premium Transpose (P1)

1. **Login** как premium-пользователь
2. **Открыть** любую песню с готовыми стемами (статус ≥ 6)
3. **Нажать** ▶ для воспроизведения
4. **Нажать** кнопку ♫ в левом верхнем углу (overlay)
5. **Выбрать** +3 semitones из меню
6. **Expected**: Аудио меняет тональность в реальном времени без перезагрузки, gap < 1.5s
7. **Нажать** +1 semitone
8. **Expected**: Тональность обновляется на +1 от оригинала (не cumulative с +3)
9. **Refresh** страницу
10. **Expected**: Восстанавливается последний выбор из localStorage

### User Story 2 — Free User Prompt (P2)

1. **Logout** (или открыть в incognito)
2. **Открыть** ту же песню
3. **Нажать** ♫
4. **Expected**: Появляется модалка «Транспонирование — PREMIUM» с CTA «Оформить подписку →»
5. **Нажать** «Закрыть»
6. **Expected**: Модалка закрывается, воспроизведение продолжается
7. **Нажать** «Оформить подписку →»
8. **Expected**: Навигация на `/premium`

### User Story 3 — Key Names Display (P3)

1. **Открыть** песню с известной тональностью (поле `key` в playerdata не null)
2. **Нажать** ♫
3. **Expected**: Вверху меню видна «Базовая: A minor» (или аналогично)
4. **Expected**: Каждый offset показывает целевую тональность: «+2 → B minor»
5. **Открыть** песню без тональности (key = null)
6. **Expected**: Только offsets: «+1», «-2» без key names

## Edge Case Checks

- **±6 semitones**: В меню появляется ⚠️ рядом с +6/-6
- **Rapid switching**: Быстро переключать +1/-1/+1 — нет щелчков (debounce 300ms)
- **Unknown base key**: Нет ошибок в консоли, UI показывает offset-only labels

## Console Checks

```javascript
// Проверить сохранённое значение
localStorage.getItem('transpose_123')  // должно вернуть "3" если выбрано +3

// Проверить pitch shifter nodes
player._stNodeAcc  // ScriptProcessorNode или null
player._stNodeVoc  // ScriptProcessorNode или null
```

## Known Limitations

- **ScriptProcessorNode** deprecated в Web Audio API, но единственный вариант совместимый с `AudioBufferSourceNode` архитектурой KaraokePlayer (v1). AudioWorklet — для v2.
- **Мобильный Safari**: Может иметь ограничения по производительности ScriptProcessorNode. При проблемах transpose просто не инициализируется (graceful fallback).
