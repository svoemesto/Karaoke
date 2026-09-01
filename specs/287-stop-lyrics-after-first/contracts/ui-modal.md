# UI Contract: 287 — Модалка «Поиск текста песни в интернете»

> **Дата**: 2026-08-31 | **Спека**: [spec.md](spec.md) | **API Contracts**: [api-endpoints.md](api-endpoints.md)

## Контекст

Модалка открывается из `SubsEdit.vue` по клику на иконку «Найти в Интернете текст песни» (`SubsEdit.vue:976`). Используется редактором для поиска и применения текста песни.

Существующий файл: `webvue3/src/components/Songs/edit/SearchText.vue`.

## Текущая структура модалки (без изменений)

```
┌────────────────────────────────────────────────────────────────┐
│  Поиск текста песни в интернете (Yandex Search API)            │
├────────────────────────────────────────────────────────────────┤
│  [поисковый запрос]                                             │
│                                                                │
│  ┌──────────────┐  ┌──────────────────────────────────────┐   │
│  │  url1 [белый] │  │  <textarea> текст песни              │   │
│  │  url2 [серый] │  │                                      │   │
│  │  url3 [серый] │  │                                      │   │
│  │  ...          │  │  [Открыть на сайте]                  │   │
│  │               │  │  [Получить текст по ссылке] ← НОВОЕ │   │
│  │               │  └──────────────────────────────────────┘   │
│  └──────────────┘                                              │
│                                                                │
│  [Вернуть] [Копировать] [Искать заново] [Удалить рез-ты] [X]   │
└────────────────────────────────────────────────────────────────┘
```

## Изменения

### Новая кнопка «Получить текст по ссылке»

**Расположение**: непосредственно под кнопкой «Открыть на сайте» в правой колонке.

**Текст**: «Получить текст по ссылке» (по запросу пользователя).

**Title (tooltip)**: «Получить текст по ссылке» (по умолчанию) или «Текст уже получен — повторная попытка не требуется» (когда `disabled` из-за непустого `text`).

**Стиль**: тот же `class="group-button"`, что и «Открыть на сайте».

### Состояния кнопки

| Состояние | `disabled` | Текст | Когда |
|-----------|-----------|-------|-------|
| Скрыта | n/a | n/a | Нет `currentResult` (поиск ещё не завершён) или `searchResultsList.length === 0` |
| Доступна | `false` | «Получить текст по ссылке» | `currentResult.text === ""` |
| Недоступна (уже есть текст) | `true` | «Текст уже есть» (опционально) или скрыта | `currentResult.text !== ""` |
| Загрузка | `true` | «Получаю текст...» | `isExtractingLyrics === true` |

### Поведение при клике

1. Проверить `currentResult` и `currentResult.text === ""` (защита от двойного клика).
2. Установить `isExtractingLyrics = true`.
3. Вызвать `this.$store.dispatch('extractLyricsBySearchResultId', { searchResultId: this.currentResult.id })`.
4. Получить ответ — обновлённый `SearchResultDTO`.
5. **Обновить запись в `searchResultsList` по id** (через `this.$set` или splice — реактивность Vue 2).
6. **Заменить `this.currentResult` на обновлённую запись** (чтобы правая колонка и кнопка обновились).
7. Если `updated.lastError` (и `updated.text === ""`):
   - Показать уведомление: «Не удалось получить текст: <lastError>» (toast / alert / сообщение в правой колонке).
   - Кнопка остаётся доступной для повторной попытки.
8. Если `updated.text !== ""`:
   - Правая колонка (`<textarea>`) заполняется текстом через computed `resultText`.
   - Ссылка в левом списке автоматически становится «белой» (потому что `text !== ""` → `backgroundColor: 'white'`).
   - Кнопка становится `disabled` или скрывается.
9. Установить `isExtractingLyrics = false`.

### Реактивность Vue 2

Используем `Vue.set` для обновления элемента массива:
```js
this.$set(this.searchResultsList, idx, updated)
```

Или splice (тоже реактивно):
```js
this.searchResultsList.splice(idx, 1, updated)
```

### Edge cases в UI

- **Пользователь кликает на другую «серую» ссылку во время `isExtractingLyrics = true`**: новая ссылка становится `currentResult`, но кнопка остаётся `disabled`. После завершения текущего запроса — можно кликнуть снова.
- **Backend возвращает 404 (запись удалена)**: показываем уведомление «Запись не найдена», закрываем модалку.
- **Backend возвращает 400 (битый запрос)**: показываем уведомление «Ошибка запроса», оставляем кнопку доступной.
- **Network error (offline)**: показываем уведомление «Нет соединения», оставляем кнопку доступной.

## Что НЕ меняется в UI

- `SearchText.vue:11-12` — заголовок модалки.
- `SearchText.vue:14-19` — поисковый запрос (заголовок тела).
- `SearchText.vue:21-31` — левая колонка со списком ссылок (без изменений).
- `SearchText.vue:36` — `<textarea class="result-text" v-text="resultText" />` (computed `resultText` остаётся как есть).
- `SearchText.vue:37-39` — кнопка «Открыть на сайте» (без изменений).
- `SearchText.vue:46-79` — подвал модалки (все 5 кнопок: Вернуть, Копировать, Искать заново, Удалить результаты поиска, Выйти).
- `SearchTextResultsTable.vue` — без изменений (визуальное состояние уже работает).
- `SubsEdit.vue` — без изменений (только открывает модалку).

## Данные в `data()` компонента

Новые поля:
```js
data() {
  return {
    // ... существующие поля ...
    isExtractingLyrics: false,
  }
}
```

## Новые computed

```js
computed: {
  canExtractLyrics() {
    return this.currentResult && this.currentResult.text === ''
  },
  // ... существующие computed ...
}
```

## Новые methods

```js
methods: {
  async extractLyricsFromSelectedResult() {
    if (!this.currentResult || this.currentResult.text !== '' || this.isExtractingLyrics) return
    this.isExtractingLyrics = true
    try {
      const updated = await this.$store.dispatch('extractLyricsBySearchResultId', {
        searchResultId: this.currentResult.id,
      })
      // Обновить запись в списке
      const idx = this.searchResultsList.findIndex(r => r.id === updated.id)
      if (idx !== -1) {
        this.$set(this.searchResultsList, idx, updated)
      }
      // Обновить currentResult
      this.currentResult = updated
      // Показать уведомление об ошибке если есть
      if (updated.lastError && updated.text === '') {
        // toast/alert/notification
        console.warn('Ошибка извлечения текста:', updated.lastError)
        // или использовать существующий customConfirmParams для модального уведомления
      }
    } catch (e) {
      console.error('Ошибка запроса:', e)
      // показать уведомление «Нет соединения» или «Ошибка сервера»
    } finally {
      this.isExtractingLyrics = false
    }
  },
  // ... существующие methods ...
}
```

## Кнопка «Открыть на сайте» — без изменений

```vue
<button class="group-button" title="Открыть на сайте" @click="openResultLink">
  Открыть на сайте
</button>
```

## Полный фрагмент правой колонки (новый)

```vue
<!-- Второй столбец тела -->
<div class="st-body-column-2">
  <!-- Текст результата поиска -->
  <textarea class="result-text" v-text="resultText" />
  <button class="group-button" title="Открыть на сайте" @click="openResultLink">
    Открыть на сайте
  </button>
  <!-- НОВОЕ: Получить текст по ссылке -->
  <button
    v-if="canExtractLyrics || isExtractingLyrics"
    class="group-button"
    :title="canExtractLyrics ? 'Получить текст по ссылке' : 'Текст уже получен'"
    :disabled="!canExtractLyrics || isExtractingLyrics"
    @click="extractLyricsFromSelectedResult"
  >
    {{ isExtractingLyrics ? 'Получаю текст...' : 'Получить текст по ссылке' }}
  </button>
</div>
```

## Что показывать в качестве уведомления об ошибке

В проекте уже есть компонент `CustomConfirm.vue`. Можно использовать его для модального уведомления. Но также можно использовать `console.warn` + `alert` как минимальный вариант.

**Решение** (на усмотрение реализатора):
- Использовать существующий `customConfirmParams` / `isCustomConfirmVisible` для модального уведомления.
- Или использовать `window.alert(...)` для простоты.

Минимальный вариант (для MVP): `alert('Не удалось получить текст: ' + updated.lastError)`.